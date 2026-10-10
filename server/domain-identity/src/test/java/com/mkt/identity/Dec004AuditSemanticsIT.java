package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.identity.command.DictTypeCreateCommand;
import com.mkt.identity.command.DictTypeUpdateCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * DEC-004 audit semantics: illegal param / unique conflict / forced rollback must not leave SUCCESS.
 */
@Testcontainers
class Dec004AuditSemanticsIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void illegalUniqueAndRollbackLeaveNoSuccessAudit() {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            UserContext.set(new UserPrincipal(1L, "admin", "admin"));
            Integer baseline = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_log WHERE result = 'SUCCESS'", Integer.class);

            assertThatThrownBy(() -> env.dictApp.createType(new DictTypeCreateCommand("  ", "空编码", null)))
                    .isInstanceOf(BusinessException.class);
            env.dictApp.createType(new DictTypeCreateCommand("dec004_dup", "首次", null));
            assertThatThrownBy(() -> env.dictApp.createType(new DictTypeCreateCommand("dec004_dup", "重复", null)))
                    .isInstanceOf(BusinessException.class);

            Long typeId = env.jdbc.queryForObject(
                    "SELECT id FROM sys_dict_type WHERE code = 'dec004_dup'", Long.class);
            assertThatThrownBy(() -> env.dictApp.updateType(
                            typeId, new DictTypeUpdateCommand("改名", "NOT_A_STATUS", null)))
                    .isInstanceOf(BusinessException.class);

            env.tx.execute(status -> {
                env.dictApp.createType(new DictTypeCreateCommand("dec004_rb", "应回滚", null));
                status.setRollbackOnly();
                return null;
            });

            env.drain.awaitDrain(Duration.ofSeconds(5));
            Integer success = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_audit_log WHERE result = 'SUCCESS'", Integer.class);
            // one SUCCESS for the first create of dec004_dup only
            assertThat(success).isEqualTo(baseline + 1);
            Integer rolled = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_dict_type WHERE code = 'dec004_rb'", Integer.class);
            assertThat(rolled).isZero();
        } finally {
            UserContext.clear();
        }
    }
}
