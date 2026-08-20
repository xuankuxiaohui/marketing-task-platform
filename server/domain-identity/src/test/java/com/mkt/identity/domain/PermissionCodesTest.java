package com.mkt.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionCodesTest {

    @Test
    void acceptsAppendixBShapeAndRejectsOthers() {
        assertThat(PermissionCodes.isOperationCode("identity:role:assign-permission")).isTrue();
        assertThat(PermissionCodes.isOperationCode("system:cache:evict")).isTrue();
        for (String code : AdminPermissionCatalog.P0) {
            assertThat(PermissionCodes.isOperationCode(code)).as(code).isTrue();
        }
        assertThat(PermissionCodes.isOperationCode("IDENTITY:role:query")).isFalse();
        assertThat(PermissionCodes.isOperationCode("identity:role")).isFalse();
        assertThat(PermissionCodes.isOperationCode("")).isFalse();
        assertThat(PermissionCodes.isOperationCode(null)).isFalse();
    }

    @Test
    void roleCodesNormalize() {
        assertThat(RoleCodes.normalizeOrNull("Ops_1")).isEqualTo("ops_1");
        assertThat(RoleCodes.normalizeOrNull("ab")).isNull();
        assertThat(RoleCodes.normalizeOrNull("super-admin")).isEqualTo(RoleCodes.SUPER_ADMIN);
    }
}
