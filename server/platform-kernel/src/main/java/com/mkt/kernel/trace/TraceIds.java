package com.mkt.kernel.trace;

import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** X-Trace-Id / MDC / Result.traceId share this value (design §6.6). */
public final class TraceIds {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private static final Pattern LEGAL = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private TraceIds() {
    }

    public static String current() {
        String value = MDC.get(MDC_KEY);
        return value == null || value.isBlank() ? "" : value;
    }

    public static String resolve(String incoming) {
        if (incoming != null && LEGAL.matcher(incoming.trim()).matches()) {
            return incoming.trim();
        }
        return generate();
    }

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static void put(String traceId) {
        MDC.put(MDC_KEY, traceId);
    }

    public static void clear() {
        MDC.remove(MDC_KEY);
    }
}
