package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.exceptions.AssignmentMismatchException;
import com.github.shikejing.experimentplatform.exceptions.ExperimentNotFoundException;
import com.github.shikejing.experimentplatform.exceptions.VariantNotFoundException;
import com.github.shikejing.experimentplatform.model.ExposureEvent;
import com.github.shikejing.experimentplatform.model.ExposureResult;
import com.github.shikejing.experimentplatform.model.ExperimentStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 曝光/指标写入幂等、去重与命中校验测试。
 */
class ExposureServiceTest {

    private TestSupport ts;

    @BeforeEach
    void setUp() {
        ts = new TestSupport();
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt-x",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));
    }

    private ExposureEvent event(String eventId, String unitId, String variant,
                                String metric, Double value) {
        ExposureEvent e = new ExposureEvent();
        e.setEventId(eventId);
        e.setUnitId(unitId);
        e.setExperimentId("exp1");
        e.setVariantName(variant);
        e.setMetric(metric);
        e.setValue(value);
        e.setTimestamp(System.currentTimeMillis());
        return e;
    }

    private String variantOf(String unitId) {
        return ts.shuntService.shunt(unitId).get(0).getVariantName();
    }

    @Test
    void repeatedExposure_sameEventId_countsOnce() {
        String unit = "u1";
        String variant = variantOf(unit);

        ExposureResult first = ts.exposureService.record(event("e1", unit, variant, "click", 1.0));
        ExposureResult second = ts.exposureService.record(event("e1", unit, variant, "click", 1.0));
        ExposureResult third = ts.exposureService.record(event("e1", unit, variant, "click", 1.0));

        assertThat(first.isAccepted()).isTrue();
        assertThat(first.isDuplicate()).isFalse();
        assertThat(second.isAccepted()).isTrue();
        assertThat(second.isDuplicate()).isTrue();
        assertThat(third.isDuplicate()).isTrue();

        ExperimentStats stats = ts.statsService.stats("exp1", "click");
        long total = stats.getVariants().stream()
                .mapToLong(v -> v.getCount()).sum();
        assertThat(total).isEqualTo(1);
    }

    @Test
    void outOfOrderExposure_withoutPriorShunt_isAcceptedWhenMatches() {
        String unit = "u2";
        // 直接用引擎重算出实际命中变体（不落库分流记录），模拟曝光先于分流到达
        String variant = ts.shuntEngine.shunt(unit, ts.snapshotStore.get()).get(0).getVariantName();
        assertThat(ts.assignmentStore.get(unit, "exp1")).isEmpty();

        ExposureResult result = ts.exposureService.record(event("e2", unit, variant, "exposure", null));
        assertThat(result.isAccepted()).isTrue();

        ExperimentStats stats = ts.statsService.stats("exp1", "exposure");
        assertThat(stats.getTotalCount()).isEqualTo(1);
    }

    @Test
    void timeoutRetry_sameId_doesNotDoubleCount() {
        String unit = "u3";
        String variant = variantOf(unit);
        for (int i = 0; i < 5; i++) {
            ExposureResult r = ts.exposureService.record(
                    event("retry-1", unit, variant, "exposure", null));
            assertThat(r.isAccepted()).isTrue();
        }
        ExperimentStats stats = ts.statsService.stats("exp1", "exposure");
        assertThat(stats.getTotalCount()).isEqualTo(1);
    }

    @Test
    void wrongVariant_isRejectedAndNotCounted() {
        String unit = "u4";
        String actual = variantOf(unit);
        String wrong = actual.equals("A") ? "B" : "A";

        assertThatThrownBy(() -> ts.exposureService.record(
                event("e3", unit, wrong, "exposure", null)))
                .isInstanceOf(AssignmentMismatchException.class)
                .hasMessageContaining("exp1")
                .hasMessageContaining(wrong);

        ExperimentStats stats = ts.statsService.stats("exp1", "exposure");
        assertThat(stats.getTotalCount()).isZero();
    }

    @Test
    void unitNotInExperiment_isRejectedAndNotCounted() {
        // 全局兜底：命中兜底的单位不在任何实验中
        ts.configService.updateGlobal(new com.github.shikejing.experimentplatform.model.GlobalConfig() {{
            setFallbackRateBps(10000);
            setFallbackSalt("global_fallback");
        }});

        assertThatThrownBy(() -> ts.exposureService.record(
                event("e4", "u5", "A", "exposure", null)))
                .isInstanceOf(AssignmentMismatchException.class);

        ExperimentStats stats = ts.statsService.stats("exp1", "exposure");
        assertThat(stats.getTotalCount()).isZero();
    }

    @Test
    void unknownExperiment_isRejected() {
        ExposureEvent e = event("e5", "u6", "A", "exposure", null);
        e.setExperimentId("nope");
        assertThatThrownBy(() -> ts.exposureService.record(e))
                .isInstanceOf(ExperimentNotFoundException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void unknownVariant_isRejected() {
        String unit = "u7";
        variantOf(unit);
        assertThatThrownBy(() -> ts.exposureService.record(
                event("e6", unit, "C", "exposure", null)))
                .isInstanceOf(VariantNotFoundException.class)
                .hasMessageContaining("exp1");
    }

    @Test
    void missingFields_areRejected() {
        assertThatThrownBy(() -> ts.exposureService.record(
                event(null, "u8", "A", "exposure", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ts.exposureService.record(
                event("e7", null, "A", "exposure", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ts.exposureService.record(
                event("e8", "u8", null, "exposure", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void differentMetrics_areCountedIndependently() {
        String u1 = "u9", u2 = "u10";
        String v1 = variantOf(u1);
        String v2 = variantOf(u2);
        ts.exposureService.record(event("m1", u1, v1, "click", 1.0));
        ts.exposureService.record(event("m2", u2, v2, "stay", 10.0));

        assertThat(ts.statsService.stats("exp1", "click").getTotalCount()).isEqualTo(1);
        assertThat(ts.statsService.stats("exp1", "stay").getTotalCount()).isEqualTo(1);
        assertThat(ts.statsService.stats("exp1", "exposure").getTotalCount()).isZero();
    }
}
