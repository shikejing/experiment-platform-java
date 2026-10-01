package com.github.shikejing.experiment.model;

import java.time.Instant;
import java.util.List;

/**
 * 已提交的实验版本，不可变。分流结果只能来自某个完整版本。
 */
public final class ExperimentVersion {

    private final String experimentKey;
    private final long version;
    private final ExperimentConfig config;
    private final List<Variant> variants;
    private final Instant publishedAt;

    public ExperimentVersion(String experimentKey, long version, ExperimentConfig config, Instant publishedAt) {
        this.experimentKey = experimentKey;
        this.version = version;
        this.config = config;
        this.variants = List.copyOf(config.variants());
        this.publishedAt = publishedAt;
    }

    public String getExperimentKey() {
        return experimentKey;
    }

    public long getVersion() {
        return version;
    }

    public ExperimentConfig getConfig() {
        return config;
    }

    public List<Variant> getVariants() {
        return variants;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
