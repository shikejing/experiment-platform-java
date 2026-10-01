package com.github.shikejing.experimentplatform.exceptions;

/**
 * 平台异常基类，异常链始终携带 experimentId / variantId 便于定位。
 */
public class ExperimentPlatformException extends RuntimeException {
    private final String experimentId;
    private final String variantId;

    public ExperimentPlatformException(String message, String experimentId, String variantId) {
        super(message);
        this.experimentId = experimentId;
        this.variantId = variantId;
    }

    public ExperimentPlatformException(String message, String experimentId, String variantId,
                                       Throwable cause) {
        super(message, cause);
        this.experimentId = experimentId;
        this.variantId = variantId;
    }

    public String getExperimentId() {
        return experimentId;
    }

    public String getVariantId() {
        return variantId;
    }
}
