package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Task 43: k6 P0 subset + Playwright journeys exist with §7.8 / §7.9 gates. */
class PerfBaselineTest {

    private static final List<String> P0_SCRIPTS =
            List.of("list.js", "advance.js", "complete.js", "risk-delta.js", "track.js", "admin-list.js");

    @Test
    void p0K6ScriptsPinNfrThresholdsAndFiveMinuteDuration() throws IOException {
        Path perf = repoRoot().resolve("perf");
        String lib = Files.readString(perf.resolve("lib.js"));
        assertThat(lib).contains("5m");
        for (String name : P0_SCRIPTS) {
            Path script = perf.resolve(name);
            assertThat(script).exists();
            String src = Files.readString(script);
            assertThat(src).contains("DURATION");
            assertThat(src).doesNotContain("perf/ad.js");
        }
        String list = Files.readString(perf.resolve("list.js"));
        assertThat(list).contains("p(95)<=300");
        assertThat(list).contains("rate==0");
        assertThat(list).contains("500");

        String advance = Files.readString(perf.resolve("advance.js"));
        assertThat(advance).contains("p(95)<=200");
        assertThat(advance).contains("/internal/task/progress");
        assertThat(advance).contains("/internal/task/callback");
        assertThat(advance).contains("300");

        String complete = Files.readString(perf.resolve("complete.js"));
        assertThat(complete).contains("p(95)<=500");
        assertThat(complete).contains("grant");

        String risk = Files.readString(perf.resolve("risk-delta.js"));
        assertThat(risk).contains("maxDeltaMs: 20");
        assertThat(risk).contains("VARIANT");

        String track = Files.readString(perf.resolve("track.js"));
        assertThat(track).contains("p(95)<=100");
        assertThat(track).contains("6000");
        assertThat(track).contains("0.001");
        assertThat(track).contains("50");

        String admin = Files.readString(perf.resolve("admin-list.js"));
        assertThat(admin).contains("p(95)<=800");
        assertThat(admin).contains("/admin/task/instances");
        assertThat(admin).doesNotContain("/admin/reward/records");
    }

    @Test
    void seedAndRunnerTargetStagingComposeWithoutPublicInternal() throws IOException {
        String runner = Files.readString(repoRoot().resolve("perf/run-p0.sh"));
        assertThat(runner).contains("list.js");
        assertThat(runner).contains("advance.js");
        assertThat(runner).contains("complete.js");
        assertThat(runner).contains("risk-delta.js");
        assertThat(runner).contains("track.js");
        assertThat(runner).contains("admin-list.js");
        assertThat(runner).contains("INTERNAL_URL=http://portal-app-1:8081");
        assertThat(runner).doesNotContain("location /internal");
        assertThat(Files.isRegularFile(repoRoot().resolve("perf/seed/seed.py"))).isTrue();
        String nginx = Files.readString(repoRoot().resolve("deploy/nginx/nginx.conf"));
        assertThat(nginx).contains("location /internal");
        assertThat(nginx).contains("return 404");
    }

    @Test
    void playwrightJourneysAreRunnableAgainstCompose() throws IOException {
        String core = Files.readString(repoRoot().resolve("web/e2e/journey-core.spec.ts"));
        String admin = Files.readString(repoRoot().resolve("web/e2e/journey-admin.spec.ts"));
        String config = Files.readString(repoRoot().resolve("web/playwright.config.ts"));
        String ci = Files.readString(repoRoot().resolve("ci/e2e-compose.sh"));
        assertThat(core).doesNotContain("test.skip(");
        assertThat(admin).doesNotContain("test.skip(");
        assertThat(core).contains("R32.1");
        assertThat(core).contains("task.card.exposure");
        assertThat(admin).contains("R14.9");
        assertThat(config).contains("PLAYWRIGHT_BASE_URL");
        assertThat(config).contains("PLAYWRIGHT_ADMIN_BASE_URL");
        assertThat(config).doesNotContain("192.168.");
        assertThat(ci).contains("pnpm test:e2e");
        assertThat(ci).contains("18080");
        assertThat(Files.readString(repoRoot().resolve(".github/workflows/ci.yml"))).contains("e2e-compose.sh");
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
