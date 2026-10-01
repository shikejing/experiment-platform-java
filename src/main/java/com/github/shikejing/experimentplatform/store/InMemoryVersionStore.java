package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 ConcurrentHashMap 的只追加版本存储。
 * 版本号在实验内单调递增，历史版本不可变、可完整复现当时分配。
 */
@Repository
public class InMemoryVersionStore implements VersionStore {

    private final Map<String, Map<Integer, ExperimentVersion>> versions = new ConcurrentHashMap<>();

    @Override
    public ExperimentVersion append(ExperimentVersion version) {
        versions.computeIfAbsent(version.getExperimentId(), id -> new ConcurrentHashMap<>())
                .put(version.getVersion(), version);
        return version;
    }

    @Override
    public Optional<ExperimentVersion> get(String experimentId, int version) {
        return Optional.ofNullable(versions.get(experimentId))
                .map(map -> map.get(version));
    }

    @Override
    public List<ExperimentVersion> list(String experimentId) {
        Map<Integer, ExperimentVersion> map = versions.get(experimentId);
        if (map == null) {
            return List.of();
        }
        List<ExperimentVersion> list = new ArrayList<>(map.values());
        list.sort((a, b) -> Integer.compare(a.getVersion(), b.getVersion()));
        return List.copyOf(list);
    }

    @Override
    public int latestVersion(String experimentId) {
        Map<Integer, ExperimentVersion> map = versions.get(experimentId);
        if (map == null || map.isEmpty()) {
            return 0;
        }
        return map.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
