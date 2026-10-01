package com.github.shikejing.experiment.model;

/**
 * 指标上报。eventId 用于幂等去重；value 为数值型指标取值。
 */
public record MetricEvent(String eventId, String experimentKey, long version,
                          String unitId, String variantKey, String metricName, double value) {
}
