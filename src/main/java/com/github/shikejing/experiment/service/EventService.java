package com.github.shikejing.experiment.service;

import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExposureEvent;
import com.github.shikejing.experiment.model.MetricEvent;
import org.springframework.stereotype.Service;

/**
 * 事件接入：曝光与指标写入。
 *
 * <p>幂等与去重：曝光按 (实验,版本,单位) 去重，事件按 eventId 去重，
 * 重复上报、乱序到达、超时重试都不会造成重复计数。</p>
 *
 * <p>真实性校验：每条上报都用 {@link AssignmentService} 对对应版本
 * 重新做确定性分流，只有分流结果与上报变体一致才计入，
 * 未真正命中实验的上报不会计入任何变体。指标事件不依赖曝光的
 * 先到，因此乱序到达也能正确入账。</p>
 */
@Service
public class EventService {

    private final AssignmentService assignmentService;
    private final MetricsStore store;

    public EventService(AssignmentService assignmentService, MetricsStore store) {
        this.assignmentService = assignmentService;
        this.store = store;
    }

    /**
     * 记录曝光。
     *
     * @return true 表示本次被计入；false 表示重复上报或未真正命中
     */
    public boolean recordExposure(ExposureEvent event) {
        validate(event);
        Assignment assignment = assignmentService.assign(
                event.experimentKey(), event.version(), event.unitId());
        if (!assignment.hit() || !assignment.variantKey().equals(event.variantKey())) {
            return false; // 未真正命中该变体，不计入
        }
        if (!store.tryRecordExposure(event.experimentKey(), event.version(),
                event.unitId(), event.eventId())) {
            return false; // 重复上报/重试，幂等去重
        }
        store.incrementExposure(event.experimentKey(), event.version(), event.variantKey());
        return true;
    }

    /**
     * 记录指标。只要求该单位在该版本下确实命中所报变体，不要求曝光先到。
     *
     * @return true 表示本次被计入
     */
    public boolean recordMetric(MetricEvent event) {
        validate(event);
        Assignment assignment = assignmentService.assign(
                event.experimentKey(), event.version(), event.unitId());
        if (!assignment.hit() || !assignment.variantKey().equals(event.variantKey())) {
            return false;
        }
        if (!store.tryRecordMetricEvent(event.experimentKey(), event.version(), event.eventId())) {
            return false; // 重复事件，幂等去重
        }
        store.addMetric(event.experimentKey(), event.version(), event.variantKey(),
                event.metricName(), event.value());
        return true;
    }

    private void validate(ExposureEvent event) {
        if (event == null || blank(event.eventId()) || blank(event.experimentKey())
                || blank(event.unitId()) || blank(event.variantKey())) {
            throw new IllegalArgumentException("invalid exposure event: " + event);
        }
    }

    private void validate(MetricEvent event) {
        if (event == null || blank(event.eventId()) || blank(event.experimentKey())
                || blank(event.unitId()) || blank(event.variantKey())
                || blank(event.metricName()) || !Double.isFinite(event.value())) {
            throw new IllegalArgumentException(
                    "invalid metric event for experiment "
                            + (event == null ? "null" : event.experimentKey())
                            + " variant " + (event == null ? "null" : event.variantKey()));
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
