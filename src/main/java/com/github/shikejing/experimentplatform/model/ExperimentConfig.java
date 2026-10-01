package com.github.shikejing.experimentplatform.model;

import java.util.List;

/**
 * 实验配置草稿（编辑态，未发布）。
 */
public class ExperimentConfig {
    private String experimentId;
    private String name;
    private String layerId;       // 互斥层 id，为空表示独立实验
    private int layerWeightBps;   // 在互斥层内的流量权重（万分比）
    private String salt;          // 分桶盐值，为空时使用实验 id
    private List<Variant> variants;

    public ExperimentConfig() {
    }

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

    public List<Variant> getVariants() {
        return variants;
    }

    public void setVariants(List<Variant> variants) {
        this.variants = variants;
    }
}
