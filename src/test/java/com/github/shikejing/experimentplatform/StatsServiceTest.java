package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.model.ExperimentStats;
import com.github.shikejing.experimentplatform.model.VariantStats;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 指标统计口径测试：计数、比例、均值可复现；
 * 分母为零/空样本/字段缺失不产生无意义数值。
 */
class StatsServiceTest {

    private final TestSupport ts = new TestSupport();

    private void publishExp() {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));
    }

    private void exposure(String eventId, String unit, String variant, String metric, Double value) {
        com.github.shikejing.experimentplatform.model.ExposureEvent e =
                new com.github.shikejing.experimentplatform.model.ExposureEvent();
        e.setEventId(eventId);
        e.setUnitId(unit);
        e.setExperimentId("exp1");
        e.setVariantName(variant);
        e.setMetric(metric);
        e.setValue(value);
        ts.exposureService.record(e);
    }

    @Test
    void countsProportionsAndMean_areReproducible() {
        publishExp();
        // 按各单位实际命中变体构造样本（变体由确定性分流决定）
        String[] units = {"u1", "u2", "u3", "u4"};
        double[] values = {1.0, 2.0, 3.0, 4.0};
        java.util.Map<String, java.util.List<Double>> byVariant = new java.util.TreeMap<>();
        for (int i = 0; i < units.length; i++) {
            String variant = ts.shuntEngine.shunt(units[i], ts.snapshotStore.get())
                    .get(0).getVariantName();
            exposure("a" + i, units[i], variant, "click", values[i]);
            byVariant.computeIfAbsent(variant, k -> new java.util.ArrayList<>()).add(values[i]);
        }

        ExperimentStats stats = ts.statsService.stats("exp1", "click");
        assertThat(stats.getTotalCount()).isEqualTo(4);

        for (VariantStats row : stats.getVariants()) {
            java.util.List<Double> vals = byVariant.get(row.getVariantName());
            if (vals == null) {
                assertThat(row.getCount()).isZero();
                assertThat(row.getProportion()).isZero();
                assertThat(row.getMean()).isNull();
            } else {
                assertThat(row.getCount()).isEqualTo(vals.size());
                assertThat(row.getProportion()).isEqualTo(vals.size() / 4.0);
                assertThat(row.getMean()).isEqualTo(
                        vals.stream().mapToDouble(Double::doubleValue).average().orElseThrow());
                assertThat(row.getValueCount()).isEqualTo(vals.size());
            }
        }

        // 比例合计为 1
        double propSum = stats.getVariants().stream()
                .mapToDouble(VariantStats::getProportion).sum();
        assertThat(propSum).isEqualTo(1.0);

        // 再次查询结果一致（可复现）
        ExperimentStats again = ts.statsService.stats("exp1", "click");
        assertThat(again.getTotalCount()).isEqualTo(stats.getTotalCount());
        assertThat(again.getVariants()).usingRecursiveComparison()
                .isEqualTo(stats.getVariants());
    }

    @Test
    void emptySample_returnsZerosAndNullMean() {
        publishExp();
        ExperimentStats stats = ts.statsService.stats("exp1", "click");
        assertThat(stats.getTotalCount()).isZero();
        assertThat(stats.getVariants()).hasSize(2);
        for (VariantStats v : stats.getVariants()) {
            assertThat(v.getCount()).isZero();
            assertThat(v.getProportion()).isZero();
            assertThat(v.getMean()).isNull();
            assertThat(v.getValueCount()).isZero();
        }
    }

    @Test
    void missingValueField_meanIsNullButCountExists() {
        publishExp();
        exposure("e1", "u1", ts.shuntEngine.shunt("u1", ts.snapshotStore.get())
                .get(0).getVariantName(), "exposure", null);
        exposure("e2", "u2", ts.shuntEngine.shunt("u2", ts.snapshotStore.get())
                .get(0).getVariantName(), "exposure", null);

        ExperimentStats stats = ts.statsService.stats("exp1", "exposure");
        assertThat(stats.getTotalCount()).isEqualTo(2);
        for (VariantStats row : stats.getVariants()) {
            assertThat(row.getMean()).isNull();
            assertThat(row.getValueCount()).isZero();
            assertThat(row.getValueSum()).isZero();
        }
    }

    @Test
    void variantsWithNoEvents_areStillListed() {
        publishExp();
        String variant = ts.shuntEngine.shunt("u1", ts.snapshotStore.get())
                .get(0).getVariantName();
        exposure("e1", "u1", variant, "click", 1.0);

        ExperimentStats stats = ts.statsService.stats("exp1", "click");
        assertThat(stats.getVariants()).extracting(VariantStats::getVariantName)
                .containsExactly("A", "B");
        for (VariantStats row : stats.getVariants()) {
            if (row.getVariantName().equals(variant)) {
                assertThat(row.getCount()).isEqualTo(1);
                assertThat(row.getProportion()).isEqualTo(1.0);
                assertThat(row.getMean()).isEqualTo(1.0);
            } else {
                assertThat(row.getCount()).isZero();
                assertThat(row.getProportion()).isZero();
                assertThat(row.getMean()).isNull();
            }
        }
    }
}
