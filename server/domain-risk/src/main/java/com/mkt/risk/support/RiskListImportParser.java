package com.mkt.risk.support;

import com.mkt.risk.domain.RiskDimension;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One list value per line (R25.3). Write and lookup use the same canonical form. */
public final class RiskListImportParser {

    public record ParseResult(List<String> values, int invalid) {
    }

    private RiskListImportParser() {
    }

    public static ParseResult parse(RiskDimension dimension, String content) {
        if (content == null || content.isBlank()) {
            return new ParseResult(List.of(), 0);
        }
        List<String> values = new ArrayList<>();
        int invalid = 0;
        String[] lines = content.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw == null ? "" : raw.trim();
            if (line.isEmpty()) {
                continue;
            }
            String normalized = normalizeOrNull(dimension, line);
            if (normalized != null) {
                values.add(normalized);
            } else {
                invalid++;
            }
        }
        return new ParseResult(List.copyOf(values), invalid);
    }

    public static boolean valid(RiskDimension dimension, String value) {
        return normalizeOrNull(dimension, value) != null;
    }

    public static String normalizeOrNull(RiskDimension dimension, String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            return null;
        }
        if (!hasNoControlChars(value)) {
            return null;
        }
        return switch (dimension) {
            case USER -> normalizeUserId(value);
            case IP -> normalizeIp(value);
            case DEVICE -> value.toLowerCase(Locale.ROOT);
        };
    }

    private static String normalizeUserId(String value) {
        if (value.length() > 19) {
            return null;
        }
        char first = value.charAt(0);
        if (first < '1' || first > '9') {
            return null;
        }
        for (int i = 1; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            long id = Long.parseLong(value);
            if (id <= 0L) {
                return null;
            }
            return Long.toString(id);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeIp(String value) {
        if (value.contains(".")) {
            if (value.contains(":")) {
                return normalizeIpv6Literal(value);
            }
            return normalizeIpv4(value);
        }
        return normalizeIpv6Literal(value);
    }

    private static String normalizeIpv4(String value) {
        if (!isStrictIpv4(value)) {
            return null;
        }
        try {
            InetAddress addr = InetAddress.getByName(value);
            if (!(addr instanceof Inet4Address)) {
                return null;
            }
            return addr.getHostAddress();
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static String normalizeIpv6Literal(String value) {
        if (value.length() > 45 || colonCount(value) < 2) {
            return null;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ':' || c == '.') {
                continue;
            }
            if (Character.digit(c, 16) < 0) {
                return null;
            }
        }
        try {
            InetAddress addr = InetAddress.getByName(value);
            if (addr instanceof Inet4Address) {
                return addr.getHostAddress();
            }
            if (!(addr instanceof Inet6Address inet6)) {
                return null;
            }
            byte[] bytes = inet6.getAddress();
            if (isIpv4Mapped(bytes)) {
                return (bytes[12] & 0xff) + "." + (bytes[13] & 0xff) + "." + (bytes[14] & 0xff) + "."
                        + (bytes[15] & 0xff);
            }
            String host = inet6.getHostAddress();
            int zone = host.indexOf('%');
            if (zone >= 0) {
                host = host.substring(0, zone);
            }
            return host.toLowerCase(Locale.ROOT);
        } catch (UnknownHostException ex) {
            return null;
        }
    }

    private static boolean isIpv4Mapped(byte[] bytes) {
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return bytes[10] == (byte) 0xff && bytes[11] == (byte) 0xff;
    }

    private static int colonCount(String value) {
        int n = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == ':') {
                n++;
            }
        }
        return n;
    }

    private static boolean isStrictIpv4(String value) {
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') {
                    return false;
                }
            }
            if (part.length() > 1 && part.charAt(0) == '0') {
                return false;
            }
            if (Integer.parseInt(part) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasNoControlChars(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
