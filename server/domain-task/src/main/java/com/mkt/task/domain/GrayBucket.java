package com.mkt.task.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** md5(userId + ":" + taskId) first 8 bytes big-endian remainderUnsigned 100 (design §5.3). */
public final class GrayBucket {

    private GrayBucket() {}

    public static int of(long userId, long taskId) {
        byte[] digest = md5(userId + ":" + taskId);
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (digest[i] & 0xffL);
        }
        return (int) Long.remainderUnsigned(value, 100L);
    }

    private static byte[] md5(String source) {
        try {
            return MessageDigest.getInstance("MD5").digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 not available", ex);
        }
    }
}
