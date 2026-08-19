package com.mkt.identity.it;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserRiskSummary;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AdminUserStore;
import com.mkt.identity.application.AuditLogConsumer;
import com.mkt.identity.application.AuthRateLimiter;
import com.mkt.identity.application.CaptchaService;
import com.mkt.identity.application.LoginAuditAppender;
import com.mkt.identity.application.PasswordHasher;
import com.mkt.identity.application.PortalAuthService;
import com.mkt.identity.application.PortalUserStore;
import com.mkt.identity.application.SessionService;
import com.mkt.identity.config.MybatisConfigService;
import com.mkt.identity.config.SysConfigMapper;
import com.mkt.identity.mapper.AdminUserMapper;
import com.mkt.identity.mapper.PortalUserMapper;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.outbox.AwaitOutboxDrain;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.JdbcOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.outbox.OutboxRelay;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.time.MutableClock;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

public final class IdentityITSupport implements AutoCloseable {

    public final MutableClock clock;
    public final JdbcTemplate jdbc;
    public final TransactionTemplate tx;
    public final AdminAuthService adminAuth;
    public final PortalAuthService portalAuth;
    public final CaptchaService captchas;
    public final MemoryKeyValueStore kv;
    public final AwaitOutboxDrain drain;
    public final PasswordHasher hasher;
    public final SessionService sessions;
    private final HikariDataSource dataSource;

    private IdentityITSupport(
            MutableClock clock,
            JdbcTemplate jdbc,
            TransactionTemplate tx,
            AdminAuthService adminAuth,
            PortalAuthService portalAuth,
            CaptchaService captchas,
            MemoryKeyValueStore kv,
            AwaitOutboxDrain drain,
            PasswordHasher hasher,
            SessionService sessions,
            HikariDataSource dataSource) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.tx = tx;
        this.adminAuth = adminAuth;
        this.portalAuth = portalAuth;
        this.captchas = captchas;
        this.kv = kv;
        this.drain = drain;
        this.hasher = hasher;
        this.sessions = sessions;
        this.dataSource = dataSource;
    }

    public static MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("mkt_platform")
                .withUsername("mkt")
                .withPassword("mkt");
    }

    public static IdentityITSupport start(MySQLContainer<?> mysql, Instant now) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        hikari.setMaximumPoolSize(64);
        hikari.setPoolName("identity-it");
        HikariDataSource ds = new HikariDataSource(hikari);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        MutableClock clock = new MutableClock(now);
        MemoryKeyValueStore kv = new MemoryKeyValueStore();
        JdbcOutboxStore outbox = new JdbcOutboxStore(jdbc);
        EventPublisher publisher = new EventPublisher(outbox, OutboxProducer.ADMIN);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(ds);
        TransactionTemplate tx = new TransactionTemplate(txm);
        SqlSessionTemplate sql = mybatis(ds);
        AdminUserStore adminUsers = new AdminUserStore(sql.getMapper(AdminUserMapper.class));
        PortalUserStore portalUsers = new PortalUserStore(sql.getMapper(PortalUserMapper.class));
        MybatisConfigService configs = new MybatisConfigService(sql.getMapper(SysConfigMapper.class));
        PasswordHasher hasher = new PasswordHasher();
        CaptchaService captchas = new CaptchaService(kv, configs);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(kv, clock);
        AuthRateLimiter rates = new AuthRateLimiter(limiter, (key, def) -> 10_000);
        SessionService sessions = new SessionService();
        LoginAuditAppender audits = new LoginAuditAppender(publisher);
        AdminAuthService adminAuth =
                new AdminAuthService(adminUsers, captchas, rates, hasher, sessions, audits, configs, clock);
        RiskCheckPort pass = new RiskCheckPort() {
            @Override
            public RiskVerdict check(com.mkt.contract.RiskScene scene, com.mkt.contract.RiskSubject subject) {
                return new RiskVerdict(RiskAction.PASS);
            }

            @Override
            public UserRiskSummary userSummary(long userId) {
                return new UserRiskSummary(0, List.of());
            }
        };
        PortalAuthService portalAuth = new PortalAuthService(
                portalUsers, captchas, rates, hasher, sessions, pass, publisher, configs, clock);
        OutboxRelay relay = new OutboxRelay(
                outbox, OutboxProducer.ADMIN, new PlatformLock(kv), clock, List.of(new AuditLogConsumer(jdbc, clock)));
        return new IdentityITSupport(
                clock,
                jdbc,
                tx,
                adminAuth,
                portalAuth,
                captchas,
                kv,
                new AwaitOutboxDrain(outbox, OutboxProducer.ADMIN, relay, clock),
                hasher,
                sessions,
                ds);
    }

    private static SqlSessionTemplate mybatis(javax.sql.DataSource dataSource) {
        try {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
            factoryBean.setTypeAliasesPackage("com.mkt.identity.entity");
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(AdminUserMapper.class);
            configuration.addMapper(PortalUserMapper.class);
            configuration.addMapper(SysConfigMapper.class);
            factoryBean.setConfiguration(configuration);
            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
            dbConfig.setIdType(IdType.AUTO);
            globalConfig.setDbConfig(dbConfig);
            factoryBean.setGlobalConfig(globalConfig);
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("SqlSessionFactory is null");
            }
            return new SqlSessionTemplate(factory);
        } catch (Exception ex) {
            throw new IllegalStateException("identity mybatis factory", ex);
        }
    }

    public String captchaCode(String realm, String captchaId) {
        return kv.get(CaptchaService.key(realm, captchaId));
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
