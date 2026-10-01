package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.Assignment;

import java.util.Optional;

/**
 * 分流结果存储，用于曝光上报时校验"真正命中"。
 */
public interface AssignmentStore {
    void put(Assignment assignment);

    Optional<Assignment> get(String unitId, String experimentId);
}
