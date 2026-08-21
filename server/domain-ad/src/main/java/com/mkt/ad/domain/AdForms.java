package com.mkt.ad.domain;

import java.util.Locale;
import java.util.Set;

public final class AdForms {

    public static final String CAROUSEL = "CAROUSEL";
    public static final String IMAGE = "IMAGE";
    public static final String SPLASH = "SPLASH";
    public static final String POPUP = "POPUP";
    public static final String FLOAT = "FLOAT";

    private static final Set<String> ALL = Set.of(CAROUSEL, IMAGE, SPLASH, POPUP, FLOAT);

    private AdForms() {}

    public static boolean valid(String value) {
        return value != null && ALL.contains(value);
    }

    public static String require(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public static String trackForm(String form) {
        String normalized = require(form);
        if (normalized == null) {
            return "image";
        }
        return switch (normalized) {
            case CAROUSEL -> "carousel";
            case SPLASH -> "splash";
            case POPUP -> "popup";
            case FLOAT -> "float";
            default -> "image";
        };
    }

    public static boolean singleSlot(String form) {
        return !CAROUSEL.equals(require(form));
    }
}
