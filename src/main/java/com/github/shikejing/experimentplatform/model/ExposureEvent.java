package com.github.shikejing.experimentplatform.model;

/**
 * 曝光/指标上报事件。eventId 为幂等键。
 */
public class ExposureEvent {
    private String eventId;      // 幂等键，客户端生成
    private String unitId;
    private String experimentId;
    private String variantName;
    private String metric;       // 指标名，如 click / stay_duration
    private Double value;        // 指标值，可为空（计数型指标）
    private long timestamp;

    public ExposureEvent() {
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUnitId() {
        return unitId;
    }

    public void setUnitId(String unitId) {
        this.unitId = unitId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public String getVariantName() {
        return variantName;
    }

    public void setVariantName(String variantName) {
        this.variantName = variantName;
    }

    public String getMetric() {
        return metric;
    }

    public void setMetric(String metric) {
        this.metric = metric;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
