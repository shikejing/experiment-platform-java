package com.github.shikejing.experimentplatform.model;

import java.time.Instant;

/**
 * 一次分流结果：单位在某实验某版本下命中的变体。
 */
public final class Assignment {
    private final String unitId;
    private final String experimentId;
    private final int version;
    private final String layerId;
    private final String variantName;
    private final Instant assignedAt;

    public Assignment(String unitId, String experimentId, int version, String layerId,
                      String variantName, Instant assignedAt) {
        this.unitId = unitId;
        this.experimentId = experimentId;
        this.version = version;
        this.layerId = layerId;
        this.variantName = variantName;
        this.assignedAt = assignedAt;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public int getVersion() {
        return version;
    }

    public String getLayerId() {
        return layerId;
    }

    public String getVariantName() {
        return variantName;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
