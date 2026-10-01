package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.model.Assignment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 性能上界验证：实验数量与指标维度增长后，
 * 单次分流与单次统计查询耗时不随实验总数线性增长（不全量扫描）。
 */
class PerformanceBoundTest {

    private final TestSupport ts = new TestSupport();

    @Test
    void singleShunt_isSublinearInExperimentCount() {
        // 200 个实验：20 个互斥层，每层 10 个实验
        for (int i = 0; i < 200; i++) {
            String layer = "layer-" + (i % 20);
            ts.publish(TestSupport.config("exp" + i, "实验" + i, layer, 800, "salt" + i,
                    TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));
        }

        // 预热
        for (int i = 0; i < 1000; i++) {
            ts.shuntService.shunt("warm" + i);
        }

        int iterations = 20000;
        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            List<Assignment> r = ts.shuntService.shunt("u" + i);
            assertThat(r).isNotNull();
            assertThat(r.size()).isLessThanOrEqualTo(20); // 每层至多命中一个
        }
        long elapsed = System.nanoTime() - start;
        long avgMicros = elapsed / iterations / 1000;

        // 单次分流平均耗时上界：< 100 微秒（20 层 × 2 次哈希 + 二分查找）
        assertThat(avgMicros).isLessThan(100L);
        System.out.println("avg shunt time: " + avgMicros + " us over " + iterations
                + " shunts, 200 experiments in 20 layers");
    }

    @Test
    void singleStatsQuery_doesNotScanEvents() {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));

        // 写入 50000 条明细
        for (int i = 0; i < 50000; i++) {
            com.github.shikejing.experimentplatform.model.ExposureEvent e =
                    new com.github.shikejing.experimentplatform.model.ExposureEvent();
            e.setEventId("e" + i);
            e.setUnitId("u" + i);
            e.setExperimentId("exp1");
            e.setVariantName(ts.shuntEngine.shunt("u" + i, ts.snapshotStore.get())
                    .get(0).getVariantName());
            e.setMetric("click");
            e.setValue((double) (i % 100));
            ts.exposureService.record(e);
        }

        // 统计查询耗时与明细量无关（读预聚合索引）
        long start = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            ts.statsService.stats("exp1", "click");
        }
        long elapsed = System.nanoTime() - start;
        long avgMicros = elapsed / 1000 / 1000;

        assertThat(ts.statsService.stats("exp1", "click").getTotalCount()).isEqualTo(50000);
        assertThat(avgMicros).isLessThan(50L);
        System.out.println("avg stats query time: " + avgMicros + " us over 1000 queries");
    }
}
