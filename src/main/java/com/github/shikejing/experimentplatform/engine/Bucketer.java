package com.github.shikejing.experimentplatform.engine;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 确定性分桶器。
 * <p>
 * 基于 SHA-256 稳定哈希：对 {@code salt + "|" + key} 取摘要，取前 8 字节
 * 构造 long，对 bound 取模。不使用任何运行时随机数、不依赖对象哈希码或
 * 迭代顺序，因此同一输入在任何时刻、任何并发度、任何执行顺序或重启下都
 * 得到相同桶号。
 */
@Component
public class Bucketer {

    private static final String DELIMITER = "|";

    /**
     * 返回 [0, bound) 区间的确定性桶号。
     */
    public int bucket(String key, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        long hash = sha256FirstLong(key);
        // 取非负值后取模，保证结果落在 [0, bound)
        return (int) ((hash & Long.MAX_VALUE) % bound);
    }

    /**
     * 带盐值的分桶：不同盐值空间下桶号相互独立。
     */
    public int bucket(String salt, String key, int bound) {
        if (salt == null || salt.isEmpty()) {
            return bucket(key, bound);
        }
        return bucket(salt + DELIMITER + key, bound);
    }

    private static long sha256FirstLong(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (bytes[i] & 0xFFL);
            }
            return value;
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必备算法，缺失属于严重环境问题
            throw new IllegalStateException("SHA-256 MessageDigest not available", e);
        }
    }
}
