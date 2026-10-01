package com.github.shikejing.experiment.service;

import com.github.shikejing.experiment.model.ExperimentVersion;
import com.github.shikejing.experiment.model.Variant;
import com.github.shikejing.experiment.model.VariantStats;
import com.github.shikejing.experiment.registry.ExperimentRegistry;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 统计查询：按实验当前版本给出各变体的计数、比例与均值。
 *
 * <p>口径：count 为该变体去重后的曝光单位数；ratio 为该变体曝光数
 * 占实验总曝光数的比例；mean 为该变体该指标上报值的算术平均。
 * 分母为零、样本为空或指标无数据时对应字段为 null，不产生
 * NaN/Infinity 等无意义数值。</p>
 *
 * <p>单次查询耗时上界：O(V)，V 为该实验的变体数——所有聚合已按
 * (实验,版本,变体,指标) 预聚合在哈希表中，无需全量扫描事件。</p>
 */
@Service
public class StatsService {

    private final ExperimentRegistry registry;
    private final MetricsStore store;

    public StatsService(ExperimentRegistry registry, MetricsStore store) {
        this.registry = registry;
        this.store = store;
    }

    /**
     * 查询某实验某指标在当前版本下各变体的统计。
     *
     * @throws IllegalArgumentException 实验不存在或未发布，异常信息包含实验标识
     */
    public List<VariantStats> stats(String experimentKey, String metricName) {
        ExperimentVersion version = registry.current(experimentKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        "experiment not found or not published: " + experimentKey));
        long v = version.getVersion();

        List<Variant> variants = version.getVariants().stream()
                .sorted(Comparator.comparing(Variant::getKey))
                .toList();
        long total = variants.stream()
                .mapToLong(variant -> store.exposureCount(experimentKey, v, variant.getKey()))
                .sum();

        return variants.stream().map(variant -> {
            String variantKey = variant.getKey();
            long count = store.exposureCount(experimentKey, v, variantKey);
            Double ratio = total == 0 ? null : (double) count / (double) total;
            MetricsStore.Aggregate aggregate =
                    store.metricAggregate(experimentKey, v, variantKey, metricName);
            Double mean = (aggregate == null || aggregate.count() == 0)
                    ? null : aggregate.sum() / aggregate.count();
            return new VariantStats(variantKey, count, ratio, mean);
        }).toList();
    }
}
