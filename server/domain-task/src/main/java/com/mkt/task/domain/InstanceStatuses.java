package com.mkt.task.domain;

import java.util.Locale;
import java.util.Set;

public final class InstanceStatuses {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String ABANDONED = "ABANDONED";
    public static final String EXPIRED = "EXPIRED";
    public static final String NOT_STARTED = "NOT_STARTED";
    public static final String OFFLINE = "OFFLINE";

    private static final Set<String> TERMINAL = Set.of(COMPLETED, ABANDONED, EXPIRED);

    private InstanceStatuses() {}

    public static boolean terminal(String value) {
        return value != null && TERMINAL.contains(value);
    }

    /**
     * C-end 我的任务 tab / query → instance status (R13.3 / design-api mine).
     * Accepts the closed enum, common aliases, and Vant tab indexes 0–3.
     */
    public static String mineFilter(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!value.isEmpty() && value.chars().allMatch(Character::isDigit)) {
            return switch (Integer.parseInt(value)) {
                case 0 -> IN_PROGRESS;
                case 1 -> COMPLETED;
                case 2 -> ABANDONED;
                case 3 -> EXPIRED;
                default -> value;
            };
        }
        return switch (value.toUpperCase(Locale.ROOT)) {
            case IN_PROGRESS -> IN_PROGRESS;
            case COMPLETED, "SUCCESS", "DONE", "FINISHED", "COMPLETE" -> COMPLETED;
            case ABANDONED -> ABANDONED;
            case EXPIRED -> EXPIRED;
            default -> value;
        };
    }

    public static boolean tabIndexLeak(String value) {
        return value != null && !value.isEmpty() && value.chars().allMatch(Character::isDigit);
    }
}
