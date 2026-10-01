package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.exceptions.InvalidConfigException;
import com.github.shikejing.experimentplatform.model.Assignment;
import com.github.shikejing.experimentplatform.model.ExperimentConfig;
import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 版本化发布、回滚可复现与发布失败保持旧版本服务测试。
 */
class VersionRollbackTest {

    private final TestSupport ts = new TestSupport();

    private ExperimentConfig configV1() {
        return TestSupport.config("exp1", "实验一", null, 0, "fixed-salt",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000));
    }

    private ExperimentConfig configV2() {
        return TestSupport.config("exp1", "实验一-改", null, 0, "fixed-salt",
                TestSupport.variant("A", 9000), TestSupport.variant("B", 1000));
    }

    @Test
    void publish_createsImmutableVersions() {
        int v1 = ts.publish(configV1());
        int v2 = ts.publish(configV2());
        assertThat(v1).isEqualTo(1);
        assertThat(v2).isEqualTo(2);

        List<ExperimentVersion> versions = ts.experimentService.listVersions("exp1");
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getVariants().get(0).getWeightBps()).isEqualTo(5000);
        assertThat(versions.get(1).getVariants().get(0).getWeightBps()).isEqualTo(9000);
    }

    @Test
    void rollback_reproducesExactAssignments() {
        ts.publish(configV1());

        // v1 下记录 3000 个单位的分配
        Map<String, String> v1Assignments = new HashMap<>();
        for (int i = 0; i < 3000; i++) {
            String unit = "u" + i;
            List<Assignment> a = ts.shuntService.shunt(unit);
            v1Assignments.put(unit, a.get(0).getVariantName());
        }

        // 发布 v2（权重变化），部分单位分配会改变
        ts.publish(configV2());
        long changed = 0;
        for (int i = 0; i < 3000; i++) {
            String unit = "u" + i;
            String now = ts.shuntService.shunt(unit).get(0).getVariantName();
            if (!now.equals(v1Assignments.get(unit))) {
                changed++;
            }
        }
        assertThat(changed).isGreaterThan(0);

        // 回滚到 v1：每个单位必须与 v1 当时的分配完全一致
        ts.experimentService.rollback("exp1", 1);
        for (int i = 0; i < 3000; i++) {
            String unit = "u" + i;
            Assignment a = ts.shuntService.shunt(unit).get(0);
            assertThat(a.getVersion()).isEqualTo(1);
            assertThat(a.getVariantName()).isEqualTo(v1Assignments.get(unit));
        }
    }

    @Test
    void rollbackToHistoricalVersion_sameResultAsThen() {
        ts.publish(configV1());
        Map<String, String> then = new HashMap<>();
        for (int i = 0; i < 500; i++) {
            then.put("u" + i, ts.shuntService.shunt("u" + i).get(0).getVariantName());
        }

        ts.publish(configV2());
        ts.publish(TestSupport.config("exp1", "实验一-再改", null, 0, "fixed-salt",
                TestSupport.variant("A", 1000), TestSupport.variant("B", 9000)));

        // 回滚到版本 1
        ts.experimentService.rollback("exp1", 1);
        for (int i = 0; i < 500; i++) {
            Assignment a = ts.shuntService.shunt("u" + i).get(0);
            assertThat(a.getVersion()).isEqualTo(1);
            assertThat(a.getVariantName()).isEqualTo(then.get("u" + i));
        }
    }

    @Test
    void failedPublish_keepsPreviousVersionServing() {
        // 已发布实验占据互斥层 80% 流量
        ts.publish(TestSupport.config("exp1", "实验1", "layer-x", 8000, "fixed-salt",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));

        // 新实验草稿单独看合法（层权重 5000），但与同层实验合计 13000 > 10000
        ts.experimentService.saveDraft(TestSupport.config("exp2", "实验2", "layer-x", 5000,
                "fixed-salt", TestSupport.variant("A", 10000)));

        assertThatThrownBy(() -> ts.experimentService.publish("exp2"))
                .isInstanceOf(InvalidConfigException.class);

        // 发布被拒：exp2 未上线，exp1 继续以版本 1 服务，分配不变
        assertThatThrownBy(() -> ts.experimentService.getPublished("exp2"))
                .isInstanceOf(com.github.shikejing.experimentplatform.exceptions.ExperimentNotFoundException.class);
        ExperimentVersion published = ts.experimentService.getPublished("exp1");
        assertThat(published.getVersion()).isEqualTo(1);

        Assignment a = ts.shuntService.shunt("u1").get(0);
        assertThat(a.getVersion()).isEqualTo(1);
    }

    @Test
    void invalidDraft_isRejectedBeforePublish() {
        ExperimentConfig bad = TestSupport.config("expX", "坏配置", null, 0, null,
                TestSupport.variant("A", 3000), TestSupport.variant("B", 3000));
        assertThatThrownBy(() -> ts.experimentService.saveDraft(bad))
                .isInstanceOf(InvalidConfigException.class);
    }

    @Test
    void rollbackToMissingVersion_isRejected() {
        ts.publish(configV1());
        assertThatThrownBy(() -> ts.experimentService.rollback("exp1", 99))
                .isInstanceOf(InvalidConfigException.class);
    }

    @Test
    void rollbackUnpublishedExperiment_isRejected() {
        ts.publish(configV1());
        assertThatThrownBy(() -> ts.experimentService.rollback("nope", 1))
                .isInstanceOf(InvalidConfigException.class);
    }
}
