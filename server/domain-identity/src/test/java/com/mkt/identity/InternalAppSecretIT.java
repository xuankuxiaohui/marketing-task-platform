package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.identity.command.InternalAppCreateCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.identity.response.InternalAppCreatedResponse;
import com.mkt.identity.response.InternalAppRotateResponse;
import com.mkt.identity.response.InternalAppView;
import com.mkt.kernel.PageQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R15.2: AES-256-GCM persist + 24h dual-live after rotate. Requires Docker; leave for CI. */
@Testcontainers
class InternalAppSecretIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void secretIsCipheredAndRotateKeepsPreviousFor24Hours() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            InternalAppCreatedResponse created = env.internalApps.create(new InternalAppCreateCommand("合作方A"));
            assertThat(created.secret()).hasSize(43).matches("[A-Za-z0-9]{43}");
            String stored = env.jdbc.queryForObject(
                    "SELECT secret_cipher FROM sys_internal_app WHERE id = ?", String.class, created.id());
            assertThat(stored).isNotEqualTo(created.secret());
            assertThat(env.secretCipher.decrypt(stored)).isEqualTo(created.secret());

            List<InternalAppView> listed = env.internalApps.page(PageQuery.of(1, 20)).records();
            assertThat(listed).hasSize(1);
            assertThat(listed.get(0).appId()).isEqualTo(created.appId());

            InternalAppRotateResponse rotated = env.internalApps.rotate(created.id());
            assertThat(rotated.secret()).isNotEqualTo(created.secret());
            assertThat(rotated.prevExpireAt()).isEqualTo(now.plus(Duration.ofHours(24)));

            var row = env.jdbc.queryForMap("SELECT secret_cipher, prev_secret_cipher, prev_expire_at FROM sys_internal_app WHERE id = ?", created.id());
            String currentCipher = (String) row.get("secret_cipher");
            String prevCipher = (String) row.get("prev_secret_cipher");
            assertThat(env.secretCipher.decrypt(currentCipher)).isEqualTo(rotated.secret());
            assertThat(env.secretCipher.decrypt(prevCipher)).isEqualTo(created.secret());

            com.mkt.identity.entity.InternalAppEntity entity = new com.mkt.identity.entity.InternalAppEntity();
            entity.setSecretCipher(currentCipher);
            entity.setPrevSecretCipher(prevCipher);
            entity.setPrevExpireAt(com.mkt.identity.convert.IdentityTime.toUtc(rotated.prevExpireAt()));
            assertThat(env.secretCipher.decryptActive(entity, now.plus(Duration.ofHours(23))))
                    .containsExactly(rotated.secret(), created.secret());
            env.clock.setInstant(now.plus(Duration.ofHours(24)));
            assertThat(env.secretCipher.decryptActive(entity, env.clock.instant()))
                    .containsExactly(rotated.secret());

            env.internalApps.disable(created.id());
            String status = env.jdbc.queryForObject(
                    "SELECT status FROM sys_internal_app WHERE id = ?", String.class, created.id());
            assertThat(status).isEqualTo("DISABLED");
            env.internalApps.enable(created.id());
            assertThat(env.jdbc.queryForObject(
                            "SELECT status FROM sys_internal_app WHERE id = ?", String.class, created.id()))
                    .isEqualTo("ENABLED");
        }
    }
}
