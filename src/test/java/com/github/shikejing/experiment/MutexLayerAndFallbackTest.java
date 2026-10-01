package com.github.shikejing.experiment;

import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.Variant;
import com.github.shikejing.experiment.registry.InMemoryExperimentRegistry;
import com.github.shikejing.experiment.service.AssignmentService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 互斥层与全局兜底：同层实验槽位互不重叠，同一单位不会同时进入
 * 同层多个实验；槽位冲突的发布被拒绝且旧版本继续服务；
 * 被兜底命中的单位不得进入任何实验。
 */
class MutexLayerAndFallbackTest {

    private ExperimentConfig layerExp(String key, int start, int end) {
        return new ExperimentConfig(key, "layer-1", start, end, "salt-" + key,
                List.of(new Variant("a", 50), new Variant("b", 50)));
    }

    @Test
    void unitNeverEntersTwoExperimentsInSameLayer() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(layerExp("exp-1", 0, 5000));
        registry.publish(layerExp("exp-2", 5000, 10000));
        AssignmentService service = new AssignmentService(registry);

        int hits1 = 0, hits2 = 0;
        for (int i = 0; i < 5000; i++) {
            String unit = "unit-" + i;
            boolean in1 = service.assign("exp-1", unit).hit();
            boolean in2 = service.assign("exp-2", unit).hit();
            assertFalse(in1 && in2, "unit " + unit + " entered both experiments");
            if (in1) hits1++;
            if (in2) hits2++;
        }
        // 层槽位对半分，命中数应大致相当
        assertTrue(Math.abs(hits1 - hits2) < hits1 * 0.2,
                "hits1=" + hits1 + " hits2=" + hits2);
    }

    @Test
    void conflictingLayerSlotPublishRejectedAndOldVersionServes() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(layerExp("exp-1", 0, 5000));
        // 与 exp-1 槽位重叠，拒绝
        assertThrows(IllegalArgumentException.class, () -> registry.publish(layerExp("exp-2", 4000, 9000)));
        // 非法槽位范围，拒绝
        assertThrows(IllegalArgumentException.class,
                () -> registry.publish(layerExp("exp-3", 6000, 6000)));
        assertThrows(IllegalArgumentException.class,
                () -> registry.publish(layerExp("exp-4", -1, 100)));
        // exp-1 未受影响
        assertEquals(1, registry.current("exp-1").orElseThrow().getVersion());
        assertTrue(registry.current("exp-2").isEmpty());
    }

    @Test
    void fallbackExcludedUnitsEnterNoExperiment() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(layerExp("exp-1", 0, 10000));
        registry.updateFallbackRatio(2000); // 20% 兜底
        AssignmentService service = new AssignmentService(registry);

        int fallbackHits = 0, entered = 0;
        for (int i = 0; i < 5000; i++) {
            Assignment a = service.assign("exp-1", "unit-" + i);
            if (a.reason() == Assignment.Reason.FALLBACK_EXCLUDED) {
                fallbackHits++;
            } else if (a.hit()) {
                entered++;
            }
        }
        assertTrue(fallbackHits > 0);
        assertTrue(entered > 0);
        assertEquals(5000, fallbackHits + entered); // 槽位覆盖全层，非兜底即命中

        // 兜底比例更新失败（越界）时旧值继续生效
        assertThrows(IllegalArgumentException.class, () -> registry.updateFallbackRatio(10001));
        assertEquals(2000, registry.fallbackPermille());
    }
}
