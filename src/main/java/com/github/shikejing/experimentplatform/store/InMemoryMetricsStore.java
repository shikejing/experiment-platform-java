package com.github.shikejing.experimentplatform.store;

import com.github.shikejing.experimentplatform.model.ExperimentStats;
import com.github.shikejing.experimentplatform.model.VariantStats;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标聚合存储。
 * <p>
 * 按 (实验, 指标名) -> 变体 -> 计数器 建立嵌套索引，写入为 O(1) 累加，
 * 查询直接命中索引，耗时仅与该实验的变体数有关，不随实验总数/指标维度
 * 增长而全量扫描。分母为 0 时比例返回 0、均值返回 null，不产生 NaN。
 */
@Repository
public class InMemoryMetricsStore implements MetricsStore {

    private record Counter(long count, double valueSum, long valueCount) {
        Counter add(Double value) {
            if (value == null) {
                return new Counter(count + 1, valueSum, valueCount);
            }
            return new Counter(count + 1, valueSum + value, valueCount + 1);
        }
    }

    /** experimentId -> metric -> variant -> counter */
    private final Map<String, Map<String, Map<String, Counter>>> index = new ConcurrentHashMap<>();

    @Override
    public void record(String experimentId, String variantName, String metric, Double value) {
        index.computeIfAbsent(experimentId, e -> new ConcurrentHashMap<>())
                .computeIfAbsent(metric, m -> new ConcurrentHashMap<>())
                .merge(variantName, new Counter(0, 0, 0).add(value),
                        (old, ignored) -> old.add(value));
    }

    @Override
    public ExperimentStats stats(String experimentId, String metric) {
        Map<String, Counter> perVariant = index
                .getOrDefault(experimentId, Map.of())
                .getOrDefault(metric, Map.of());

        long total = perVariant.values().stream().mapToLong(c -> c.count).sum();

        List<VariantStats> rows = perVariant.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Counter c = e.getValue();
                    double proportion = total == 0 ? 0.0 : (double) c.count / total;
                    Double mean = c.valueCount == 0 ? null : c.valueSum / c.valueCount;
                    return new VariantStats(e.getKey(), c.count, proportion, mean,
                            c.valueCount, c.valueSum);
                })
                .toList();

        return new ExperimentStats(experimentId, metric, total, rows);
    }
}
