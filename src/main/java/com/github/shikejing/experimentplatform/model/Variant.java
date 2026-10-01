package com.github.shikejing.experimentplatform.model;

/**
 * 实验变体（分组）定义。
 */
public class Variant {
    private String name;
    private int weightBps; // 万分比权重，变体间合计 10000

    public Variant() {
    }

    public Variant(String name, int weightBps) {
        this.name = name;
        this.weightBps = weightBps;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWeightBps() {
        return weightBps;
    }

    public void setWeightBps(int weightBps) {
        this.weightBps = weightBps;
    }
}
