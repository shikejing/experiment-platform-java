package com.github.shikejing.experimentplatform.model;

/**
 * 曝光写入结果。重复上报返回原结果（duplicate=true），不计入新计数。
 */
public final class ExposureResult {
    private final boolean accepted;
    private final boolean duplicate;
    private final String experimentId;
    private final String variantName;
    private final String reason;

    private ExposureResult(boolean accepted, boolean duplicate, String experimentId,
                           String variantName, String reason) {
        this.accepted = accepted;
        this.duplicate = duplicate;
        this.experimentId = experimentId;
        this.variantName = variantName;
        this.reason = reason;
    }

    public static ExposureResult accepted(String experimentId, String variantName) {
        return new ExposureResult(true, false, experimentId, variantName, null);
    }

    public static ExposureResult duplicateOf(ExposureResult original) {
        return new ExposureResult(true, true, original.experimentId, original.variantName,
                "duplicate event, idempotent replay");
    }

    public static ExposureResult rejected(String experimentId, String variantName, String reason) {
        return new ExposureResult(false, false, experimentId, variantName, reason);
    }

    public boolean isAccepted() {
        return accepted;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getVariantName() {
        return variantName;
    }

    public String getReason() {
        return reason;
    }
}
