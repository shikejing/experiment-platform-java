package com.github.shikejing.experiment.model;

/**
 * 曝光上报。eventId 由客户端生成，用于幂等去重。
 */
public record ExposureEvent(String eventId, String experimentKey, long version,
                            String unitId, String variantKey) {
}
