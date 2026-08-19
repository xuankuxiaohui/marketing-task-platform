package com.mkt.kernel;

import java.util.Map;
import java.util.Set;

/**
 * Error-code domain/scene skeleton (design §3.9). Reason spellings live in §4.
 */
public final class ErrorSegments {

    public static final Set<String> COMMON_REASONS = Set.of(
            "param-invalid", "permission-denied", "rate-limited", "not-found", "server-error");

    private static final Map<String, Set<String>> SCENES_BY_DOMAIN = Map.ofEntries(
            Map.entry(
                    "auth",
                    Set.of(
                            "login",
                            "captcha",
                            "session",
                            "profile",
                            "username",
                            "account",
                            "password",
                            "register",
                            "role",
                            "user")),
            Map.entry("dict", Set.of("dict", "entry")),
            Map.entry("config", Set.of("config", "value")),
            Map.entry("cache", Set.of("cache", "namespace")),
            Map.entry(
                    "task",
                    Set.of(
                            "definition",
                            "publish",
                            "expression",
                            "mutex",
                            "crowd",
                            "claim",
                            "step",
                            "progress",
                            "instance")),
            Map.entry(
                    "reward",
                    Set.of("prize", "stock", "grant", "claim", "fulfill", "category", "recon")),
            Map.entry("points", Set.of("account", "transaction")),
            Map.entry("risk", Set.of("blocked", "list", "rule", "case")),
            Map.entry("track", Set.of("batch", "metadata", "query")),
            Map.entry("internal", Set.of("sign", "app", "nonce", "timestamp")),
            Map.entry("ad", Set.of("position", "material")),
            Map.entry("signin", Set.of("signin")),
            Map.entry("activity", Set.of("activity")));

    private ErrorSegments() {
    }

    public static boolean isKnownDomain(String domain) {
        return "common".equals(domain) || SCENES_BY_DOMAIN.containsKey(domain);
    }

    public static boolean isKnownScene(String domain, String scene) {
        Set<String> scenes = SCENES_BY_DOMAIN.get(domain);
        return scenes != null && scenes.contains(scene);
    }

    public static boolean isClosedCommonReason(String reason) {
        return COMMON_REASONS.contains(reason);
    }
}
