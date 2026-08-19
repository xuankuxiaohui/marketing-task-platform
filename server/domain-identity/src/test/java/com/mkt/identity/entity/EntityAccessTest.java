package com.mkt.identity.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class EntityAccessTest {

    @Test
    void adminUserFlagsAndFields() {
        AdminUserEntity e = new AdminUserEntity();
        e.setId(1L);
        e.setUsername("a");
        e.setNickname("n");
        e.setPasswordHash("h");
        e.setStatus("ENABLED");
        e.setDeleted(0);
        e.setFailedAttempts(1);
        e.setLockedUntil(LocalDateTime.of(2026, 1, 1, 0, 0));
        e.setMustChangePassword(1);
        e.setLastLoginAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        e.setCreatedAt(LocalDateTime.of(2026, 1, 3, 0, 0));
        e.setUpdatedAt(LocalDateTime.of(2026, 1, 4, 0, 0));
        assertThat(e.getId()).isEqualTo(1L);
        assertThat(e.getUsername()).isEqualTo("a");
        assertThat(e.getNickname()).isEqualTo("n");
        assertThat(e.getPasswordHash()).isEqualTo("h");
        assertThat(e.enabled()).isTrue();
        assertThat(e.deletedFlag()).isFalse();
        assertThat(e.getFailedAttempts()).isEqualTo(1);
        assertThat(e.getLockedUntil()).isNotNull();
        assertThat(e.mustChangePasswordFlag()).isTrue();
        assertThat(e.getLastLoginAt()).isNotNull();
        assertThat(e.getCreatedAt()).isNotNull();
        assertThat(e.getUpdatedAt()).isNotNull();
        e.setDeleted(1);
        e.setStatus("DISABLED");
        e.setMustChangePassword(0);
        assertThat(e.deletedFlag()).isTrue();
        assertThat(e.enabled()).isFalse();
        assertThat(e.mustChangePasswordFlag()).isFalse();
    }

    @Test
    void portalUserAndConfigFields() {
        PortalUserEntity p = new PortalUserEntity();
        p.setId(2L);
        p.setUsername("u");
        p.setNickname("n");
        p.setPasswordHash("h");
        p.setProvince("GD");
        p.setUserLevel("1");
        p.setUserRole("r");
        p.setTags("[]");
        p.setOrgId("o");
        p.setStatus("ENABLED");
        p.setDeleted(0);
        p.setFailedAttempts(0);
        p.setLockedUntil(null);
        p.setMustChangePassword(0);
        p.setRegisteredAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        p.setLastLoginAt(null);
        p.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        p.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(p.getProvince()).isEqualTo("GD");
        assertThat(p.getUserLevel()).isEqualTo("1");
        assertThat(p.getUserRole()).isEqualTo("r");
        assertThat(p.getTags()).isEqualTo("[]");
        assertThat(p.getOrgId()).isEqualTo("o");
        assertThat(p.getRegisteredAt()).isNotNull();
        assertThat(p.enabled()).isTrue();
        assertThat(p.deletedFlag()).isFalse();
        p.setDeleted(1);
        p.setStatus("DISABLED");
        assertThat(p.deletedFlag()).isTrue();
        assertThat(p.enabled()).isFalse();
        InternalAppEntity app = new InternalAppEntity();
        app.setId(4L);
        app.setAppId("appAb12cd34ef56");
        app.setAppName("partner");
        app.setSecretCipher("cipher");
        app.setPrevSecretCipher("prev");
        app.setPrevExpireAt(LocalDateTime.of(2026, 1, 2, 0, 0));
        app.setStatus("ENABLED");
        app.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        app.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(app.getAppId()).isEqualTo("appAb12cd34ef56");
        assertThat(app.getSecretCipher()).isEqualTo("cipher");
        assertThat(app.enabled()).isTrue();
        app.setStatus("DISABLED");
        assertThat(app.enabled()).isFalse();
        SysConfigEntity c = new SysConfigEntity();
        c.setId(3L);
        c.setConfigKey("k");
        c.setConfigGroup("g");
        c.setConfigValue("v");
        c.setValueType("NUMBER");
        c.setMasked(0);
        c.setStatus("ENABLED");
        assertThat(c.getId()).isEqualTo(3L);
        assertThat(c.getConfigKey()).isEqualTo("k");
        assertThat(c.getConfigGroup()).isEqualTo("g");
        assertThat(c.getConfigValue()).isEqualTo("v");
        assertThat(c.getValueType()).isEqualTo("NUMBER");
        assertThat(c.getMasked()).isZero();
        assertThat(c.getStatus()).isEqualTo("ENABLED");
        c.setRemark("r");
        c.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        c.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(c.getRemark()).isEqualTo("r");
        assertThat(c.maskedFlag()).isFalse();
        assertThat(c.enabled()).isTrue();
        DictTypeEntity type = new DictTypeEntity();
        type.setId(5L);
        type.setCode("province");
        type.setName("省份");
        type.setStatus("ENABLED");
        type.setRemark(null);
        type.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        type.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(type.enabled()).isTrue();
        type.setStatus("DISABLED");
        assertThat(type.enabled()).isFalse();
        DictEntryEntity entry = new DictEntryEntity();
        entry.setId(6L);
        entry.setTypeId(5L);
        entry.setLabel("广东");
        entry.setValue("GD");
        entry.setSort(1);
        entry.setStatus("ENABLED");
        entry.setRemark(null);
        assertThat(entry.getValue()).isEqualTo("GD");
        assertThat(entry.enabled()).isTrue();
        entry.setStatus("DISABLED");
        assertThat(entry.enabled()).isFalse();
    }

    @Test
    void roleAndPermissionFlags() {
        RoleEntity role = new RoleEntity();
        role.setId(1L);
        role.setCode("super-admin");
        role.setName("超管");
        role.setDescription("d");
        role.setStatus("ENABLED");
        role.setBuiltIn(1);
        role.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        role.setUpdatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(role.builtInFlag()).isTrue();
        assertThat(role.enabled()).isTrue();
        role.setBuiltIn(0);
        role.setStatus("DISABLED");
        assertThat(role.builtInFlag()).isFalse();
        assertThat(role.enabled()).isFalse();
        PermissionEntity p = new PermissionEntity();
        p.setId(2L);
        p.setParentId(0L);
        p.setType("MENU");
        p.setName("角色");
        p.setRoute("/system/roles");
        p.setComponent("system/role/index");
        p.setIcon("role");
        p.setSort(1);
        p.setStatus("ENABLED");
        p.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        assertThat(p.enabled()).isTrue();
        assertThat(p.getRoute()).isEqualTo("/system/roles");
        p.setStatus("DISABLED");
        assertThat(p.enabled()).isFalse();
    }
}
