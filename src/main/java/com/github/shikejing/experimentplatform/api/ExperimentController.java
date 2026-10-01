package com.github.shikejing.experimentplatform.api;

import com.github.shikejing.experimentplatform.dto.ExperimentConfigDto;
import com.github.shikejing.experimentplatform.dto.VariantDto;
import com.github.shikejing.experimentplatform.model.ExperimentConfig;
import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import com.github.shikejing.experimentplatform.model.Variant;
import com.github.shikejing.experimentplatform.service.ExperimentService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentService experimentService;

    public ExperimentController(ExperimentService experimentService) {
        this.experimentService = experimentService;
    }

    @PostMapping("/draft")
    public Map<String, Object> saveDraft(@RequestBody ExperimentConfigDto dto) {
        experimentService.saveDraft(toConfig(dto));
        return Map.of("experimentId", dto.getExperimentId(), "status", "DRAFT_SAVED");
    }

    @PostMapping("/{experimentId}/publish")
    public Map<String, Object> publish(@PathVariable String experimentId) {
        return versionView(experimentService.publish(experimentId), "PUBLISHED");
    }

    @PostMapping("/{experimentId}/rollback")
    public Map<String, Object> rollback(@PathVariable String experimentId,
                                        @RequestParam int version) {
        return versionView(experimentService.rollback(experimentId, version), "ROLLED_BACK");
    }

    @GetMapping("/{experimentId}/versions")
    public List<Map<String, Object>> versions(@PathVariable String experimentId) {
        return experimentService.listVersions(experimentId).stream()
                .map(v -> versionView(v, null))
                .toList();
    }

    @GetMapping("/{experimentId}")
    public Map<String, Object> current(@PathVariable String experimentId) {
        return versionView(experimentService.getPublished(experimentId), "PUBLISHED");
    }

    private static ExperimentConfig toConfig(ExperimentConfigDto dto) {
        ExperimentConfig config = new ExperimentConfig();
        config.setExperimentId(dto.getExperimentId());
        config.setName(dto.getName());
        config.setLayerId(dto.getLayerId());
        config.setLayerWeightBps(dto.getLayerWeightBps());
        config.setSalt(dto.getSalt());
        if (dto.getVariants() != null) {
            config.setVariants(dto.getVariants().stream().map(ExperimentController::toVariant).toList());
        }
        return config;
    }

    private static Variant toVariant(VariantDto dto) {
        return new Variant(dto.getName(), dto.getWeightBps());
    }

    private static Map<String, Object> versionView(ExperimentVersion v, String status) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("experimentId", v.getExperimentId());
        map.put("version", v.getVersion());
        map.put("name", v.getName());
        map.put("layerId", v.getLayerId());
        map.put("layerWeightBps", v.getLayerWeightBps());
        map.put("salt", v.getSalt());
        map.put("variants", v.getVariants().stream()
                .map(var -> Map.of("name", var.getName(), "weightBps", var.getWeightBps()))
                .toList());
        map.put("createdAt", v.getCreatedAt().toString());
        if (status != null) {
            map.put("status", status);
        }
        return map;
    }
}
