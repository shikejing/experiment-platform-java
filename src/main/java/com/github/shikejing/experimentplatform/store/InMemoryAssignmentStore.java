package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.Assignment;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 分流结果存储。key = unitId + experimentId。
 */
@Repository
public class InMemoryAssignmentStore implements AssignmentStore {

    private final Map<String, Assignment> assignments = new ConcurrentHashMap<>();

    private static String key(String unitId, String experimentId) {
        return unitId + "\u0000" + experimentId;
    }

    @Override
    public void put(Assignment assignment) {
        assignments.putIfAbsent(key(assignment.getUnitId(), assignment.getExperimentId()), assignment);
    }

    @Override
    public Optional<Assignment> get(String unitId, String experimentId) {
        return Optional.ofNullable(assignments.get(key(unitId, experimentId)));
    }
}
