package com.mkt.task.domain;

import java.util.regex.Pattern;

/** Task / mutex / crowd code: 4–64 {@code [a-z0-9_-]} (R11.13). */
public final class TaskCodes {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9_-]{4,64}$");

    private TaskCodes() {}

    public static boolean valid(String code) {
        return code != null && PATTERN.matcher(code).matches();
    }
}
