package com.github.shikejing.experimentplatform.engine;

import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import com.github.shikejing.experimentplatform.model.GlobalConfig;
import com.github.shikejing.experimentplatform.model.LayerShuntIndex;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import com.github.shikejing.experimentplatform.model.VariantShuntIndex;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 快照工厂：发布/回滚时由完整实验集合构建不可变快照与预分桶索引。
 * 索引构建只发生在发布写路径（低频），读路径直接使用，不做全量扫描。
 */
@Component
public class SnapshotFactory {

    /** 独立实验（未配置互斥层）的隐式层 id 前缀，每个独立实验独占一层。 */
    public static final String INDEPENDENT_LAYER_PREFIX = "~independent~";

    /** 独立实验默认流量：100% 进入分桶。 */
    public static final int DEFAULT_INDEPENDENT_WEIGHT_BPS = 10000;

    public PublishedSnapshot create(long snapshotId,
                                    Map<String, ExperimentVersion> experiments,
                                    GlobalConfig globalConfig) {
        // 按有效互斥层分组
        Map<String, List<ExperimentVersion>> grouped = new TreeMap<>();
        for (ExperimentVersion v : experiments.values()) {
            String layerId = effectiveLayerId(v);
            grouped.computeIfAbsent(layerId, k -> new ArrayList<>()).add(v);
        }

        Map<String, LayerShuntIndex> layerIndexes = new TreeMap<>();
        Map<String, VariantShuntIndex> variantIndexes = new HashMap<>();

        for (Map.Entry<String, List<ExperimentVersion>> entry : grouped.entrySet()) {
            // 层内按实验 id 排序，保证区间顺序确定，与迭代顺序无关
            List<ExperimentVersion> list = entry.getValue();
            list.sort(Comparator.comparing(ExperimentVersion::getExperimentId));

            List<LayerShuntIndex.Range> ranges = new ArrayList<>();
            int cum = 0;
            for (ExperimentVersion v : list) {
                int w = effectiveLayerWeight(v);
                ranges.add(new LayerShuntIndex.Range(cum, cum + w, v.getExperimentId()));
                cum += w;
                variantIndexes.put(v.getExperimentId(), buildVariantIndex(v));
            }
            layerIndexes.put(entry.getKey(), new LayerShuntIndex(entry.getKey(), ranges));
        }

        return new PublishedSnapshot(snapshotId, experiments, layerIndexes, variantIndexes,
                globalConfig);
    }

    public static String effectiveLayerId(ExperimentVersion v) {
        if (v.getLayerId() == null || v.getLayerId().isEmpty()) {
            return INDEPENDENT_LAYER_PREFIX + v.getExperimentId();
        }
        return v.getLayerId();
    }

    public static int effectiveLayerWeight(ExperimentVersion v) {
        if (v.getLayerId() == null || v.getLayerId().isEmpty()) {
            return v.getLayerWeightBps() > 0 ? v.getLayerWeightBps()
                    : DEFAULT_INDEPENDENT_WEIGHT_BPS;
        }
        return v.getLayerWeightBps();
    }

    private VariantShuntIndex buildVariantIndex(ExperimentVersion v) {
        // 变体按名称排序，区间顺序确定
        List<com.github.shikejing.experimentplatform.model.Variant> sorted =
                new ArrayList<>(v.getVariants());
        sorted.sort(Comparator.comparing(com.github.shikejing.experimentplatform.model.Variant::getName));

        List<VariantShuntIndex.Range> ranges = new ArrayList<>();
        int cum = 0;
        for (var variant : sorted) {
            ranges.add(new VariantShuntIndex.Range(cum, cum + variant.getWeightBps(),
                    variant.getName()));
            cum += variant.getWeightBps();
        }
        return new VariantShuntIndex(v.getExperimentId(), ranges);
    }
}
