package com.github.shikejing.experimentplatform.exceptions;

/**
 * 上报变体与实际命中变体不一致（未真正命中实验），不计入任何变体。
 */
public class AssignmentMismatchException extends ExperimentPlatformException {
    private final String unitId;
    private final String expectedVariant;

    public AssignmentMismatchException(String experimentId, String variantName, String unitId,
                                       String expectedVariant) {
        super("exposure variant mismatch: experiment=" + experimentId + ", variant=" + variantName
                + ", unit=" + unitId + ", expected=" + expectedVariant, experimentId, variantName);
        this.unitId = unitId;
        this.expectedVariant = expectedVariant;
    }

    public String getUnitId() {
        return unitId;
    }

    public String getExpectedVariant() {
        return expectedVariant;
    }
}
