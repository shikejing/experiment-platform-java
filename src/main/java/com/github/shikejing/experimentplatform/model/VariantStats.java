package com.github.shikejing.experimentplatform.model;

/**
 * 单变体指标统计。分母为 0 / 无样本时 proportion=0、mean=null，不产生无意义数值。
 */
public final class VariantStats {
    private final String variantName;
    private final long count;        // 样本数（曝光次数）
    private final double proportion;  // 占该实验总样本的比例
    private final Double mean;       // 均值，无值字段样本时为 null
    private final long valueCount;   // 携带非空 value 的样本数
    private final double valueSum;   // 值字段合计

    public VariantStats(String variantName, long count, double proportion, Double mean,
                        long valueCount, double valueSum) {
        this.variantName = variantName;
        this.count = count;
        this.proportion = proportion;
        this.mean = mean;
        this.valueCount = valueCount;
        this.valueSum = valueSum;
    }

    public String getVariantName() {
        return variantName;
    }

    public long getCount() {
        return count;
    }

    public double getProportion() {
        return proportion;
    }

    public Double getMean() {
        return mean;
    }

    public long getValueCount() {
        return valueCount;
    }

    public double getValueSum() {
        return valueSum;
    }
}
