package com.github.shikejing.experiment;

import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.ExposureEvent;
import com.github.shikejing.experiment.model.MetricEvent;
import com.github.shikejing.experiment.model.Variant;
import com.github.shikejing.experiment.registry.InMemoryExperimentRegistry;
import com.github.shikejing.experiment.service.AssignmentService;
import com.github.shikejing.experiment.service.EventService;
import com.github.shikejing.experiment.service.MetricsStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 事件幂等与去重：重复上报、乱序到达、超时重试不造成重复计数；
 * 未真正命中的上报不报入任何变体；异常输入可定位到实验与变体。
 */
class EventIdempotencyTest {

    private InMemoryExperimentRegistry registry;
    private AssignmentService assignmentService;
    private EventService eventService;
    private MetricsStore store;

    @BeforeEach
    void setUp() {
        registry = new InMemoryExperimentRegistry();
        registry.publish(new ExperimentConfig("exp", null, 0, 0, "salt",
                List.of(new Variant("a", 50), new Variant("b", 50))));
        assignmentService = new AssignmentService(registry);
        store = new MetricsStore();
        eventService = new EventService(assignmentService, store);
    }

    private String hitUnit(int start) {
        for (int i = start; ; i++) {
            if (assignmentService.assign("exp", "unit-" + i).hit()) {
                return "unit-" + i;
            }
        }
    }

    @Test
    void duplicateExposureCountedOnce() {
        String unit = hitUnit(0);
        String variant = assignmentService.assign("exp", unit).variantKey();
        ExposureEvent event = new ExposureEvent("e1", "exp", 1, unit, variant);

        assertTrue(eventService.recordExposure(event));
        assertFalse(eventService.recordExposure(event)); // 同一事件重试
        // 同一单位换 eventId 重复上报（乱序/重发）
        assertFalse(eventService.recordExposure(new ExposureEvent("e2", "exp", 1, unit, variant)));
        assertEquals(1, store.exposureCount("exp", 1, variant));
    }

    @Test
    void outOfOrderMetricBeforeExposureStillCountedOnce() {
        String unit = hitUnit(0);
        Assignment a = assignmentService.assign("exp", unit);
        // 指标先于曝光到达
        assertTrue(eventService.recordMetric(
                new MetricEvent("m1", "exp", 1, unit, a.variantKey(), "revenue", 10.0)));
        // 重复指标事件（重试）去重
        assertFalse(eventService.recordMetric(
                new MetricEvent("m1", "exp", 1, unit, a.variantKey(), "revenue", 10.0)));
        MetricsStore.Aggregate agg = store.metricAggregate("exp", 1, a.variantKey(), "revenue");
        assertEquals(1, agg.count());
        assertEquals(10.0, agg.sum());
    }

    @Test
    void reportForNonHitVariantNotCounted() {
        String unit = hitUnit(0);
        Assignment a = assignmentService.assign("exp", unit);
        String other = a.variantKey().equals("a") ? "b" : "a";
        // 上报了未命中的变体
        assertFalse(eventService.recordExposure(new ExposureEvent("e1", "exp", 1, unit, other)));
        assertFalse(eventService.recordMetric(new MetricEvent("m1", "exp", 1, unit, other, "revenue", 1.0)));
        // 未命中实验的单位
        assertFalse(eventService.recordExposure(new ExposureEvent("e2", "exp", 1, unit, other)));
        assertEquals(0, store.exposureCount("exp", 1, other));
        assertNull(store.metricAggregate("exp", 1, other, "revenue"));
    }

    @Test
    void invalidInputThrowsWithExperimentAndVariantContext() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.recordMetric(
                        new MetricEvent("m1", "exp", 1, "unit", "a", "revenue", Double.NaN)));
        assertTrue(ex.getMessage().contains("exp"));
        assertTrue(ex.getMessage().contains("a"));
        assertThrows(IllegalArgumentException.class,
                () -> eventService.recordExposure(new ExposureEvent("", "exp", 1, "unit", "a")));
        assertThrows(IllegalArgumentException.class,
                () -> eventService.recordExposure(null));
    }
}
