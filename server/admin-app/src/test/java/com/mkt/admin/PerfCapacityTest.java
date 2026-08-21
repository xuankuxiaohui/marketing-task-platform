package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Task 49: k6 NFR 1–8 full gate + capacity seed + slow-query review; 5m soak not in PR CI. */
class PerfCapacityTest {

    @Test
    void adScriptPinsPerf6Threshold() throws IOException {
        String ad = Files.readString(repoRoot().resolve("perf/ad.js"));
        assertThat(ad).contains("p(95)<=100");
        assertThat(ad).contains("300");
        assertThat(ad).contains("/api/common/ad/positions/");
        assertThat(ad).contains("DURATION");
        assertThat(ad).contains("rate==0");
    }

    @Test
    void fullRunnerCoversNfr1To8AndRefusesGithubActionsSoak() throws IOException {
        String runner = Files.readString(repoRoot().resolve("perf/run-full.sh"));
        assertThat(runner).contains("list.js");
        assertThat(runner).contains("advance.js");
        assertThat(runner).contains("complete.js");
        assertThat(runner).contains("risk-delta.js");
        assertThat(runner).contains("track.js");
        assertThat(runner).contains("admin-list.js");
        assertThat(runner).contains("ad.js");
        assertThat(runner).contains("SEED_SCALE:-p1");
        assertThat(runner).contains("DURATION:-5m");
        assertThat(runner).contains("capacity_check.py");
        assertThat(runner).contains("explain_hot.py");
        assertThat(runner).contains("GITHUB_ACTIONS");
        assertThat(runner).contains("ALLOW_K6_SOAK");
        assertThat(runner).contains("INTERNAL_URL=http://portal-app-1:8081");
        assertThat(runner).doesNotContain("location /internal");
    }

    @Test
    void p1SeedMatchesOneMillionUsersAndFiveMillionEvents() throws IOException {
        String seed = Files.readString(repoRoot().resolve("perf/seed/seed.py"));
        assertThat(seed).contains("p1");
        assertThat(seed).contains("1_000_000");
        assertThat(seed).contains("5_000_000");
        assertThat(seed).contains("3000");
        assertThat(seed).contains("bulk_users");
        assertThat(seed).contains("bulk_events");
        assertThat(seed).contains("ensure_event_partitions");
        assertThat(seed).contains("home_banner");
        assertThat(seed).contains("adCodes");
        String check = Files.readString(repoRoot().resolve("perf/seed/capacity_check.py"));
        assertThat(check).contains("1_000_000");
        assertThat(check).contains("500_000");
        assertThat(check).contains("5_000_000");
        assertThat(check).contains("3000");
    }

    @Test
    void slowQueryReviewAndExplainExist() throws IOException {
        Path review = repoRoot().resolve("perf/slow-query-review.md");
        Path explain = repoRoot().resolve("perf/seed/explain.sql");
        assertThat(review).exists();
        assertThat(explain).exists();
        String text = Files.readString(review);
        assertThat(text).contains("idx_status_sort");
        assertThat(text).contains("evt_event_log");
        assertThat(text).contains("3000");
        assertThat(text).contains("不发明索引");
        assertThat(Files.readString(explain)).contains("EXPLAIN");
        assertThat(Files.readString(explain)).contains("task_instance");
        assertThat(Files.readString(explain)).contains("ad_position");
    }

    @Test
    void routinePrCiDoesNotRunFiveMinuteK6() throws IOException {
        String ci = Files.readString(repoRoot().resolve(".github/workflows/ci.yml"));
        assertThat(ci).doesNotContain("run-full.sh");
        assertThat(ci).doesNotContain("run-p0.sh");
        assertThat(ci).doesNotContain("grafana/k6");
        assertThat(ci).doesNotContain("k6 run");
        assertThat(ci).doesNotContain("DURATION=5m");
        assertThat(ci).contains("e2e-compose.sh");
        assertThat(ci).contains("deploy-smoke.sh");
    }

    private static Path repoRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path candidate = cwd;
        for (int i = 0; i < 6; i++) {
            if (Files.isRegularFile(candidate.resolve("deploy/docker-compose.yml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
            if (candidate == null) {
                break;
            }
        }
        throw new IllegalStateException("deploy/docker-compose.yml not found from " + cwd);
    }
}
