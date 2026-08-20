package com.mkt.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.identity.command.AdminUserCreateCommand;
import com.mkt.identity.it.IdentityITSupport;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.kernel.BusinessException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R3.1: concurrent mixed-case username creates exactly one row; deleted names stay reserved. */
@Testcontainers
class AdminUsernameUniqueIT {

    @Container
    static final MySQLContainer<?> MYSQL = IdentityITSupport.mysql();

    @Test
    void concurrentMixedCaseCreatesExactlyOneThenDeletedNameStaysTaken() throws Exception {
        Instant now = Instant.parse("2026-08-19T12:00:00Z");
        try (IdentityITSupport env = IdentityITSupport.start(MYSQL, now)) {
            String[] spellings = {"Same_User", "same_user", "SAME_USER"};
            AtomicInteger successes = new AtomicInteger();
            AtomicInteger duplicates = new AtomicInteger();
            int threads = 64;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threads);
            for (int i = 0; i < threads; i++) {
                String username = spellings[i % spellings.length];
                pool.submit(() -> {
                    try {
                        start.await();
                        env.adminUserApp.create(
                                new AdminUserCreateCommand(username, "同名", "Abcdef12!x", List.of()));
                        successes.incrementAndGet();
                    } catch (BusinessException ex) {
                        if (ex.errorCode() == AuthErrorCodes.USERNAME_DUPLICATE) {
                            duplicates.incrementAndGet();
                        } else {
                            throw ex;
                        }
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
            pool.shutdownNow();
            assertThat(successes.get()).isEqualTo(1);
            assertThat(duplicates.get()).isEqualTo(threads - 1);
            Long remaining = env.jdbc.queryForObject(
                    "SELECT COUNT(*) FROM sys_admin_user WHERE username = 'same_user'", Long.class);
            assertThat(remaining).isEqualTo(1L);

            Long id = env.jdbc.queryForObject(
                    "SELECT id FROM sys_admin_user WHERE username = 'same_user'", Long.class);
            env.adminUserApp.delete(id);
            assertThatThrownBy(() -> env.adminUserApp.create(
                            new AdminUserCreateCommand("SAME_USER", "复用", "Abcdef12!x", List.of())))
                    .isInstanceOf(BusinessException.class)
                    .extracting(ex -> ((BusinessException) ex).errorCode())
                    .isEqualTo(AuthErrorCodes.USERNAME_DUPLICATE);
        }
    }
}
