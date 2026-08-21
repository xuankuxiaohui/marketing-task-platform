package com.mkt.reward.domain;

import java.util.Map;

/** typeParams keys (design §3.4.1). */
public final class TypeParams {

    public static final String POINTS = "points";
    public static final String FACE_FEN = "faceFen";
    public static final String ADAPTER_CODE = "adapterCode";

    private TypeParams() {}

    public static Integer positiveInt(Map<String, Object> params, String key) {
        if (params == null || key == null) {
            return null;
        }
        Object raw = params.get(key);
        Integer parsed = toInt(raw);
        if (parsed == null || parsed <= 0) {
            return null;
        }
        return parsed;
    }

    public static String text(Map<String, Object> params, String key) {
        if (params == null || key == null) {
            return null;
        }
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer toInt(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Integer value) {
            return value;
        }
        if (raw instanceof Long value) {
            if (value > Integer.MAX_VALUE || value < Integer.MIN_VALUE) {
                return null;
            }
            return value.intValue();
        }
        if (raw instanceof Number value) {
            return value.intValue();
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
