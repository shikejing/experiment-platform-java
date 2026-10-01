package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExperimentConfig;

import java.util.Optional;

/**
 * 实验草稿存储（编辑态，不参与分流）。
 */
public interface DraftStore {
    void save(ExperimentConfig config);

    Optional<ExperimentConfig> get(String experimentId);

    void delete(String experimentId);
}
