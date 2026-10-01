package com.github.shikejing.experimentplatform.model;

import java.util.Map;

/**
 * 已发布配置的不可变快照。读取侧永远拿到某一完整快照，
 * 发布/回滚通过原子引用整体替换，不存在半更新状态。
 * <p>
 * 快照同时携带发布时预构建的层/变体分桶索引，分流直接二分查找，
 * 不随实验数量增长而全量扫描。
 */
public final class PublishedSnapshot {
    private final long snapshotId;
    private final Map<String, ExperimentVersion> experiments;
    private final Map<String, LayerShuntIndex> layerIndexes;
    private final Map<String, VariantShuntIndex> variantIndexes;
    private final GlobalConfig globalConfig;

    public PublishedSnapshot(long snapshotId,
                            Map<String, ExperimentVersion> experiments,
                            Map<String, LayerShuntIndex> layerIndexes,
                            Map<String, VariantShuntIndex> variantIndexes,
                            GlobalConfig globalConfig) {
        this.snapshotId = snapshotId;
        this.experiments = Map.copyOf(experiments);
        this.layerIndexes = Map.copyOf(layerIndexes);
        this.variantIndexes = Map.copyOf(variantIndexes);
        this.globalConfig = globalConfig;
    }

    public long getSnapshotId() {
        return snapshotId;
    }

    public Map<String, ExperimentVersion> getExperiments() {
        return experiments;
    }

    public Map<String, LayerShuntIndex> getLayerIndexes() {
        return layerIndexes;
    }

    public Map<String, VariantShuntIndex> getVariantIndexes() {
        return variantIndexes;
    }

    public GlobalConfig getGlobalConfig() {
        return globalConfig;
    }
}
