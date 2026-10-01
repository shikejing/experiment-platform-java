package com.github.shikejing.experimentplatform.service;

import com.github.shikejing.experimentplatform.engine.ShuntEngine;
import com.github.shikejing.experimentplatform.model.Assignment;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import com.github.shikejing.experimentplatform.store.AssignmentStore;
import com.github.shikejing.experimentplatform.store.SnapshotStore;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 分流服务：读取不可变快照完成分配，结果可复现。
 * 分流结果同步落库，供曝光上报做"真正命中"校验。
 */
@Service
public class ShuntService {

    private final ShuntEngine shuntEngine;
    private final SnapshotStore snapshotStore;
    private final AssignmentStore assignmentStore;

    public ShuntService(ShuntEngine shuntEngine, SnapshotStore snapshotStore,
                        AssignmentStore assignmentStore) {
        this.shuntEngine = shuntEngine;
        this.snapshotStore = snapshotStore;
        this.assignmentStore = assignmentStore;
    }

    public List<Assignment> shunt(String unitId) {
        if (unitId == null || unitId.isBlank()) {
            throw new IllegalArgumentException("unitId is required");
        }
        PublishedSnapshot snapshot = snapshotStore.get();
        List<Assignment> assignments = shuntEngine.shunt(unitId, snapshot);
        assignments.forEach(assignmentStore::put);
        return assignments;
    }
}
