package com.github.shikejing.experiment.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 确定性哈希工具：基于 SHA-256，同一输入在任何进程、任何时刻、
 * 任何并发度下都得到同一结果，不依赖 hashCode 或迭代顺序。
 */
public final class Hasher {

    /** 槽位空间大小：万分比，[0, 10000)。 */
    public static final int SLOT_COUNT = 10_000;

    private Hasher() {
    }

    /**
     * 将输入字符串确定性地映射到 [0, {@link #SLOT_COUNT}) 的槽位。
     */
    public static int slot(String input) {
        byte[] digest = sha256(input.getBytes(StandardCharsets.UTF_8));
        // 取前 8 字节构造无符号 long，再取模，结果与平台无关
        long h = 0;
        for (int i = 0; i < 8; i++) {
            h = (h << 8) | (digest[i] & 0xFFL);
        }
        // h 可能为负，使用无符号取模语义
        return (int) Long.remainderUnsigned(h, SLOT_COUNT);
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
