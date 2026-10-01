package com.github.shikejing.experimentplatform.service;

import com.github.shikejing.experimentplatform.engine.SnapshotFactory;
import com.github.shikejing.experimentplatform.exceptions.ExperimentNotFoundException;
import com.github.shikejing.experimentplatform.exceptions.InvalidConfigException;
import com.github.shikejing.experimentplatform.model.ExperimentConfig;
import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import com.github.shikejing.experimentplatform.model.Variant;
import com.github.shikejing.experimentplatform.store.DraftStore;
import com.github.shikejing.experimentplatform.store.SnapshotStore;
import com.github.shikejing.experimentplatform.store.VersionStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 实验配置服务：草稿编辑、版本化发布与回滚。
 * <p>
 * 发布/回滚只产生不可变版本并整体替换已发布快照；任何校验失败都在
 * 变更前抛出，上一个可用版本继续服务。分流永远只读取已发布的完整版本，
 * 不存在新旧配置字段级混合。
 */
@Service
public class ExperimentService {

    private static final int BPS = 10000;

    private final DraftStore draftStore;
    private final VersionStore versionStore;
    private final SnapshotStore snapshotStore;
    private final SnapshotFactory snapshotFactory;

    public ExperimentService(DraftStore draftStore, VersionStore versionStore,
                             SnapshotStore snapshotStore, SnapshotFactory snapshotFactory) {
        this.draftStore = draftStore;
        this.versionStore = versionStore;
        this.snapshotStore = snapshotStore;
        this.snapshotFactory = snapshotFactory;
    }

    public void saveDraft(ExperimentConfig config) {
        validate(config);
        draftStore.save(config);
    }

    /**
     * 发布草稿：校验通过后追加新版本并原子替换快照。
     * 失败时不产生任何新版本，旧快照继续服务。
     */
    public ExperimentVersion publish(String experimentId) {
        ExperimentConfig draft = draftStore.get(experimentId)
                .orElseThrow(() -> new InvalidConfigException(
                        "no draft found for experiment: " + experimentId, experimentId, null));
        validate(draft);

        PublishedSnapshot current = snapshotStore.get();
        int newVersion = versionStore.latestVersion(experimentId) + 1;
        ExperimentVersion version = toVersion(draft, newVersion);

        // 先用"包含新版本"的候选快照做层权重和校验，失败则不写入任何存储
        Map<String, ExperimentVersion> candidate = new HashMap<>(current.getExperiments());
        candidate.put(experimentId, version);
        validateLayerWeights(candidate);

        versionStore.append(version);
        PublishedSnapshot next = snapshotFactory.create(current.getSnapshotId() + 1,
                candidate, current.getGlobalConfig());
        snapshotStore.casPublish(next);
        return version;
    }

    /**
     * 回滚到历史版本：历史版本不可变，回滚后同一输入的分配与该版本
     * 当时的结果完全一致。
     */
    public ExperimentVersion rollback(String experimentId, int targetVersion) {
        ExperimentVersion target = versionStore.get(experimentId, targetVersion)
                .orElseThrow(() -> new InvalidConfigException(
                        "target version not found: experiment=" + experimentId
                                + ", version=" + targetVersion, experimentId, null));

        PublishedSnapshot current = snapshotStore.get();
        if (!current.getExperiments().containsKey(experimentId)) {
            throw new ExperimentNotFoundException(experimentId);
        }

        Map<String, ExperimentVersion> candidate = new HashMap<>(current.getExperiments());
        candidate.put(experimentId, target);
        validateLayerWeights(candidate);

        PublishedSnapshot next = snapshotFactory.create(current.getSnapshotId() + 1,
                candidate, current.getGlobalConfig());
        snapshotStore.casPublish(next);
        return target;
    }

    public List<ExperimentVersion> listVersions(String experimentId) {
        return versionStore.list(experimentId);
    }

    public ExperimentVersion getPublished(String experimentId) {
        ExperimentVersion v = snapshotStore.get().getExperiments().get(experimentId);
        if (v == null) {
            throw new ExperimentNotFoundException(experimentId);
        }
        return v;
    }

    // ------------------------------------------------------------------
    // 校验逻辑
    // ------------------------------------------------------------------

    private void validate(ExperimentConfig config) {
        if (config == null || config.getExperimentId() == null
                || config.getExperimentId().isBlank()) {
            throw new InvalidConfigException("experimentId is required", null, null);
        }
        String experimentId = config.getExperimentId();
        if (config.getName() == null || config.getName().isBlank()) {
            throw new InvalidConfigException("name is required", experimentId, null);
        }
        List<Variant> variants = config.getVariants();
        if (variants == null || variants.isEmpty()) {
            throw new InvalidConfigException("variants must not be empty", experimentId, null);
        }
        Set<String> names = new HashSet<>();
        int sum = 0;
        for (Variant v : variants) {
            if (v.getName() == null || v.getName().isBlank()) {
                throw new InvalidConfigException("variant name is required", experimentId, null);
            }
            if (!names.add(v.getName())) {
                throw new InvalidConfigException("duplicate variant name: " + v.getName(),
                        experimentId, v.getName());
            }
            if (v.getWeightBps() < 0 || v.getWeightBps() > BPS) {
                throw new InvalidConfigException("variant weight out of range [0,10000]: "
                        + v.getName(), experimentId, v.getName());
            }
            sum += v.getWeightBps();
        }
        if (sum != BPS) {
            throw new InvalidConfigException(
                    "variant weights must sum to 10000, got: " + sum, experimentId, null);
        }
        if (config.getLayerWeightBps() < 0 || config.getLayerWeightBps() > BPS) {
            throw new InvalidConfigException("layerWeightBps out of range [0,10000]",
                    experimentId, null);
        }
        if (config.getLayerId() != null && !config.getLayerId().isBlank()
                && config.getLayerWeightBps() <= 0) {
            throw new InvalidConfigException(
                    "experiment in mutex layer must have positive layerWeightBps",
                    experimentId, null);
        }
    }

    /**
     * 校验每个互斥层内的实验权重合计不超过 10000（层内流量可部分分配，
     * 剩余桶位表示不命中该层任何实验）。
     */
    private void validateLayerWeights(Map<String, ExperimentVersion> experiments) {
        Map<String, Integer> sums = new HashMap<>();
        for (ExperimentVersion v : experiments.values()) {
            String layer = SnapshotFactory.effectiveLayerId(v);
            int weight = SnapshotFactory.effectiveLayerWeight(v);
            sums.merge(layer, weight, Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : sums.entrySet()) {
            if (entry.getValue() > BPS) {
                throw new InvalidConfigException(
                        "layer weight sum exceeds 10000: layer=" + entry.getKey()
                                + ", sum=" + entry.getValue(), null, null);
            }
        }
    }

    private ExperimentVersion toVersion(ExperimentConfig config, int version) {
        return new ExperimentVersion(
                config.getExperimentId(),
                version,
                config.getName(),
                config.getLayerId(),
                config.getLayerWeightBps(),
                config.getSalt(),
                config.getVariants().stream()
                        .map(v -> new Variant(v.getName(), v.getWeightBps()))
                        .toList(),
                Instant.now());
    }
}
