package com.github.shikejing.experiment.model;

/**
 * 一次分流结果。
 *
 * @param experimentKey 实验标识
 * @param version       命中的实验版本号
 * @param variantKey    命中的变体；未命中时为 null
 * @param reason        未命中原因（命中时为 HIT）
 */
public record Assignment(String experimentKey, long version, String variantKey, Reason reason) {

    public enum Reason {
        HIT,
        EXPERIMENT_NOT_FOUND,
        FALLBACK_EXCLUDED,
        OUT_OF_TRAFFIC
    }

    public boolean hit() {
        return reason == Reason.HIT && variantKey != null;
    }

    public static Assignment miss(String experimentKey, long version, Reason reason) {
        return new Assignment(experimentKey, version, null, reason);
    }
}
