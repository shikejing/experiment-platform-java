package com.github.shikejing.experimentplatform;

import com.github.shikejing.experimentplatform.model.Assignment;
import com.github.shikejing.experimentplatform.model.GlobalConfig;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 互斥层与全局兜底测试。
 */
class MutexLayerTest {

    private final TestSupport ts = new TestSupport();

    @Test
    void sameMutexLayer_unitEntersAtMostOneExperiment() {
        // 同一互斥层内两个实验，各占 50% 层流量
        ts.publish(TestSupport.config("ly1", "层实验1", "layer-x", 5000, "s1",
                TestSupport.variant("A", 10000)));
        ts.publish(TestSupport.config("ly2", "层实验2", "layer-x", 5000, "s2",
                TestSupport.variant("B", 10000)));

        int inOne = 0, inBoth = 0, inNone = 0;
        for (int i = 0; i < 10000; i++) {
            List<Assignment> list = ts.shuntService.shunt("u" + i);
            Set<String> expIds = new HashSet<>();
            list.forEach(a -> expIds.add(a.getExperimentId()));
            if (expIds.size() == 2) inBoth++;
            else if (expIds.size() == 1) inOne++;
            else inNone++;
        }
        assertThat(inBoth).isZero();
        // 50% + 50% 层流量，几乎所有单位命中其中一个
        assertThat(inOne / 10000.0).isGreaterThan(0.98);
        assertThat(inNone / 10000.0).isLessThan(0.02);
    }

    @Test
    void differentLayers_unitCanEnterBoth() {
        ts.publish(TestSupport.config("a", "实验A", "layer-1", 10000, "s1",
                TestSupport.variant("A", 10000)));
        ts.publish(TestSupport.config("b", "实验B", "layer-2", 10000, "s2",
                TestSupport.variant("B", 10000)));

        int both = 0;
        for (int i = 0; i < 1000; i++) {
            long count = ts.shuntService.shunt("u" + i).stream()
                    .map(Assignment::getExperimentId).distinct().count();
            if (count == 2) both++;
        }
        assertThat(both / 1000.0).isGreaterThan(0.98);
    }

    @Test
    void independentExperiments_canCoexist() {
        // 未配置互斥层 = 独立实验，可同时命中
        ts.publish(TestSupport.config("a", "实验A", null, 0, "s1",
                TestSupport.variant("A", 10000)));
        ts.publish(TestSupport.config("b", "实验B", null, 0, "s2",
                TestSupport.variant("B", 10000)));

        int both = 0;
        for (int i = 0; i < 1000; i++) {
            long count = ts.shuntService.shunt("u" + i).stream()
                    .map(Assignment::getExperimentId).distinct().count();
            if (count == 2) both++;
        }
        assertThat(both / 1000.0).isGreaterThan(0.98);
    }

    @Test
    void globalFallback_hitUnitsEnterNoExperiment() {
        ts.publish(TestSupport.config("a", "实验A", null, 0, "s1",
                TestSupport.variant("A", 10000)));
        ts.publish(TestSupport.config("b", "实验B", "layer-x", 10000, "s2",
                TestSupport.variant("B", 10000)));

        GlobalConfig gc = new GlobalConfig();
        gc.setFallbackRateBps(1000); // 10% 全局兜底
        gc.setFallbackSalt("global_fallback");
        ts.configService.updateGlobal(gc);

        int fallbackHit = 0;
        for (int i = 0; i < 10000; i++) {
            List<Assignment> list = ts.shuntService.shunt("u" + i);
            if (list.isEmpty()) fallbackHit++;
        }
        // 兜底命中率 ≈ 10%
        assertThat(fallbackHit / 10000.0).isBetween(0.085, 0.115);
    }

    @Test
    void layerTrafficNotFilled_someUnitsEnterNone() {
        // 层内只分配 30% 流量，剩余 70% 不命中该层任何实验
        ts.publish(TestSupport.config("a", "实验A", "layer-x", 3000, "s1",
                TestSupport.variant("A", 10000)));

        int none = 0;
        for (int i = 0; i < 10000; i++) {
            if (ts.shuntService.shunt("u" + i).isEmpty()) none++;
        }
        assertThat(none / 10000.0).isBetween(0.67, 0.73);
    }
}
