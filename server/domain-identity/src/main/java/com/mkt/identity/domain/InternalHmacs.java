package com.mkt.identity.domain;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 stringToSign (R15.2 / design §4.8). */
public final class InternalHmacs {

    private InternalHmacs() {}

    public static String stringToSign(String method, String path, String timestamp, String nonce, byte[] body) {
        String upper = method == null ? "" : method.toUpperCase(Locale.ROOT);
        String resolvedPath = path == null ? "" : path;
        String ts = timestamp == null ? "" : timestamp;
        String n = nonce == null ? "" : nonce;
        byte[] raw = body == null ? new byte[0] : body;
        return upper + "\n" + resolvedPath + "\n" + ts + "\n" + n + "\n" + sha256Hex(raw);
    }

    public static String signHex(String secret, String stringToSign) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(utf8(secret), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(utf8(stringToSign)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 unavailable", ex);
        }
    }

    public static boolean equalsConstantTime(String expectedHex, String presentedHex) {
        byte[] expected = decodeHex(expectedHex);
        byte[] presented = decodeHex(presentedHex);
        if (expected == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(expected, presented);
    }

    public static byte[] decodeHex(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static String signedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            uri = uri.substring(context.length());
        }
        if (uri == null || uri.isEmpty()) {
            uri = "/";
        }
        String query = request.getQueryString();
        if (query == null || query.isEmpty()) {
            return uri;
        }
        return uri + "?" + query;
    }

    public static byte[] utf8(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
