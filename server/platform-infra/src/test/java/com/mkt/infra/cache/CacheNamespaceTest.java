package com.mkt.infra.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class CacheNamespaceTest {

    @Test
    void closedListMatchesR91() {
        Set<String> ids = Arrays.stream(CacheNamespace.values()).map(CacheNamespace::id).collect(Collectors.toSet());
        assertThat(ids)
                .containsExactlyInAnyOrder(
                        "dict",
                        "config",
                        "rbac:permission",
                        "task:snapshot",
                        "task:published-index",
                        "task:crowd",
                        "risk:rule",
                        "identity:user-attr",
                        "identity:session",
                        "ad:position");
        assertThat(CacheNamespace.values()).hasSize(10);
        assertThat(CacheNamespace.IDENTITY_SESSION.kind()).isEqualTo(CacheNamespaceKind.SA_TOKEN);
        assertThat(CacheNamespace.AD_POSITION.kind()).isEqualTo(CacheNamespaceKind.MANAGED);
        assertThat(CacheNamespace.require("identity:user-attr")).isEqualTo(CacheNamespace.IDENTITY_USER_ATTR);
    }
}
