package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.exceptions.InvalidConfigException;
import com.github.shikejing.experimentplatform.model.GlobalConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 配置热更新与并发读写测试：
 * 更新/发布失败时上一个可用版本继续服务，读取不出现半更新状态。
 */
class ConfigHotUpdateTest {

    private final TestSupport ts = new TestSupport();

    @Test
    void publishFailure_oldSnapshotStillServes() {
        // 已发布实验占据互斥层 80% 流量
        ts.publish(TestSupport.config("exp1", "实验1", "layer-x", 8000, "salt",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));

        // 记录旧版本下的分配
        String before = ts.shuntService.shunt("u1").get(0).getVariantName();

        // 新实验草稿单独看合法，但与同层实验合计超过 10000，发布被拒
        ts.experimentService.saveDraft(TestSupport.config("exp2", "实验2", "layer-x", 5000,
                "salt", TestSupport.variant("A", 10000)));
        assertThatThrownBy(() -> ts.experimentService.publish("exp2"))
                .isInstanceOf(InvalidConfigException.class);

        // 旧版本继续服务，分配不变
        assertThat(ts.experimentService.getPublished("exp1").getVersion()).isEqualTo(1);
        assertThat(ts.shuntService.shunt("u1").get(0).getVariantName()).isEqualTo(before);
    }

    @Test
    void globalConfigUpdate_isAtomicForReaders() {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt",
                TestSupport.variant("A", 10000)));

        // 热更新兜底配置：更新前全部命中实验
        assertThat(ts.shuntService.shunt("u1")).hasSize(1);

        GlobalConfig gc = new GlobalConfig();
        gc.setFallbackRateBps(10000);
        gc.setFallbackSalt("global_fallback");
        ts.configService.updateGlobal(gc);

        // 更新后全部进入兜底
        assertThat(ts.shuntService.shunt("u1")).isEmpty();
        assertThat(ts.configService.current().getFallbackRateBps()).isEqualTo(10000);
    }

    @Test
    void invalidGlobalConfig_isRejectedAndOldRemains() {
        GlobalConfig gc = new GlobalConfig();
        gc.setFallbackRateBps(5000);
        ts.configService.updateGlobal(gc);

        GlobalConfig bad = new GlobalConfig();
        bad.setFallbackRateBps(15000);
        assertThatThrownBy(() -> ts.configService.updateGlobal(bad))
                .isInstanceOf(InvalidConfigException.class);

        // 旧配置继续服务
        assertThat(ts.configService.current().getFallbackRateBps()).isEqualTo(5000);
    }

    @Test
    void concurrentReadsDuringPublish_neverSeeHalfState() throws Exception {
        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt",
                TestSupport.variant("A", 5000), TestSupport.variant("B", 5000)));

        int readerThreads = 4;
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newFixedThreadPool(readerThreads + 1);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean inconsistent =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        try {
            // 读线程：不断读取快照并校验快照内部一致性（版本与变体权重和）
            java.util.concurrent.Future<?>[] readers = new java.util.concurrent.Future[readerThreads];
            for (int t = 0; t < readerThreads; t++) {
                readers[t] = pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 20000; i++) {
                            var snap = ts.snapshotStore.get();
                            var v = snap.getExperiments().get("exp1");
                            if (v == null) {
                                inconsistent.set(true);
                                return;
                            }
                            int sum = v.getVariants().stream()
                                    .mapToInt(com.github.shikejing.experimentplatform.model.Variant::getWeightBps)
                                    .sum();
                            if (sum != 10000) {
                                inconsistent.set(true);
                                return;
                            }
                            // 分流结果引用的版本必须与快照中的版本一致
                            var a = ts.shuntEngine.shunt("u" + (i % 100), snap);
                            for (var as : a) {
                                if (as.getVersion() != v.getVersion()) {
                                    inconsistent.set(true);
                                    return;
                                }
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            // 写线程：反复发布新版本（每次权重和都为 10000）
            java.util.concurrent.Future<?> writer = pool.submit(() -> {
                try {
                    start.await();
                    for (int p = 0; p < 50; p++) {
                        int a = 3000 + (p % 5) * 1000;
                        ts.publish(TestSupport.config("exp1", "实验1", null, 0, "salt",
                                TestSupport.variant("A", a),
                                TestSupport.variant("B", 10000 - a)));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });

            start.countDown();
            for (java.util.concurrent.Future<?> r : readers) {
                r.get();
            }
            writer.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(inconsistent.get()).isFalse();
        // 最终版本号应为 51
        assertThat(ts.experimentService.getPublished("exp1").getVersion()).isEqualTo(51);
    }
}
