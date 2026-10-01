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
 * 分流确定性与稳定性：同一单位同一版本，无论执行顺序、
 * 模拟重启（重建服务实例）还是配置列表顺序，结果一致；
 * 分配比例贴合配置权重。
 */
class DeterministicAssignmentTest {

    private ExperimentConfig config(String salt, List<Variant> variants) {
        return new ExperimentConfig("exp-a", null, 0, 0, salt, variants);
    }

    @Test
    void sameUnitSameVersionAlwaysSameVariant() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(config("s1", List.of(new Variant("control", 50), new Variant("treat", 50))));
        AssignmentService service = new AssignmentService(registry);

        Map<String, String> first = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            String unit = "unit-" + i;
            Assignment a = service.assign("exp-a", unit);
            assertTrue(a.hit());
            first.put(unit, a.variantKey());
        }
        // 重复执行 + 乱序执行，结果不变
        for (int i = 999; i >= 0; i--) {
            assertEquals(first.get("unit-" + i), service.assign("exp-a", "unit-" + i).variantKey());
        }

        // 模拟重启：全新注册表发布相同配置，结果必须一致
        InMemoryExperimentRegistry registry2 = new InMemoryExperimentRegistry();
        registry2.publish(config("s1", List.of(new Variant("control", 50), new Variant("treat", 50))));
        AssignmentService service2 = new AssignmentService(registry2);
        for (int i = 0; i < 1000; i++) {
            assertEquals(first.get("unit-" + i), service2.assign("exp-a", "unit-" + i).variantKey());
        }
    }

    @Test
    void variantListOrderDoesNotAffectResult() {
        InMemoryExperimentRegistry r1 = new InMemoryExperimentRegistry();
        r1.publish(config("s1", List.of(new Variant("a", 30), new Variant("b", 70))));
        InMemoryExperimentRegistry r2 = new InMemoryExperimentRegistry();
        r2.publish(config("s1", List.of(new Variant("b", 70), new Variant("a", 30))));

        AssignmentService s1 = new AssignmentService(r1);
        AssignmentService s2 = new AssignmentService(r2);
        for (int i = 0; i < 500; i++) {
            assertEquals(s1.assign("exp-a", "u" + i).variantKey(),
                    s2.assign("exp-a", "u" + i).variantKey());
        }
    }

    @Test
    void distributionMatchesWeights() {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(config("s1",
                List.of(new Variant("a", 25), new Variant("b", 75))));
        AssignmentService service = new AssignmentService(registry);

        int a = 0, n = 20_000;
        for (int i = 0; i < n; i++) {
            if ("a".equals(service.assign("exp-a", "unit-" + i).variantKey())) {
                a++;
            }
        }
        double ratio = (double) a / n;
        assertTrue(ratio > 0.22 && ratio < 0.28, "ratio=" + ratio);
    }
}
