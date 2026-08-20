package com.mkt.identity.domain;

import com.mkt.kernel.json.JsonUtil;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;

/** sys_config.value_type (R8.1). */
public final class ConfigValueTypes {

    public static final String STRING = "STRING";
    public static final String NUMBER = "NUMBER";
    public static final String BOOL = "BOOL";
    public static final String JSON = "JSON";

    private static final Set<String> ALL = Set.of(STRING, NUMBER, BOOL, JSON);

    private ConfigValueTypes() {}

    public static boolean valid(String type) {
        return type != null && ALL.contains(type);
    }

    public static boolean matches(String type, String raw) {
        if (raw == null) {
            return false;
        }
        if (STRING.equals(type)) {
            return true;
        }
        if (NUMBER.equals(type)) {
            try {
                new BigDecimal(raw.trim());
                return true;
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        if (BOOL.equals(type)) {
            String folded = raw.trim().toLowerCase(Locale.ROOT);
            return "true".equals(folded) || "false".equals(folded);
        }
        if (JSON.equals(type)) {
            try {
                JsonUtil.readTree(raw);
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        }
        return false;
    }
}
