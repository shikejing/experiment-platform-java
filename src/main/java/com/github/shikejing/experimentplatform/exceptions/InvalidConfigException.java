package com.github.shikejing.experimentplatform.exceptions;

/**
 * 配置校验失败：发布被拒绝，上一个可用版本继续服务。
 */
public class InvalidConfigException extends ExperimentPlatformException {
    public InvalidConfigException(String message, String experimentId, String variantId) {
        super(message, experimentId, variantId);
    }
}
