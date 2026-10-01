package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExperimentStats;

/**
 * 指标聚合存储：按 (实验, 变体, 指标名) 维护聚合计数器，
 * 统计查询直接读聚合结果，不扫描明细。
 */
public interface MetricsStore {
    void record(String experimentId, String variantName, String metric, Double value);

    ExperimentStats stats(String experimentId, String metric);
}
