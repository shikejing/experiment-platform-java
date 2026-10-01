package com.github.shikejing.experimentplatform.dto;

public class GlobalConfigDto {
    private int fallbackRateBps;
    private String fallbackSalt;

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
