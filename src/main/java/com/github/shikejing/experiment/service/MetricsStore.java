package com.github.shikejing.experiment.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 事件与聚合存储。所有结构均为并发容器：
 * <ul>
 *   <li>曝光按 (实验,版本,单位) 去重，重复上报/超时重试只计一次；</li>
 *   <li>事件按 eventId 去重，保证幂等；</li>
 *   <li>聚合按 (实验,版本,变体,指标) 预聚合在哈希表中，
 *       单次写入与单次查询均为 O(1)，与实验数量、指标维度无关。</li>
 * </ul>
 */
@Component
public class MetricsStore {

    /** 指标聚合：计数 + 总和。更新串行化，保证结果可复现。 */
    public static final class Aggregate {
        private long count;
        private double sum;

        public synchronized void add(double value) {
            count++;
            sum += value;
        }

        public synchronized long count() {
            return count;
        }

        public synchronized double sum() {
            return sum;
        }
    }

    private static final String SEP = "\u0001";

    private final Set<String> exposureEventIds = ConcurrentHashMap.newKeySet();
    private final Set<String> metricEventIds = ConcurrentHashMap.newKeySet();
    private final Set<String> exposedUnits = ConcurrentHashMap.newKeySet();
    private final Map<String, LongAdder> exposureCounts = new ConcurrentHashMap<>();
    private final Map<String, Aggregate> metricAggregates = new ConcurrentHashMap<>();

    static String key(String... parts) {
        return String.join(SEP, parts);
    }

    /** 尝试计入一次曝光；重复 eventId 或重复单位返回 false。 */
    public boolean tryRecordExposure(String experimentKey, long version, String unitId, String eventId) {
        if (!exposureEventIds.add(key(experimentKey, String.valueOf(version), eventId))) {
            return false;
        }
        if (!exposedUnits.add(key(experimentKey, String.valueOf(version), unitId))) {
            return false;
        }
        return true;
    }

    /** 尝试计入一个指标事件；重复 eventId 返回 false。 */
    public boolean tryRecordMetricEvent(String experimentKey, long version, String eventId) {
        return metricEventIds.add(key(experimentKey, String.valueOf(version), eventId));
    }

    public void incrementExposure(String experimentKey, long version, String variantKey) {
        exposureCounts
                .computeIfAbsent(key(experimentKey, String.valueOf(version), variantKey), k -> new LongAdder())
                .increment();
    }

    public long exposureCount(String experimentKey, long version, String variantKey) {
        LongAdder adder = exposureCounts.get(key(experimentKey, String.valueOf(version), variantKey));
        return adder == null ? 0L : adder.sum();
    }

    public void addMetric(String experimentKey, long version, String variantKey, String metricName, double value) {
        metricAggregates
                .computeIfAbsent(key(experimentKey, String.valueOf(version), variantKey, metricName),
                        k -> new Aggregate())
                .add(value);
    }

    public Aggregate metricAggregate(String experimentKey, long version, String variantKey, String metricName) {
        return metricAggregates.get(key(experimentKey, String.valueOf(version), variantKey, metricName));
    }

    /** 指定版本下出现过曝光的变体 key（确定序）。 */
    public List<String> exposedVariants(String experimentKey, long version) {
        String prefix = key(experimentKey, String.valueOf(version)) + SEP;
        return exposureCounts.keySet().stream()
                .filter(k -> k.startsWith(prefix))
                .map(k -> k.substring(prefix.length()))
                .sorted()
                .toList();
    }
}
