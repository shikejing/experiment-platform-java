package com.github.shikejing.experiment.model;

import java.util.List;

/**
 * 实验配置草稿。发布时会被整体校验并固化为一个不可变版本。
 *
 * @param key            实验唯一标识
 * @param layerKey       所属互斥层，null 或空表示独占流量（不参与互斥）
 * @param layerStartSlot 在互斥层槽位空间 [0,10000) 中的起始槽位（含）
 * @param layerEndSlot   结束槽位（不含）；不参与互斥层时忽略
 * @param salt           哈希盐，版本内固定，保证分流可复现
 * @param variants       变体列表，权重之和必须为 100
 */
public record ExperimentConfig(
        String key,
        String layerKey,
        int layerStartSlot,
        int layerEndSlot,
        String salt,
        List<Variant> variants) {

    public boolean inLayer() {
        return layerKey != null && !layerKey.isBlank();
    }
}
