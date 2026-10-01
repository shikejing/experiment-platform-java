package com.github.shikejing.experimentplatform.api;

import com.github.shikejing.experimentplatform.exceptions.AssignmentMismatchException;
import com.github.shikejing.experimentplatform.exceptions.ExperimentNotFoundException;
import com.github.shikejing.experimentplatform.exceptions.ExperimentPlatformException;
import com.github.shikejing.experimentplatform.exceptions.InvalidConfigException;
import com.github.shikejing.experimentplatform.exceptions.VariantNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 异常统一处理：响应体携带 experimentId / variantId / unitId，
 * 异常链路可定位到具体实验与变体。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidConfigException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidConfig(InvalidConfigException ex) {
        return build(HttpStatus.BAD_REQUEST, ex, ex.getExperimentId(), ex.getVariantId(), null);
    }

    @ExceptionHandler(ExperimentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ExperimentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex, ex.getExperimentId(), ex.getVariantId(), null);
    }

    @ExceptionHandler(VariantNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleVariantNotFound(VariantNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex, ex.getExperimentId(), ex.getVariantId(), null);
    }

    @ExceptionHandler(AssignmentMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleMismatch(AssignmentMismatchException ex) {
        Map<String, Object> body = base(HttpStatus.CONFLICT, ex, ex.getExperimentId(),
                ex.getVariantId());
        body.put("unitId", ex.getUnitId());
        body.put("expectedVariant", ex.getExpectedVariant());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status,
                                                       ExperimentPlatformException ex,
                                                       String experimentId, String variantId,
                                                       String unitId) {
        return ResponseEntity.status(status).body(base(status, ex, experimentId, variantId));
    }

    private Map<String, Object> base(HttpStatus status, ExperimentPlatformException ex,
                                     String experimentId, String variantId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", ex.getMessage());
        body.put("experimentId", experimentId);
        body.put("variantId", variantId);
        return body;
    }
}
