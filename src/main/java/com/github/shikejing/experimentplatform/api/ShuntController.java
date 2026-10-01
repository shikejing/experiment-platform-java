package com.github.shikejing.experimentplatform.api;

import com.github.shikejing.experimentplatform.model.Assignment;
import com.github.shikejing.experimentplatform.service.ShuntService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/shunt")
public class ShuntController {

    private final ShuntService shuntService;

    public ShuntController(ShuntService shuntService) {
        this.shuntService = shuntService;
    }

    @GetMapping
    public Map<String, Object> shunt(@RequestParam String unitId) {
        List<Assignment> assignments = shuntService.shunt(unitId);
        List<Map<String, Object>> rows = assignments.stream().map(a -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("experimentId", a.getExperimentId());
            row.put("version", a.getVersion());
            row.put("layerId", a.getLayerId());
            row.put("variant", a.getVariantName());
            return row;
        }).toList();
        return Map.of("unitId", unitId, "assignments", rows);
    }
}
