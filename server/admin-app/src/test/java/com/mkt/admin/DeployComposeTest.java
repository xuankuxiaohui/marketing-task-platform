package com.mkt.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Task 42: compose / env / gateway / flyway-admin-only inventory (R31). */
class DeployComposeTest {

    @Test
    void composeStartsMysqlRedisNginxDualAppsPrometheusWithHealthOrder() throws IOException {
        String compose = read("deploy/docker-compose.yml");
        assertThat(compose).contains("image: mysql:8.0");
        assertThat(compose).contains("image: redis:7-alpine");
        assertThat(compose).contains("admin-app:");
        assertThat(compose).contains("portal-app-1:");
        assertThat(compose).contains("portal-app-2:");
        assertThat(compose).contains("nginx:");
        assertThat(compose).contains("prometheus:");
        assertThat(compose).contains("condition: service_healthy");
        assertThat(compose).contains("/actuator/health/readiness");
        assertThat(compose).contains("SPRING_FLYWAY_ENABLED: \"true\"");
        assertThat(compose).contains("SPRING_FLYWAY_ENABLED: \"false\"");
        assertThat(compose).contains("REDIS_DATABASE: \"2\"");
        assertThat(compose).contains("limits:");
        assertThat(compose).contains("JAVA_TOOL_OPTIONS");
        assertThat(compose).contains("MaxRAMPercentage");
        String dockerfile = read("deploy/docker/Dockerfile");
        assertThat(dockerfile).contains("fontconfig");
        assertThat(dockerfile).contains("fonts-dejavu-core");
        assertThat(dockerfile).contains("MaxRAMPercentage");
        assertThat(dockerfile).contains("HOME=/app");
        String smoke = read("ci/deploy-smoke.sh");
        assertThat(smoke).contains("dump_stack");
        String e2e = read("ci/e2e-compose.sh");
        assertThat(e2e).contains("dump_stack");
        assertThat(hostPublishedPorts(compose))
                .doesNotContain("3308", "6379", "8080", "8081")
                .anyMatch(port -> port.contains("18080"))
                .anyMatch(port -> port.contains("19090"));
    }

    @Test
    void nginxPrefixesAdminAndApiButNotInternalOrActuator() throws IOException {
        String nginx = read("deploy/nginx/nginx.conf");
        assertThat(nginx).contains("location /admin/");
        assertThat(nginx).contains("proxy_pass http://admin_app");
        assertThat(nginx).contains("location /api/");
        assertThat(nginx).contains("proxy_pass http://portal_app");
        assertThat(nginx).contains("location /internal");
        assertThat(nginx).contains("location /actuator");
        assertThat(internalLocation(nginx)).contains("return 404").doesNotContain("proxy_pass");
        assertThat(actuatorLocation(nginx)).contains("return 404").doesNotContain("proxy_pass");
        assertThat(nginx).contains("X-CSRF-Token");
        assertThat(nginx).contains("X-Trace-Id");
        assertThat(nginx).contains("X-Sign");
    }

    @Test
    void prometheusScrapesBothAppsInternallyAndListsNfrAlerts() throws IOException {
        String prom = read("deploy/prometheus/prometheus.yml");
        assertThat(prom).contains("metrics_path: /actuator/prometheus");
        assertThat(prom).contains("admin-app:8080");
        assertThat(prom).contains("portal-app-1:8081");
        assertThat(prom).contains("portal-app-2:8081");
        String alerts = read("deploy/prometheus/alerts.yml");
        assertThat(alerts).contains("HttpErrorRateHigh");
        assertThat(alerts).contains("OutboxBacklogHigh");
        assertThat(alerts).contains("GrantPermanentFailure");
        assertThat(alerts).contains("PrizeStockLow");
        assertThat(alerts).contains("RedisUnavailable");
        assertThat(alerts).contains("DegradeEvent");
        assertThat(alerts).contains("TrackDropRateHigh");
    }

    @Test
    void envExampleHasInventoryWithoutRealSecretsAndPinsRedisDb2() throws IOException {
        String example = read("deploy/.env.example");
        assertThat(example).contains("REDIS_DATABASE=2");
        assertThat(example).contains("MKT_REDIS_DATABASE=2");
        assertThat(example).contains("MKT_DATASOURCE_PASSWORD=");
        assertThat(example).contains("MKT_FLYWAY_PASSWORD=");
        assertThat(example).contains("MKT_INTERNAL_APP_AES_KEY=");
        assertThat(example).contains("MKT_INIT_ADMIN_PASSWORD=");
        assertThat(example).doesNotContain("192.168.88.149");
        for (String line : example.split("\n")) {
            if (line.isBlank() || line.startsWith("#") || !line.contains("=")) {
                continue;
            }
            String key = line.substring(0, line.indexOf('='));
            String value = line.substring(line.indexOf('=') + 1);
            if (key.contains("PASSWORD") || key.contains("SECRET") || key.contains("AES_KEY")) {
                assertThat(value).isNotBlank();
                assertThat(value).containsAnyOf("CHANGE_ME", "0123456789abcdef");
            }
        }
    }

    @Test
    void flywayOnlyAdminInAppYamlAndChecklistAndBackupExist() throws IOException {
        String adminYml = Files.readString(serverRoot().resolve("admin-app/src/main/resources/application.yml"));
        String portalYml = Files.readString(serverRoot().resolve("portal-app/src/main/resources/application.yml"));
        assertThat(adminYml).contains("enabled: true");
        assertThat(adminYml).contains("MKT_FLYWAY_USER");
        assertThat(adminYml).contains("probes:");
        assertThat(adminYml).contains("include: health,prometheus");
        assertThat(portalYml).contains("enabled: ${MKT_FLYWAY_ENABLED:false}");
        assertThat(portalYml).contains("include: health,prometheus");
        assertThat(Files.isRegularFile(repoRoot().resolve("deploy/R31-go-live-checklist.md"))).isTrue();
        assertThat(Files.isRegularFile(repoRoot().resolve("deploy/backup/backup.sh"))).isTrue();
        assertThat(Files.isRegularFile(repoRoot().resolve("deploy/backup/restore.sh"))).isTrue();
        assertThat(Files.isRegularFile(repoRoot().resolve("deploy/backup/RESTORE-DRILL.md"))).isTrue();
        assertThat(Files.isRegularFile(repoRoot().resolve("ci/deploy-smoke.sh"))).isTrue();
        String smoke = read("ci/deploy-smoke.sh");
        assertThat(smoke).contains("up -d");
        assertThat(smoke).contains("/admin/auth/login");
        assertThat(smoke).contains("flyway_schema_history");
    }

    private static List<String> hostPublishedPorts(String compose) {
        Matcher matcher = Pattern.compile("\"127\\.0\\.0\\.1:\\$\\{[^}]+:-(\\d+)}:(\\d+)\"").matcher(compose);
        ArrayList<String> ports = new ArrayList<>();
        while (matcher.find()) {
            ports.add(matcher.group(1));
            ports.add(matcher.group(2));
        }
        return ports;
    }

    private static String internalLocation(String nginx) {
        int start = nginx.indexOf("location /internal");
        return nginx.substring(start, nginx.indexOf('}', start));
    }

    private static String actuatorLocation(String nginx) {
        int start = nginx.indexOf("location /actuator");
        return nginx.substring(start, nginx.indexOf('}', start));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(repoRoot().resolve(relative));
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

    private static Path serverRoot() {
        return repoRoot().resolve("server");
    }
}
