package com.github.shikejing.experiment.api;

import com.github.shikejing.experiment.model.ExposureEvent;
import com.github.shikejing.experiment.model.MetricEvent;
import com.github.shikejing.experiment.service.EventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** 曝光上报，幂等。counted=false 表示重复上报或未真正命中。 */
    @PostMapping("/exposure")
    public ResponseEntity<Map<String, Object>> exposure(@RequestBody ExposureEvent event) {
        try {
            boolean counted = eventService.recordExposure(event);
            return ResponseEntity.ok(Map.of("counted", counted));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 指标上报，幂等。 */
    @PostMapping("/metric")
    public ResponseEntity<Map<String, Object>> metric(@RequestBody MetricEvent event) {
        try {
            boolean counted = eventService.recordMetric(event);
            return ResponseEntity.ok(Map.of("counted", counted));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
