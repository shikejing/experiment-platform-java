package com.github.shikejing.experimentplatform.exceptions;

public class ExperimentNotFoundException extends ExperimentPlatformException {
    public ExperimentNotFoundException(String experimentId) {
        super("experiment not found or not published: " + experimentId, experimentId, null);
    }
}
