package com.mkt.infra.cache;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/** Closed R9.1 namespaces (design §6.2). */
public enum CacheNamespace {
    DICT("dict", Duration.ofMinutes(5), 10_000, CacheNamespaceKind.MANAGED),
    CONFIG("config", Duration.ofMinutes(5), 10_000, CacheNamespaceKind.MANAGED),
    RBAC_PERMISSION("rbac:permission", Duration.ofMinutes(5), 10_000, CacheNamespaceKind.MANAGED),
    TASK_SNAPSHOT("task:snapshot", Duration.ofHours(24), 1_000, CacheNamespaceKind.MANAGED),
    TASK_PUBLISHED_INDEX("task:published-index", Duration.ofSeconds(30), 1_000, CacheNamespaceKind.MANAGED),
    TASK_CROWD("task:crowd", Duration.ofMinutes(10), 10_000, CacheNamespaceKind.MANAGED),
    RISK_RULE("risk:rule", Duration.ofMinutes(5), 10_000, CacheNamespaceKind.MANAGED),
    IDENTITY_USER_ATTR("identity:user-attr", Duration.ofMinutes(5), 1_000, CacheNamespaceKind.MANAGED),
    IDENTITY_SESSION("identity:session", Duration.ZERO, 0, CacheNamespaceKind.SA_TOKEN),
    AD_POSITION("ad:position", Duration.ofSeconds(60), 1_000, CacheNamespaceKind.PLACEHOLDER);

    private final String id;
    private final Duration ttl;
    private final int l1Capacity;
    private final CacheNamespaceKind kind;

    CacheNamespace(String id, Duration ttl, int l1Capacity, CacheNamespaceKind kind) {
        this.id = id;
        this.ttl = ttl;
        this.l1Capacity = l1Capacity;
        this.kind = kind;
    }

    public String id() {
        return id;
    }

    public Duration ttl() {
        return ttl;
    }

    public int l1Capacity() {
        return l1Capacity;
    }

    public CacheNamespaceKind kind() {
        return kind;
    }

    public String redisKey(String bizKey) {
        return id + ":" + bizKey;
    }

    public static Optional<CacheNamespace> ofId(String id) {
        return Arrays.stream(values()).filter(ns -> ns.id.equals(id)).findFirst();
    }

    public static CacheNamespace require(String id) {
        return ofId(id).orElseThrow(() -> new IllegalArgumentException("unknown cache namespace: " + id));
    }
}
