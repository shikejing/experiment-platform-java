package com.github.shikejing.experiment.registry;

import com.github.shikejing.experiment.model.ExperimentConfig;
import com.github.shikejing.experiment.model.ExperimentVersion;

import java.util.List;
import java.util.Optional;

/**
 * 实验注册表：管理版本化配置、互斥层与全局兜底比例。
 * 读操作基于不可变快照，写操作整体校验后原子切换，失败时保留旧版本。
 */
public interface ExperimentRegistry {

    /**
     * 发布新版本。校验失败抛出异常，当前服务版本不受影响。
     *
     * @return 新版本号
     */
    long publish(ExperimentConfig config);

    /**
     * 回滚到历史版本（作为新的当前版本生效，内容与原历史版本完全一致）。
     *
     * @return 生效的版本号
     */
    long rollback(String experimentKey, long toVersion);

    /**
     * 查询实验当前生效版本。
     */
    Optional<ExperimentVersion> current(String experimentKey);

    /**
     * 查询指定历史版本（用于复现历史分流）。
     */
    Optional<ExperimentVersion> version(String experimentKey, long version);

    /**
     * 当前快照下所有生效实验版本。
     */
    List<ExperimentVersion> currentAll();

    /**
     * 更新全局兜底比例（万分比）。校验失败抛异常，旧值继续生效。
     */
    void updateFallbackRatio(int fallbackPermille);

    /**
     * 当前全局兜底比例（万分比）。
     */
    int fallbackPermille();
}
