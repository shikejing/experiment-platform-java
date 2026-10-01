package com.github.shikejing.experimentplatform.api;

import com.github.shikejing.experimentplatform.dto.GlobalConfigDto;
import com.github.shikejing.experimentplatform.model.GlobalConfig;
import com.github.shikejing.experimentplatform.service.ConfigService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/config/global")
public class GlobalConfigController {

    private final ConfigService configService;

    public GlobalConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @PostMapping
    public void update(@RequestBody GlobalConfigDto dto) {
        GlobalConfig config = new GlobalConfig();
        config.setFallbackRateBps(dto.getFallbackRateBps());
        config.setFallbackSalt(dto.getFallbackSalt());
        configService.updateGlobal(config);
    }

    @GetMapping
    public GlobalConfigDto current() {
        GlobalConfig current = configService.current();
        GlobalConfigDto dto = new GlobalConfigDto();
        dto.setFallbackRateBps(current.getFallbackRateBps());
        dto.setFallbackSalt(current.getFallbackSalt());
        return dto;
    }
}
