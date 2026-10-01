package com.github.shikejing.experimentplatform.model;

import java.util.List;

/**
 * 实验内变体分桶索引：变体按名称排序，权重区间互不重叠，
 * 分流时对桶号二分查找，O(log V) 命中变体。
 */
public final class VariantShuntIndex {

    public record Range(int startBps, int endBps, String variantName) {
    }

    private final String experimentId;
    private final List<Range> ranges;

    public VariantShuntIndex(String experimentId, List<Range> ranges) {
        this.experimentId = experimentId;
        this.ranges = List.copyOf(ranges);
    }

    public String getExperimentId() {
        return experimentId;
    }

    public List<Range> getRanges() {
        return ranges;
    }

    /**
     * 二分查找桶号命中的变体；未命中返回 null。
     */
    public String pickVariant(int bucket) {
        int lo = 0;
        int hi = ranges.size() - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            Range r = ranges.get(mid);
            if (bucket < r.startBps()) {
                hi = mid - 1;
            } else if (bucket >= r.endBps()) {
                lo = mid + 1;
            } else {
                return r.variantName();
            }
        }
        return null;
    }
}
