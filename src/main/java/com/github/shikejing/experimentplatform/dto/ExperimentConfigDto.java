package com.github.shikejing.experimentplatform.dto;

import java.util.List;

public class ExperimentConfigDto {
    private String experimentId;
    private String name;
    private String layerId;
    private int layerWeightBps;
    private String salt;
    private List<VariantDto> variants;

    public String getExperimentId() {
        return experimentId;
    }

    public void setExperimentId(String experimentId) {
        this.experimentId = experimentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLayerId() {
        return layerId;
    }

    public void setLayerId(String layerId) {
        this.layerId = layerId;
    }

    public int getLayerWeightBps() {
        return layerWeightBps;
    }

    public void setLayerWeightBps(int layerWeightBps) {
        this.layerWeightBps = layerWeightBps;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public List<VariantDto> getVariants() {
        return variants;
    }

    public void setVariants(List<VariantDto> variants) {
        this.variants = variants;
    }
}
