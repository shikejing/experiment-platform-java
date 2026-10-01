package com.github.shikejing.experiment;

import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.Variant;
import com.github.shikejing.experiment.registry.InMemoryExperimentRegistry;
import com.github.shikejing.experiment.service.AssignmentService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 版本化与回滚：分流只来自完整版本；回滚后同一输入分配与当时一致；
 * 历史版本可随时复现。
 */
class VersioningRollbackTest {

    private Map<String, String> assignments(AssignmentService service, String key, long version) {
        Map<String, String> result = new HashMap<>();
        for (int i = 0; i < 500; i++) {
            Assignment a = service.assign(key, version, "unit-" + i);
            result.put("unit-" + i, a.variantKey());
        }
        return result;
    }

    @Test
    void rollbackRestoresHistoricalAssignments() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        AssignmentService service = new AssignmentService(registry);

        long v1 = registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt-1",
                List.of(new Variant("a", 50), new Variant("b", 50))));
        Map<String, String> atV1 = assignments(service, "exp", v1);

        // 发布 v2：完全不同的配置（不同盐与权重）
        long v2 = registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt-2",
                List.of(new Variant("a", 10), new Variant("b", 90))));
        assertEquals(2, v2);
        Map<String, String> atV2 = assignments(service, "exp", v2);
        assertNotEquals(atV1, atV2); // 配置整体切换，非字段级混合

        // 回滚到 v1：生成新版本号，但内容与 v1 完全一致
        long v3 = registry.rollback("exp", v1);
        assertEquals(3, v3);
        assertEquals(atV1, assignments(service, "exp", v3));
        // 当前版本即回滚后的版本，当前分流也与 v1 一致
        for (int i = 0; i < 500; i++) {
            assertEquals(atV1.get("unit-" + i), service.assign("exp", "unit-" + i).variantKey());
        }
        // 历史版本仍可复现
        assertEquals(atV2, assignments(service, "exp", v2));
    }

    @Test
    void failedPublishKeepsPreviousVersionServing() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        AssignmentService service = new AssignmentService(registry);
        registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt-1",
                List.of(new Variant("a", 50), new Variant("b", 50))));
        Map<String, String> before = assignments(service, "exp", 1);

        // 权重之和不为 100，发布失败
        assertThrows(IllegalArgumentException.class, () -> registry.publish(
                new ExperimentConfig("exp", null, 0, 0, "salt-x",
                        List.of(new Variant("a", 30), new Variant("b", 30)))));
        // 空变体列表，发布失败
        assertThrows(IllegalArgumentException.class, () -> registry.publish(
                new ExperimentConfig("exp", null, 0, 0, "salt-x", List.of())));

        // 旧版本继续服务，分流结果不变
        assertEquals(1, registry.current("exp").orElseThrow().getVersion());
        assertEquals(before, assignments(service, "exp", 1));
    }

    @Test
    void rollbackToUnknownVersionFailsAndKeepsCurrent() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt-1",
                List.of(new Variant("a", 100))));
        assertThrows(IllegalArgumentException.class, () -> registry.rollback("exp", 99));
        assertThrows(IllegalArgumentException.class, () -> registry.rollback("nope", 1));
        assertEquals(1, registry.current("exp").orElseThrow().getVersion());
    }
}
