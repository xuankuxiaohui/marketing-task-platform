package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import com.mkt.identity.command.PortalUserProfileCommand;
import com.mkt.identity.it.IdentityITSupport;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * R5.1: profile overwrite is visible on the next {@code UserAttributePort.attributes} read.
 * Task-list visibility (filter expressions) is asserted in task 28.
 */
@Testcontainers
class ProfileEffectVisibilityIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void profileOverwriteReplacesTagsOnNextAttributeRead() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            env.jdbc.update(
                    """
                    INSERT INTO sys_portal_user
                      (id, username, nickname, password_hash, province, user_level, tags, status, deleted)
                    VALUES (30, 'vis_user', '可见', ?, 'GD', '1', '["old"]', 'ENABLED', 0)
                    """,
                    new BCryptPasswordEncoder(12).encode("pass1234"));
            UserAttributes before = env.userAttributes.attributes(30L);
            assertThat(before.province()).isEqualTo("GD");
            assertThat(before.tags()).containsExactly("old");
            assertThat(before.accountStatus()).isEqualTo(AccountStatus.ACTIVE);

            env.portalUserApp.updateProfile(
                    30L, new PortalUserProfileCommand("BJ", "2", "vip", List.of("hot"), "88"));
            UserAttributes after = env.userAttributes.attributes(30L);
            assertThat(after.province()).isEqualTo("BJ");
            assertThat(after.userLevel()).isEqualTo(2);
            assertThat(after.userRole()).isEqualTo("vip");
            assertThat(after.tags()).containsExactly("hot");
            assertThat(after.orgId()).isEqualTo(88L);

            env.portalUserApp.updateProfile(
                    30L, new PortalUserProfileCommand("SH", "3", "vip", List.of("only"), "88"));
            UserAttributes replaced = env.userAttributes.attributes(30L);
            assertThat(replaced.tags()).containsExactly("only");
            assertThat(replaced.province()).isEqualTo("SH");
        }
    }
}
