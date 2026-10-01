package com.github.shikejing.experiment.core;

import com.github.shikejing.experiment.model.ExperimentVersion;
import com.github.shikejing.experiment.model.Variant;

import java.util.Comparator;
import java.util.List;

/**
 * 纯函数分流器：给定不可变版本与实验单位，确定性地返回变体。
 *
 * <p>分流键 = salt:experimentKey:unitId，经 {@link Hasher} 映射到
 * [0,10000) 槽位；变体按 key 字典序排列后按权重（权重×100 个槽位）
 * 依次占据连续区间，因此结果与配置列表顺序、运行时随机化、
 * 并发度及进程重启均无关。</p>
 */
public final class BucketAssigner {

    private BucketAssigner() {
    }

    /**
     * 在版本内为实验单位分配变体。权重之和必须为 100（发布时已校验）。
     */
    public static Variant assign(ExperimentVersion version, String unitId) {
        String hashKey = version.getConfig().salt() + ":" + version.getExperimentKey() + ":" + unitId;
        int slot = Hasher.slot(hashKey);
        List<Variant> variants = version.getVariants().stream()
                .sorted(Comparator.comparing(Variant::getKey))
                .toList();
        int cumulative = 0;
        for (Variant v : variants) {
            cumulative += v.getWeight() * 100;
            if (slot < cumulative) {
                return v;
            }
        }
        // 权重之和为 100 时不可达；防御性返回最后一个变体
        return variants.get(variants.size() - 1);
    }
}
