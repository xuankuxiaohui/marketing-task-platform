package com.mkt.portal;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.RewardPort;
import com.mkt.identity.application.RewardPortStub;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.points.PointsPort;
import com.mkt.reward.points.PointsPortImpl;
import com.mkt.reward.port.RewardPortImpl;
import com.mkt.infra.redis.InfraRedisProperties;
import com.mkt.infra.redis.RedissonFactory;
import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.engine.StepEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.redisson.api.RedissonClient;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * RL-05: portal-app assembles task + reward in-process (not a remote proxy).
 * Requires Docker; leave for CI when absent.
 */
@Testcontainers
class PortalAssemblyIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @Test
    void portalPomDependsOnTaskAndReward() throws Exception {
        String pom = Files.readString(portalPom());
        assertThat(pom).contains("<artifactId>domain-task</artifactId>");
        assertThat(pom).contains("<artifactId>domain-reward</artifactId>");
        assertThat(pom).doesNotContain("<artifactId>domain-points</artifactId>");
    }

    @Test
    void portalContextWiresInProcessRewardPortAndStepEngine() {
        try (ConfigurableApplicationContext ctx = startPortal()) {
            RewardPort rewards = ctx.getBean(RewardPort.class);
            PointsPort points = ctx.getBean(PointsPort.class);
            assertThat(AopProxyUtils.ultimateTargetClass(rewards)).isEqualTo(RewardPortImpl.class);
            assertThat(AopProxyUtils.ultimateTargetClass(points)).isEqualTo(PointsPortImpl.class);
            assertThat(ctx.getBean(TaskStepAppService.class)).isNotNull();
            assertThat(ctx.getBean(PointsAppService.class)).isNotNull();
            assertThat(StepEngine.class.getPackageName()).isEqualTo("com.mkt.task.engine");
            assertThat(ctx.getBeanNamesForType(RewardPortStub.class)).isEmpty();
            assertThat(rewards.getClass().getName()).doesNotContain("Feign", "HttpClient", "RestClient");
            Integer definitions = ctx.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                    .queryForObject("SELECT COUNT(*) FROM task_definition", Integer.class);
            assertThat(definitions).isNotNull();
            InfraRedisProperties redis = ctx.getBean(InfraRedisProperties.class);
            assertThat(redis.host()).isEqualTo(REDIS.getHost());
            assertThat(redis.port()).isEqualTo(REDIS.getMappedPort(6379));
            assertThat(redis.database()).isEqualTo(2);
        }
    }

    private static ConfigurableApplicationContext startPortal() {
        SpringApplication app = new SpringApplication(PortalApplication.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        InfraRedisProperties redis = new InfraRedisProperties(REDIS.getHost(), REDIS.getMappedPort(6379), "", 2);
        app.addInitializers((GenericApplicationContext ctx) -> {
            ctx.registerBean(InfraRedisProperties.class, () -> redis);
            ctx.registerBean(
                    RedissonClient.class, () -> RedissonFactory.create(redis), bd -> bd.setDestroyMethodName("shutdown"));
        });
        return app.run(
                "--server.port=0",
                "--spring.main.banner-mode=off",
                "--spring.task.scheduling.enabled=false",
                "--spring.flyway.enabled=true",
                "--spring.datasource.url=" + MYSQL.getJdbcUrl(),
                "--spring.datasource.username=" + MYSQL.getUsername(),
                "--spring.datasource.password=" + MYSQL.getPassword(),
                "--mkt.outbox.producer=portal");
    }

    private static Path portalPom() {
        Path cwd = Path.of("").toAbsolutePath();
        Path nested = cwd.resolve("portal-app/pom.xml");
        if (Files.isRegularFile(nested)) {
            return nested;
        }
        Path local = cwd.resolve("pom.xml");
        if (Files.isRegularFile(local) && cwd.getFileName().toString().equals("portal-app")) {
            return local;
        }
        throw new IllegalStateException("cannot locate portal-app pom from " + cwd);
    }
}
