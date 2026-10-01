package com.github.shikejing.experiment.service;

import com.github.shikejing.experiment.core.BucketAssigner;
import com.github.shikejing.experiment.core.Hasher;
import com.github.shikejing.experiment.model.Assignment;
import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.ExperimentVersion;
import com.github.shikejing.experiment.model.Variant;
import com.github.shikejing.experiment.registry.ExperimentRegistry;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 分流服务。判定顺序固定为：
 * <ol>
 *   <li>实验/版本存在性；</li>
 *   <li>全局兜底：hash("fallback:"+unitId) 落入兜底区间的单位被所有实验排除；</li>
 *   <li>互斥层槽位：hash("layer:"+layerKey+":"+unitId) 决定单位进入层内哪个实验，
 *       层内各实验槽位区间发布时已校验互不重叠，因此同一单位不会同时进入
 *       同层相互冲突的多个实验；</li>
 *   <li>变体分配：{@link BucketAssigner} 纯函数，确定且稳定。</li>
 * </ol>
 * 单次分流耗时上界：O(1)——两次哈希计算加一次哈希表查询，与实验总数无关。
 */
@Service
public class AssignmentService {

    private final ExperimentRegistry registry;

    public AssignmentService(ExperimentRegistry registry) {
        this.registry = registry;
    }

    /**
     * 按实验当前版本分流。
     */
    public Assignment assign(String experimentKey, String unitId) {
        Optional<ExperimentVersion> version = registry.current(experimentKey);
        if (version.isEmpty()) {
            return Assignment.miss(experimentKey, -1, Assignment.Reason.EXPERIMENT_NOT_FOUND);
        }
        return doAssign(version.get(), unitId);
    }

    /**
     * 按指定版本分流（用于复现历史分配，回滚后同一输入结果与当时一致）。
     */
    public Assignment assign(String experimentKey, long version, String unitId) {
        Optional<ExperimentVersion> v = registry.version(experimentKey, version);
        if (v.isEmpty()) {
            return Assignment.miss(experimentKey, version, Assignment.Reason.EXPERIMENT_NOT_FOUND);
        }
        return doAssign(v.get(), unitId);
    }

    private Assignment doAssign(ExperimentVersion version, String unitId) {
        if (unitId == null || unitId.isBlank()) {
            return Assignment.miss(version.getExperimentKey(), version.getVersion(),
                    Assignment.Reason.EXPERIMENT_NOT_FOUND);
        }
        String key = version.getExperimentKey();
        long v = version.getVersion();

        // 1. 全局兜底：被兜底命中的单位不得进入任何实验
        int fallbackSlot = Hasher.slot("fallback:" + unitId);
        if (fallbackSlot < registry.fallbackPermille()) {
            return Assignment.miss(key, v, Assignment.Reason.FALLBACK_EXCLUDED);
        }

        // 2. 互斥层槽位判定
        ExperimentConfig config = version.getConfig();
        if (config.inLayer()) {
            int layerSlot = Hasher.slot("layer:" + config.layerKey() + ":" + unitId);
            if (layerSlot < config.layerStartSlot() || layerSlot >= config.layerEndSlot()) {
                return Assignment.miss(key, v, Assignment.Reason.OUT_OF_TRAFFIC);
            }
        }

        // 3. 变体分配（确定性纯函数）
        Variant variant = BucketAssigner.assign(version, unitId);
        return new Assignment(key, v, variant.getKey(), Assignment.Reason.HIT);
    }
}
