package com.github.shikejing.experiment;

import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.ExposureEvent;
import com.github.shikejing.experiment.model.MetricEvent;
import com.github.shikejing.experiment.model.Variant;
import com.github.shikejing.experiment.model.VariantStats;
import com.github.shikejing.experiment.registry.InMemoryExperimentRegistry;
import com.github.shikejing.experiment.service.AssignmentService;
import com.github.shikejing.experiment.service.EventService;
import com.github.shikejing.experiment.service.MetricsStore;
import com.github.shikejing.experiment.service.StatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 统计口径：按变体给出计数、比例与均值；分母为零、样本为空、
 * 指标无数据时返回 null 而非无意义数值；异常可定位到具体实验。
 */
class StatsServiceTest {

    private InMemoryExperimentRegistry registry;
    private AssignmentService assignmentService;
    private EventService eventService;
    private StatsService statsService;

    @BeforeEach
    void setUp() {
        registry = new InMemoryExperimentRegistry();
        registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt",
                List.of(new Variant("a", 50), new Variant("b", 50))));
        assignmentService = new AssignmentService(registry);
        MetricsStore store = new MetricsStore();
        eventService = new EventService(assignmentService, store);
        statsService = new StatsService(registry, store);
    }

    private Map<String, VariantStats> byVariant(List<VariantStats> stats) {
        return stats.stream().collect(Collectors.toMap(VariantStats::variantKey, Function.identity()));
    }

    @Test
    void emptySampleYieldsNullRatioAndMean() {
        Map<String, VariantStats> stats = byVariant(statsService.stats("exp", "revenue"));
        assertEquals(2, stats.size());
        for (VariantStats s : stats.values()) {
            assertEquals(0, s.count());
            assertNull(s.ratio(), "分母为零时比例必须为 null");
            assertNull(s.mean(), "样本为空时均值必须为 null");
        }
    }

    @Test
    void countRatioAndMeanAreExactAndReproducible() {
        int eventSeq = 0;
        for (int i = 0; i < 200; i++) {
            String unit = "unit-" + i;
            Assignment a = assignmentService.assign("exp", unit);
            assertTrue(a.hit());
            assertTrue(eventService.recordExposure(
                    new ExposureEvent("e" + (eventSeq++), "exp", 1, unit, a.variantKey())));
            // 每个单位上报两次相同指标事件（第二次为重试），值 = 序号
            assertTrue(eventService.recordMetric(
                    new MetricEvent("m" + i, "exp", 1, unit, a.variantKey(), "revenue", i)));
            assertFalse(eventService.recordMetric(
                    new MetricEvent("m" + i, "exp", 1, unit, a.variantKey(), "revenue", i)));
        }

        Map<String, VariantStats> first = byVariant(statsService.stats("exp", "revenue"));
        long total = first.values().stream().mapToLong(VariantStats::count).sum();
        assertEquals(200, total);
        for (VariantStats s : first.values()) {
            assertNotNull(s.ratio());
            assertNotNull(s.mean());
            assertEquals(s.count() / 200.0, s.ratio(), 1e-12);
        }
        // 重复查询结果完全一致（可复现）
        Map<String, VariantStats> second = byVariant(statsService.stats("exp", "revenue"));
        assertEquals(first, second);

        // 无数据的指标：均值为 null，计数与比例仍有效
        Map<String, VariantStats> noData = byVariant(statsService.stats("exp", "no-such-metric"));
        for (VariantStats s : noData.values()) {
            assertNull(s.mean());
            assertNotNull(s.ratio());
        }
    }

    @Test
    void unknownExperimentErrorNamesExperiment() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> statsService.stats("missing-exp", "revenue"));
        assertTrue(ex.getMessage().contains("missing-exp"));
    }
}
