package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExperimentConfig;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 草稿存储：编辑态配置，与已发布版本严格隔离，绝不参与分流。
 */
@Repository
public class InMemoryDraftStore implements DraftStore {

    private final Map<String, ExperimentConfig> drafts = new ConcurrentHashMap<>();

    @Override
    public void save(ExperimentConfig config) {
        drafts.put(config.getExperimentId(), config);
    }

    @Override
    public Optional<ExperimentConfig> get(String experimentId) {
        return Optional.ofNullable(drafts.get(experimentId));
    }

    @Override
    public void delete(String experimentId) {
        drafts.remove(experimentId);
    }
}
