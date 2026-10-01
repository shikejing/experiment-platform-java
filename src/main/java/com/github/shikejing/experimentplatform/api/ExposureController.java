package com.github.shikejing.experimentplatform.api;

import com.github.shikejing.experimentplatform.dto.ExposureRequest;
import com.github.shikejing.experimentplatform.model.ExposureEvent;
import com.github.shikejing.experimentplatform.model.ExposureResult;
import com.github.shikejing.experimentplatform.service.ExposureService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/exposures")
public class ExposureController {

    private final ExposureService exposureService;

    public ExposureController(ExposureService exposureService) {
        this.exposureService = exposureService;
    }

    @PostMapping
    public Map<String, Object> record(@RequestBody ExposureRequest request) {
        ExposureEvent event = new ExposureEvent();
        event.setEventId(request.getEventId());
        event.setUnitId(request.getUnitId());
        event.setExperimentId(request.getExperimentId());
        event.setVariantName(request.getVariantName());
        event.setMetric(request.getMetric());
        event.setValue(request.getValue());
        event.setTimestamp(request.getTimestamp());

        ExposureResult result = exposureService.record(event);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("accepted", result.isAccepted());
        map.put("duplicate", result.isDuplicate());
        map.put("experimentId", result.getExperimentId());
        map.put("variantName", result.getVariantName());
        if (result.getReason() != null) {
            map.put("reason", result.getReason());
        }
        return map;
    }
}
