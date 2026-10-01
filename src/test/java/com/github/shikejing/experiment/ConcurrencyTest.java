package com.github.shikejing.experiment;

import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.ExposureEvent;
import com.github.shikejing.experiment.model.Variant;
import com.github.shikejing.experiment.registry.InMemoryExperimentRegistry;
import com.github.shikejing.experiment.service.AssignmentService;
import com.github.shikejing.experiment.service.EventService;
import com.github.shikejing.experiment.service.MetricsStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发场景：高并发分流结果与单线程一致；发布/热更新期间读不到
 * 半更新状态；并发重复曝光只计一次。
 */
class ConcurrencyTest {

    @Test
    void concurrentAssignmentMatchesSequential() throws Exception {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt",
                List.of(new Variant("a", 30), new Variant("b", 30), new Variant("c", 40))));
        AssignmentService service = new AssignmentService(registry);

        // 先得到单线程基准
        Map<String, String> baseline = new ConcurrentHashMap<>();
        for (int i = 0; i < 2000; i++) {
            baseline.put("unit-" + i, service.assign("exp", "unit-" + i).variantKey());
        }

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger mismatches = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 2000; i++) {
                        String v = service.assign("exp", "unit-" + i).variantKey();
                        if (!baseline.get("unit-" + i).equals(v)) {
                            mismatches.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, mismatches.get());
    }

    @Test
    void readersNeverSeeHalfUpdatedState() throws Exception {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt-1",
                List.of(new Variant("a", 50), new Variant("b", 50))));
        AssignmentService service = new AssignmentService(registry);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger inconsistencies = new AtomicInteger();
        // 读者：每次读到的一定是某个完整版本（版本号与盐一致）
        for (int t = 0; t < threads - 1; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 5000; i++) {
                        var v = registry.current("exp").orElseThrow();
                        String salt = v.getConfig().salt();
                        long expected = salt.equals("salt-1") ? -1 : Long.parseLong(salt.substring(5));
                        if (expected != -1 && v.getVersion() != expected) {
                            inconsistencies.incrementAndGet(); // 版本号与配置不属于同一版本
                        }
                        // 分流本身不抛异常且结果完整
                        Assignment a = service.assign("exp", "unit-x");
                        assertNotNull(a.reason());
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        // 写者：热更新 50 次
        pool.submit(() -> {
            try {
                start.await();
                for (int v = 2; v <= 50; v++) {
                    registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt-" + v,
                            List.of(new Variant("a", 50), new Variant("b", 50))));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, inconsistencies.get());
        assertEquals(50, registry.current("exp").orElseThrow().getVersion());
    }

    @Test
    void concurrentDuplicateExposureCountedOnce() throws Exception {
        InMemoryExperimentRegistry registry = new InMemoryExperimentRegistry();
        registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt",
                List.of(new Variant("a", 100))));
        AssignmentService service = new AssignmentService(registry);
        MetricsStore store = new MetricsStore();
        EventService eventService = new EventService(service, store);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                    // 所有线程并发上报同一批单位的曝光（模拟重试风暴）
                    for (int i = 0; i < 500; i++) {
                        String unit = "unit-" + i;
                        eventService.recordExposure(new ExposureEvent("e-" + i, "exp", 1, unit, "a"));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(500, store.exposureCount("exp", 1, "a"));
    }
}
