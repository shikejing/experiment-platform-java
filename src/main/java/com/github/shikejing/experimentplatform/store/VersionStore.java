package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExperimentVersion;

import java.util.List;
import java.util.Optional;

/**
 * 实验版本存储：只追加不可变版本，支持按版本号读取与全量列举。
 */
public interface VersionStore {
    ExperimentVersion append(ExperimentVersion version);

    Optional<ExperimentVersion> get(String experimentId, int version);

    List<ExperimentVersion> list(String experimentId);

    int latestVersion(String experimentId);
}
