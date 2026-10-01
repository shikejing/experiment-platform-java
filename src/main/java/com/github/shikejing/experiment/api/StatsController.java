package com.github.shikejing.experiment.api;

import com.github.shikejing.experiment.model.VariantStats;
import com.github.shikejing.experiment.service.StatsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    /** 查询实验当前版本某指标的各变体统计。 */
    @GetMapping("/{experimentKey}")
    public ResponseEntity<?> stats(@PathVariable String experimentKey, @RequestParam String metric) {
        try {
            List<VariantStats> stats = statsService.stats(experimentKey, metric);
            return ResponseEntity.ok(stats);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
