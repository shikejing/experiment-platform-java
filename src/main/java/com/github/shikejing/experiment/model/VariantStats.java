package com.github.shikejing.experiment.model;

/**
 * 单变体某指标的统计结果。样本为空时 count 为 0，ratio/mean 为 null，不产生无意义数值。
 */
public record VariantStats(String variantKey, long count, Double ratio, Double mean) {
}
