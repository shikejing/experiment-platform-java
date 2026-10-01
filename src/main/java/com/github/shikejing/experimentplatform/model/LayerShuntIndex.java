package com.github.shikejing.experimentplatform.model;

import java.util.List;

/**
 * 互斥层分桶索引：层内实验按 experimentId 排序，权重区间互不重叠，
 * 分流时对桶号做二分查找，O(log E) 命中层内实验。
 */
public final class LayerShuntIndex {

    public record Range(int startBps, int endBps, String experimentId) {
    }

    private final String layerId;
    private final List<Range> ranges;

    public LayerShuntIndex(String layerId, List<Range> ranges) {
        this.layerId = layerId;
        this.ranges = List.copyOf(ranges);
    }

    public String getLayerId() {
        return layerId;
    }

    public List<Range> getRanges() {
        return ranges;
    }

    /**
     * 二分查找桶号命中的实验；未命中（层内流量未铺满）返回 null。
     */
    public String pickExperiment(int bucket) {
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
                return r.experimentId();
            }
        }
        return null;
    }
}
