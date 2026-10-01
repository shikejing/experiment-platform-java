package com.github.shikejing.experimentplatform.api;

import com.github.shikejing.experimentplatform.model.ExperimentStats;
import com.github.shikejing.experimentplatform.model.VariantStats;
import com.github.shikejing.experimentplatform.service.StatsService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/{experimentId}")
    public Map<String, Object> stats(@PathVariable String experimentId,
                                     @RequestParam(defaultValue = "exposure") String metric) {
        ExperimentStats stats = statsService.stats(experimentId, metric);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("experimentId", stats.getExperimentId());
        map.put("metric", stats.getMetric());
        map.put("totalCount", stats.getTotalCount());
        map.put("variants", stats.getVariants().stream().map(StatsController::row).toList());
        return map;
    }

    private static Map<String, Object> row(VariantStats vs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("variant", vs.getVariantName());
        map.put("count", vs.getCount());
        map.put("proportion", vs.getProportion());
        map.put("mean", vs.getMean());
        map.put("valueCount", vs.getValueCount());
        map.put("valueSum", vs.getValueSum());
        return map;
    }
}
