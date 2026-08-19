package com.mkt.identity.audit;

import cn.hutool.core.util.DesensitizedUtil;
import com.mkt.kernel.json.JsonUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.validation.BindingResult;
import tools.jackson.databind.JsonNode;

/** JsonUtil → sensitive-field mask (R10.6) → truncate 2000 + {@code ...(truncated)} (R10.1). */
public final class AuditSummaries {

    public static final int MAX_CHARS = 2000;
    private static final String MARKER = "...(truncated)";

    private AuditSummaries() {}

    public static String ofArgs(Object[] args) {
        List<Object> body = new ArrayList<>();
        if (args != null) {
            for (Object arg : args) {
                if (include(arg)) {
                    body.add(arg);
                }
            }
        }
        Object payload = body.isEmpty() ? Map.of() : (body.size() == 1 ? body.get(0) : body);
        return ofValue(payload);
    }

    public static String ofValue(Object value) {
        String json = JsonUtil.toJson(value == null ? Map.of() : value);
        JsonNode tree = JsonUtil.readTree(json);
        String masked = JsonUtil.toJson(mask(tree, ""));
        return truncate(masked, MAX_CHARS);
    }

    public static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        if (max <= MARKER.length()) {
            return value.substring(0, max);
        }
        return value.substring(0, max - MARKER.length()) + MARKER;
    }

    public static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    static boolean sensitiveKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("password")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("csrf")
                || lower.contains("phone")
                || lower.contains("mobile")
                || lower.contains("captcha");
    }

    private static boolean include(Object arg) {
        if (arg == null) {
            return false;
        }
        if (arg instanceof ServletRequest || arg instanceof ServletResponse) {
            return false;
        }
        return !(arg instanceof BindingResult);
    }

    private static Object mask(JsonNode node, String key) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject()) {
            Map<String, Object> out = new LinkedHashMap<>();
            node.properties().forEach(entry -> out.put(entry.getKey(), mask(entry.getValue(), entry.getKey())));
            return out;
        }
        if (node.isArray()) {
            List<Object> out = new ArrayList<>();
            node.forEach(item -> out.add(mask(item, key)));
            return out;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isNumber()) {
            return sensitiveKey(key) ? maskValue(key, node.asString()) : node.numberValue();
        }
        String text = node.asString();
        return sensitiveKey(key) ? maskValue(key, text) : text;
    }

    private static String maskValue(String key, String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        String lower = key == null ? "" : key.toLowerCase(Locale.ROOT);
        if (lower.contains("phone") || lower.contains("mobile")) {
            return DesensitizedUtil.mobilePhone(raw);
        }
        return DesensitizedUtil.password(raw);
    }
}
