package com.github.shikejing.experimentplatform.model;

import java.util.List;

/**
 * 实验级指标统计：总样本 + 各变体计数/比例/均值。
 */
public final class ExperimentStats {
    private final String experimentId;
    private final String metric;
    private final long totalCount;
    private final List<VariantStats> variants;

    public ExperimentStats(String experimentId, String metric, long totalCount,
                           List<VariantStats> variants) {
        this.experimentId = experimentId;
        this.metric = metric;
        this.totalCount = totalCount;
        this.variants = List.copyOf(variants);
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getMetric() {
        return metric;
    }

    public long getTotalCount() {
        return totalCount;
    }

    public List<VariantStats> getVariants() {
        return variants;
    }
}
