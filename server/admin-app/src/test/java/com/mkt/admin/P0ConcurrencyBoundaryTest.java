package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Task 41/44: P0 C-1~C-8 / C-12 live; C-9 delivered with domain-signin; C-10/C-11 still later.
 */
class P0ConcurrencyBoundaryTest {

    @Test
    void p1ConcurrencyModulesMatchTaskBoundary() {
        Path server = serverRoot();
        assertThat(server.resolve("domain-signin")).exists();
        assertThat(server.resolve("domain-activity")).doesNotExist();
        assertThat(server.resolve("domain-ad")).doesNotExist();
        assertThat(Files.isRegularFile(server.resolve("domain-task/src/test/java/com/mkt/task/InstanceUniquenessIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-task/src/test/java/com/mkt/task/StepAdvanceExactlyOnceIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(server.resolve("domain-task/src/test/java/com/mkt/task/ProgressDedupIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-reward/src/test/java/com/mkt/reward/StockNoOversellIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-reward/src/test/java/com/mkt/reward/ClaimLimitConcurrentIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-reward/src/test/java/com/mkt/reward/GrantExactlyOnceIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-reward/src/test/java/com/mkt/reward/PrizeClaimExactlyOnceIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-reward/src/test/java/com/mkt/reward/PointsNonNegativeIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-risk/src/test/java/com/mkt/risk/ListConcurrentDecisionIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(server.resolve("domain-signin/src/test/java/com/mkt/signin/SigninUniqueIT.java")))
                .isTrue();
        assertThat(Files.isRegularFile(
                        server.resolve("domain-activity/src/test/java/com/mkt/activity/ActivityQuotaIT.java")))
                .isFalse();
        assertThat(Files.isRegularFile(server.resolve("domain-ad/src/test/java/com/mkt/ad/AdFrequencyIT.java")))
                .isFalse();
    }

    private static Path serverRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (cwd.getFileName().toString().equals("admin-app")) {
            return cwd.getParent();
        }
        Path nested = cwd.resolve("server");
        if (Files.isDirectory(nested)) {
            return nested;
        }
        return cwd;
    }
}
