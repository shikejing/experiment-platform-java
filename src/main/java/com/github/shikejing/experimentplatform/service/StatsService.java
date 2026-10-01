package com.github.shikejing.experimentplatform.service;

import com.github.shikejing.experimentplatform.exceptions.ExperimentNotFoundException;
import com.github.shikejing.experimentplatform.model.ExperimentStats;
import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import com.github.shikejing.experimentplatform.model.VariantStats;
import com.github.shikejing.experimentplatform.store.MetricsStore;
import com.github.shikejing.experimentplatform.store.SnapshotStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 指标统计服务：直接读取预聚合索引，耗时仅与实验变体数有关，
 * 不扫描明细事件。聚合结果可复现；分母为 0 / 无值字段时
 * 比例为 0、均值为 null，不产生无意义数值。
 */
@Service
public class StatsService {

    private static final String DEFAULT_METRIC = "exposure";

    private final SnapshotStore snapshotStore;
    private final MetricsStore metricsStore;

    public StatsService(SnapshotStore snapshotStore, MetricsStore metricsStore) {
        this.snapshotStore = snapshotStore;
        this.metricsStore = metricsStore;
    }

    public ExperimentStats stats(String experimentId, String metric) {
        PublishedSnapshot snapshot = snapshotStore.get();
        ExperimentVersion version = snapshot.getExperiments().get(experimentId);
        if (version == null) {
            throw new ExperimentNotFoundException(experimentId);
        }
        String m = metric == null ? DEFAULT_METRIC : metric;

        ExperimentStats raw = metricsStore.stats(experimentId, m);
        Map<String, VariantStats> recorded = new HashMap<>();
        for (VariantStats vs : raw.getVariants()) {
            recorded.put(vs.getVariantName(), vs);
        }

        // 补齐已发布变体的零计数行，保证空样本也是结构完整的结果
        List<VariantStats> rows = new ArrayList<>();
        for (String variantName : version.getVariants().stream()
                .map(v -> v.getName()).sorted().toList()) {
            VariantStats row = recorded.get(variantName);
            if (row == null) {
                row = new VariantStats(variantName, 0L, 0.0, null, 0L, 0.0);
            }
            rows.add(row);
        }
        return new ExperimentStats(experimentId, m, raw.getTotalCount(), rows);
    }
}
