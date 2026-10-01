package com.github.shikejing.experiment.registry;

import com.github.shikejing.experiment.core.Hasher;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.ExperimentVersion;
import com.github.shikejing.experiment.model.Variant;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于不可变快照 + 原子切换的内存注册表。
 *
 * <p>读路径只读 {@link AtomicReference} 中的不可变快照，无锁，
 * 正在进行的读取永远不会看到半更新状态；写路径在锁内完成全部校验，
 * 构建好新快照后一次性原子替换，任何校验失败都在替换前抛出，
 * 上一个可用版本继续服务。</p>
 */
@Component
public class InMemoryExperimentRegistry implements ExperimentRegistry {

    /**
     * 不可变全局快照：当前生效版本、全量历史版本、兜底比例。
     * 快照一旦发布不再修改，配置分批下发时读者看到的始终是某一个完整快照。
     */
    private record Snapshot(
            Map<String, ExperimentVersion> current,
            Map<String, NavigableHistory> history,
            int fallbackPermille) {
    }

    /** 单实验的版本历史，按版本号有序。 */
    private record NavigableHistory(TreeMap<Long, ExperimentVersion> versions) {
    }

    private final AtomicReference<Snapshot> snapshot =
            new AtomicReference<>(new Snapshot(Map.of(), Map.of(), 0));
    private final Object writeLock = new Object();

    @Override
    public long publish(ExperimentConfig config) {
        validateConfig(config);
        synchronized (writeLock) {
            Snapshot old = snapshot.get();
            String key = config.key();
            // 与当前快照中同层其他实验做槽位冲突校验（基于旧快照，失败则旧快照继续服务）
            if (config.inLayer()) {
                validateLayerSlot(old, config);
            }
            long nextVersion = nextVersion(old, key);
            ExperimentVersion version = new ExperimentVersion(key, nextVersion, config, Instant.now());

            Map<String, ExperimentVersion> newCurrent = new HashMap<>(old.current());
            newCurrent.put(key, version);
            Map<String, NavigableHistory> newHistory = new HashMap<>(old.history());
            TreeMap<Long, ExperimentVersion> versions = new TreeMap<>(
                    old.history().containsKey(key) ? old.history().get(key).versions() : Map.of());
            versions.put(nextVersion, version);
            newHistory.put(key, new NavigableHistory(versions));

            snapshot.set(new Snapshot(Map.copyOf(newCurrent), Map.copyOf(newHistory), old.fallbackPermille()));
            return nextVersion;
        }
    }

    @Override
    public long rollback(String experimentKey, long toVersion) {
        synchronized (writeLock) {
            Snapshot old = snapshot.get();
            NavigableHistory history = old.history().get(experimentKey);
            if (history == null || !history.versions().containsKey(toVersion)) {
                throw new IllegalArgumentException(
                        "experiment " + experimentKey + " has no version " + toVersion);
            }
            // 以历史版本的完整配置重新发布为新版本：分流语义与当时完全一致
            ExperimentConfig historicalConfig = history.versions().get(toVersion).getConfig();
            if (historicalConfig.inLayer()) {
                validateLayerSlot(old, historicalConfig);
            }
            long nextVersion = nextVersion(old, experimentKey);
            ExperimentVersion version =
                    new ExperimentVersion(experimentKey, nextVersion, historicalConfig, Instant.now());

            Map<String, ExperimentVersion> newCurrent = new HashMap<>(old.current());
            newCurrent.put(experimentKey, version);
            Map<String, NavigableHistory> newHistory = new HashMap<>(old.history());
            TreeMap<Long, ExperimentVersion> versions = new TreeMap<>(history.versions());
            versions.put(nextVersion, version);
            newHistory.put(experimentKey, new NavigableHistory(versions));

            snapshot.set(new Snapshot(Map.copyOf(newCurrent), Map.copyOf(newHistory), old.fallbackPermille()));
            return nextVersion;
        }
    }

    @Override
    public Optional<ExperimentVersion> current(String experimentKey) {
        return Optional.ofNullable(snapshot.get().current().get(experimentKey));
    }

    @Override
    public Optional<ExperimentVersion> version(String experimentKey, long version) {
        NavigableHistory history = snapshot.get().history().get(experimentKey);
        if (history == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(history.versions().get(version));
    }

    @Override
    public List<ExperimentVersion> currentAll() {
        return snapshot.get().current().values().stream()
                .sorted(Comparator.comparing(ExperimentVersion::getExperimentKey))
                .toList();
    }

    @Override
    public void updateFallbackRatio(int fallbackPermille) {
        if (fallbackPermille < 0 || fallbackPermille > Hasher.SLOT_COUNT) {
            throw new IllegalArgumentException(
                    "fallbackPermille must be in [0," + Hasher.SLOT_COUNT + "]: " + fallbackPermille);
        }
        synchronized (writeLock) {
            Snapshot old = snapshot.get();
            snapshot.set(new Snapshot(old.current(), old.history(), fallbackPermille));
        }
    }

    @Override
    public int fallbackPermille() {
        return snapshot.get().fallbackPermille();
    }

    private long nextVersion(Snapshot snap, String key) {
        NavigableHistory history = snap.history().get(key);
        return history == null ? 1L : history.versions().lastKey() + 1;
    }

    private void validateConfig(ExperimentConfig config) {
        if (config == null || config.key() == null || config.key().isBlank()) {
            throw new IllegalArgumentException("experiment key must not be blank");
        }
        List<Variant> variants = config.variants();
        if (variants == null || variants.isEmpty()) {
            throw new IllegalArgumentException("experiment " + config.key() + " must have variants");
        }
        int sum = 0;
        List<String> keys = new ArrayList<>();
        for (Variant v : variants) {
            if (v.getWeight() <= 0) {
                throw new IllegalArgumentException(
                        "experiment " + config.key() + " variant " + v.getKey() + " weight must be positive");
            }
            if (!keys.contains(v.getKey())) {
                keys.add(v.getKey());
            } else {
                throw new IllegalArgumentException(
                        "experiment " + config.key() + " has duplicate variant key " + v.getKey());
            }
            sum += v.getWeight();
        }
        if (sum != 100) {
            throw new IllegalArgumentException(
                    "experiment " + config.key() + " variant weights must sum to 100, got " + sum);
        }
        if (config.salt() == null || config.salt().isBlank()) {
            throw new IllegalArgumentException("experiment " + config.key() + " salt must not be blank");
        }
        if (config.inLayer()) {
            if (config.layerStartSlot() < 0 || config.layerEndSlot() > Hasher.SLOT_COUNT
                    || config.layerStartSlot() >= config.layerEndSlot()) {
                throw new IllegalArgumentException(
                        "experiment " + config.key() + " invalid layer slot range ["
                                + config.layerStartSlot() + "," + config.layerEndSlot() + ")");
            }
        }
    }

    private void validateLayerSlot(Snapshot snap, ExperimentConfig config) {
        for (ExperimentVersion other : snap.current().values()) {
            ExperimentConfig oc = other.getConfig();
            if (oc.key().equals(config.key()) || !oc.inLayer()
                    || !oc.layerKey().equals(config.layerKey())) {
                continue;
            }
            if (config.layerStartSlot() < oc.layerEndSlot() && oc.layerStartSlot() < config.layerEndSlot()) {
                throw new IllegalArgumentException(
                        "layer " + config.layerKey() + " slot range [" + config.layerStartSlot() + ","
                                + config.layerEndSlot() + ") conflicts with experiment " + oc.key()
                                + " [" + oc.layerStartSlot() + "," + oc.layerEndSlot() + ")");
            }
        }
    }
}
