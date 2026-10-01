package com.github.shikejing.experimentplatform.model;

import java.time.Instant;
import java.util.List;

/**
 * 已提交的不可变实验版本。分流只允许引用已发布版本的完整快照。
 */
public final class ExperimentVersion {
    private final String experimentId;
    private final int version;
    private final String name;
    private final String layerId;
    private final int layerWeightBps;
    private final String salt;
    private final List<Variant> variants;
    private final Instant createdAt;

    public ExperimentVersion(String experimentId, int version, String name, String layerId,
                             int layerWeightBps, String salt, List<Variant> variants,
                             Instant createdAt) {
        this.experimentId = experimentId;
        this.version = version;
        this.name = name;
        this.layerId = layerId;
        this.layerWeightBps = layerWeightBps;
        this.salt = salt;
        this.variants = List.copyOf(variants);
        this.createdAt = createdAt;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public int getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public String getLayerId() {
        return layerId;
    }

    public int getLayerWeightBps() {
        return layerWeightBps;
    }

    public String getSalt() {
        return salt;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
