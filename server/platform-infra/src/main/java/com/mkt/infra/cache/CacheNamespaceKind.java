package com.mkt.infra.cache;

/** How PlatformCache treats a registered namespace (design §6.2). */
public enum CacheNamespaceKind {
    MANAGED,
    SA_TOKEN,
    PLACEHOLDER
}
