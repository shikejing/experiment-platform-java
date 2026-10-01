package com.github.shikejing.experimentplatform.exceptions;

public class VariantNotFoundException extends ExperimentPlatformException {
    public VariantNotFoundException(String experimentId, String variantName) {
        super("variant not found: experiment=" + experimentId + ", variant=" + variantName,
                experimentId, variantName);
    }
}
