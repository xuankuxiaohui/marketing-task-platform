package com.mkt.ad.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** md5(userId + ":" + positionId) first 8 bytes remainderUnsigned 100 (design §5.3). */
public final class AdGrayBuckets {

    private AdGrayBuckets() {}

    public static int of(long userId, long positionId) {
        byte[] digest = md5(userId + ":" + positionId);
        long value = 0L;
        for (int i = 0; i < 8; i++) {
            value = (value << 8) | (digest[i] & 0xffL);
        }
        return (int) Long.remainderUnsigned(value, 100L);
    }

    public static boolean hit(String grayType, Integer ratio, long userId, long positionId) {
        if (grayType == null || AdGrayTypes.NONE.equals(grayType)) {
            return true;
        }
        if (!AdGrayTypes.RATIO.equals(grayType) || ratio == null) {
            return false;
        }
        return of(userId, positionId) < ratio;
    }

    private static byte[] md5(String source) {
        try {
            return MessageDigest.getInstance("MD5").digest(source.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("MD5 not available", ex);
        }
    }
}
