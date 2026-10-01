package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.model.Assignment;
import com.github.shikejing.experimentplatform.model.ExperimentConfig;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分流确定性、比例贴合与并发稳定性测试。
 */
class ShuntEngineTest {

    private final TestSupport ts = new TestSupport();

    @Test
    void sameUnitSameVersion_alwaysSameVariant() {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt-a",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));

        Map<String, String> first = new HashMap<>();
        for (int i = 0; i < 2000; i++) {
            String unit = "u" + i;
            first.put(unit, ts.shuntService.shunt(unit).get(0).getVariantName());
        }

        // 再次分流（模拟重启后重新读取快照），结果必须完全一致
        for (int i = 0; i < 2000; i++) {
            String unit = "u" + i;
            List<Assignment> again = ts.shuntService.shunt(unit);
            assertThat(again).hasSize(1);
            assertThat(again.get(0).getVariantName()).isEqualTo(first.get(unit));
            assertThat(again.get(0).getVersion()).isEqualTo(1);
        }
    }

    @Test
    void distribution_matchesConfiguredWeights() {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt-b",
                TestSupport.variant("A", 8000), TestSupport.variant("B", 2000)));

        int countA = 0, countB = 0;
        for (int i = 0; i < 20000; i++) {
            String variant = ts.shuntService.shunt("u" + i).get(0).getVariantName();
            if ("A".equals(variant)) countA++;
            else countB++;
        }
        double propA = countA / 20000.0;
        double propB = countB / 20000.0;
        // 80% / 20%，允许 ±1.5% 统计波动
        assertThat(propA).isBetween(0.785, 0.815);
        assertThat(propB).isBetween(0.185, 0.215);
    }

    @Test
    void distribution_evenSplit() {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt-c",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));

        int[] counts = new int[2];
        for (int i = 0; i < 20000; i++) {
            String variant = ts.shuntService.shunt("u" + i).get(0).getVariantName();
            counts["A".equals(variant) ? 0 : 1]++;
        }
        assertThat(counts[0] / 20000.0).isBetween(0.485, 0.515);
        assertThat(counts[1] / 20000.0).isBetween(0.485, 0.515);
    }

    @Test
    void concurrentShunt_sameUnitAlwaysSameVariant() throws Exception {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt-d",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));

        int units = 500;
        int threads = 8;
        Map<String, String> observed = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?>[] futures = new Future[threads];
            for (int t = 0; t < threads; t++) {
                futures[t] = pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < units; i++) {
                            String unit = "u" + i;
                            String variant = ts.shuntService.shunt(unit).get(0).getVariantName();
                            observed.merge(unit, variant, (a, b) -> {
                                if (!a.equals(b)) {
                                    throw new AssertionError("variant changed for unit " + unit);
                                }
                                return a;
                            });
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(observed).hasSize(units);
    }

    @Test
    void shunt_doesNotDependOnIterationOrder() {
        // 同一单位集合，不同遍历顺序，分配结果必须一致
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt-e",
                TestSupport.variant("A", 3000), TestSupport.variant("B", 3000),
                TestSupport.variant("C", 4000)));

        List<String> unitIds = java.util.stream.IntStream.range(0, 1000)
                .mapToObj(i -> "u" + i).toList();

        Map<String, String> forward = new HashMap<>();
        unitIds.forEach(u -> forward.put(u, ts.shuntService.shunt(u).get(0).getVariantName()));

        Map<String, String> reverse = new HashMap<>();
        for (int i = unitIds.size() - 1; i >= 0; i--) {
            String u = unitIds.get(i);
            reverse.put(u, ts.shuntService.shunt(u).get(0).getVariantName());
        }
        assertThat(reverse).isEqualTo(forward);
    }
}
