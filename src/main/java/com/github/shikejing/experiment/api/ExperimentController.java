package com.github.shikejing.experiment.api;

import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.registry.ExperimentRegistry;
import com.github.shikejing.experiment.service.AssignmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentRegistry registry;
    private final AssignmentService assignmentService;

    public ExperimentController(ExperimentRegistry registry, AssignmentService assignmentService) {
        this.registry = registry;
        this.assignmentService = assignmentService;
    }

    /** 发布新版本；校验失败返回 400，当前服务版本不受影响。 */
    @PostMapping("/publish")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody ExperimentConfig config) {
        try {
            long version = registry.publish(config);
            return ResponseEntity.ok(Map.of("experimentKey", config.key(), "version", version));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 回滚到历史版本。 */
    @PostMapping("/{key}/rollback/{version}")
    public ResponseEntity<Map<String, Object>> rollback(@PathVariable String key, @PathVariable long version) {
        try {
            long effective = registry.rollback(key, version);
            return ResponseEntity.ok(Map.of("experimentKey", key, "version", effective));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 更新全局兜底比例（万分比）。 */
    @PostMapping("/fallback")
    public ResponseEntity<Map<String, Object>> fallback(@RequestParam int permille) {
        try {
            registry.updateFallbackRatio(permille);
            return ResponseEntity.ok(Map.of("fallbackPermille", permille));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** 按当前版本分流。 */
    @GetMapping("/{key}/assign")
    public Assignment assign(@PathVariable String key, @RequestParam String unitId) {
        return assignmentService.assign(key, unitId);
    }

    /** 按指定版本分流（复现历史分配）。 */
    @GetMapping("/{key}/versions/{version}/assign")
    public Assignment assignByVersion(@PathVariable String key, @PathVariable long version,
                                      @RequestParam String unitId) {
        return assignmentService.assign(key, version, unitId);
    }
}
