package com.github.shikejing.experimentplatform.engine;

import com.github.shikejing.experimentplatform.model.Assignment;
import com.github.shikejing.experimentplatform.model.ExperimentVersion;
import com.github.shikejing.experimentplatform.model.GlobalConfig;
import com.github.shikejing.experimentplatform.model.LayerShuntIndex;
import com.github.shikejing.experimentplatform.model.PublishedSnapshot;
import com.github.shikejing.experimentplatform.model.VariantShuntIndex;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 分流引擎：在不可变快照上做确定性分配。
 * <p>
 * 单次分流耗时上界：O(L · log(E/L) + H · log V)，其中 L 为互斥层数量，
 * E 为实验总数，H ≤ L 为命中的实验数，V 为单实验变体数。
 * 全局兜底为 1 次哈希；每层 1 次哈希 + 二分；命中后每实验 1 次哈希 + 二分。
 * 不随实验总数做全量扫描，也不受迭代顺序、并发度或重启影响。
 */
@Component
public class ShuntEngine {

    private static final int BPS = 10000;
    private static final String DEFAULT_FALLBACK_SALT = "global_fallback";
    private static final String LAYER_SALT_PREFIX = "layer:";

    private final Bucketer bucketer;

    public ShuntEngine(Bucketer bucketer) {
        this.bucketer = bucketer;
    }

    public List<Assignment> shunt(String unitId, PublishedSnapshot snapshot) {
        GlobalConfig global = snapshot.getGlobalConfig();
        String fallbackSalt = global.getFallbackSalt() == null
                ? DEFAULT_FALLBACK_SALT : global.getFallbackSalt();

        // 1. 全局兜底：命中兜底的单位不进入任何实验
        int fallbackBucket = bucketer.bucket(fallbackSalt, unitId, BPS);
        if (fallbackBucket < global.getFallbackRateBps()) {
            return List.of();
        }

        // 2. 逐层分流：同层至多命中一个实验（互斥），层间相互独立
        List<Assignment> assignments = new ArrayList<>();
        for (LayerShuntIndex layer : snapshot.getLayerIndexes().values()) {
            int layerBucket = bucketer.bucket(LAYER_SALT_PREFIX + layer.getLayerId(), unitId, BPS);
            String experimentId = layer.pickExperiment(layerBucket);
            if (experimentId == null) {
                continue;
            }

            ExperimentVersion version = snapshot.getExperiments().get(experimentId);
            VariantShuntIndex variantIndex = snapshot.getVariantIndexes().get(experimentId);
            if (version == null || variantIndex == null) {
                // 快照不完整属于发布缺陷，直接失败，不得静默混合配置
                throw new IllegalStateException("incomplete snapshot for experiment: "
                        + experimentId);
            }

            String salt = (version.getSalt() == null || version.getSalt().isEmpty())
                    ? experimentId : version.getSalt();
            int variantBucket = bucketer.bucket(salt, unitId, BPS);
            String variant = variantIndex.pickVariant(variantBucket);
            if (variant == null) {
                // 变体权重未铺满等异常：不分配，保证不产生错误命中
                continue;
            }
            assignments.add(new Assignment(unitId, experimentId, version.getVersion(),
                    version.getLayerId(), variant, Instant.now()));
        }
        return assignments;
    }
}
