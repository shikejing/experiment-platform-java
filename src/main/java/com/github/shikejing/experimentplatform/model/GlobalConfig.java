package com.github.shikejing.experimentplatform.model;

/**
 * 全局配置：全局兜底流量开关。被兜底命中的单位不进入任何实验。
 */
public class GlobalConfig {
    private int fallbackRateBps; // 万分比
    private String fallbackSalt;

    public GlobalConfig() {
    }

    public int getFallbackRateBps() {
        return fallbackRateBps;
    }

    public void setFallbackRateBps(int fallbackRateBps) {
        this.fallbackRateBps = fallbackRateBps;
    }

    public String getFallbackSalt() {
        return fallbackSalt;
    }

    public void setFallbackSalt(String fallbackSalt) {
        this.fallbackSalt = fallbackSalt;
    }
}
