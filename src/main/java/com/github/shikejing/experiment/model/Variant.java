package com.github.shikejing.experiment.model;

import java.util.Objects;

/**
 * 变体定义。weight 为整数权重，同一实验所有变体权重之和必须为 100。
 */
public final class Variant {

    private final String key;
    private final int weight;

    public Variant(String key, int weight) {
        this.key = Objects.requireNonNull(key, "key");
        this.weight = weight;
    }

    public String getKey() {
        return key;
    }

    public int getWeight() {
        return weight;
    }
}
