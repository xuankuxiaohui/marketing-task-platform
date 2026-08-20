package com.mkt.identity.it;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RewardPort;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.TaskReadPort;
import com.mkt.contract.UserRewardSummary;
import com.mkt.contract.UserRiskSummary;
import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AdminUserAppService;
import com.mkt.identity.application.AdminUserStore;
import com.mkt.identity.application.AuditLogConsumer;
import com.mkt.identity.application.AuthRateLimiter;
import com.mkt.identity.application.CaptchaService;
import com.mkt.identity.application.IdentityAuditAppender;
import com.mkt.identity.application.CacheAdminAppService;
import com.mkt.identity.application.DictAppService;
import com.mkt.identity.application.InternalAppAppService;
import com.mkt.identity.application.InternalAppSecretCipher;
import com.mkt.identity.application.LoginAuditAppender;
import com.mkt.identity.application.PasswordHasher;
import com.mkt.identity.application.PortalAuthService;
import com.mkt.identity.application.PortalUserAppService;
import com.mkt.identity.application.PortalUserStore;
import com.mkt.identity.application.RbacPermissionCache;
import com.mkt.identity.application.RoleAppService;
import com.mkt.identity.application.SessionService;
import com.mkt.identity.application.UserAttributePortImpl;
import com.mkt.identity.config.ConfigAppService;
import com.mkt.identity.config.MybatisConfigService;
import com.mkt.identity.config.SysConfigMapper;
import com.mkt.identity.mapper.AdminUserMapper;
import com.mkt.identity.mapper.DictEntryMapper;
import com.mkt.identity.mapper.DictTypeMapper;
import com.mkt.identity.mapper.AdminUserRoleMapper;
import com.mkt.identity.mapper.InternalAppMapper;
import com.mkt.identity.mapper.PermissionMapper;
import com.mkt.identity.mapper.PortalUserMapper;
import com.mkt.identity.mapper.RoleMapper;
import com.mkt.identity.mapper.RolePermissionMapper;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
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
    public final AdminUserStore adminUsers;
    public final RoleAppService roles;
    public final RbacPermissionCache permissionCache;
    public final IdentityAuditAppender identityAudits;
    public final AdminUserAppService adminUserApp;
    public final PortalUserAppService portalUserApp;
    public final InternalAppAppService internalApps;
    public final UserAttributePortImpl userAttributes;
    public final InternalAppSecretCipher secretCipher;
    public final DictAppService dictApp;
    public final ConfigAppService configApp;
    public final CacheAdminAppService cacheAdmin;
    public final PlatformCache platformCache;
    public final org.springframework.transaction.PlatformTransactionManager txm;
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
            AdminUserStore adminUsers,
            RoleAppService roles,
            RbacPermissionCache permissionCache,
            IdentityAuditAppender identityAudits,
            AdminUserAppService adminUserApp,
            PortalUserAppService portalUserApp,
            InternalAppAppService internalApps,
            UserAttributePortImpl userAttributes,
            InternalAppSecretCipher secretCipher,
            DictAppService dictApp,
            ConfigAppService configApp,
            CacheAdminAppService cacheAdmin,
            PlatformCache platformCache,
            org.springframework.transaction.PlatformTransactionManager txm,
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
        this.adminUsers = adminUsers;
        this.roles = roles;
        this.permissionCache = permissionCache;
        this.identityAudits = identityAudits;
        this.adminUserApp = adminUserApp;
        this.portalUserApp = portalUserApp;
        this.internalApps = internalApps;
        this.userAttributes = userAttributes;
        this.secretCipher = secretCipher;
        this.dictApp = dictApp;
        this.configApp = configApp;
        this.cacheAdmin = cacheAdmin;
        this.platformCache = platformCache;
        this.txm = txm;
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
        AdminUserMapper adminUserMapper = sql.getMapper(AdminUserMapper.class);
        RoleMapper roleMapper = sql.getMapper(RoleMapper.class);
        PermissionMapper permissionMapper = sql.getMapper(PermissionMapper.class);
        RolePermissionMapper rolePermissionMapper = sql.getMapper(RolePermissionMapper.class);
        AdminUserRoleMapper adminUserRoleMapper = sql.getMapper(AdminUserRoleMapper.class);
        PortalUserMapper portalUserMapper = sql.getMapper(PortalUserMapper.class);
        InternalAppMapper internalAppMapper = sql.getMapper(InternalAppMapper.class);
        DictTypeMapper dictTypeMapper = sql.getMapper(DictTypeMapper.class);
        DictEntryMapper dictEntryMapper = sql.getMapper(DictEntryMapper.class);
        SysConfigMapper sysConfigMapper = sql.getMapper(SysConfigMapper.class);
        TwoLevelPlatformCache platformCache = new TwoLevelPlatformCache(kv);
        RbacPermissionCache rbacCache =
                new RbacPermissionCache(platformCache, adminUserMapper, roleMapper, permissionMapper);
        AdminUserStore adminUsers = new AdminUserStore(adminUserMapper, rbacCache);
        IdentityAuditAppender identityAudits = new IdentityAuditAppender(publisher);
        RoleAppService roleApp = TransactionalProxies.proxy(
                new RoleAppService(
                        roleMapper,
                        adminUserRoleMapper,
                        rolePermissionMapper,
                        permissionMapper,
                        rbacCache,
                        identityAudits,
                        clock),
                txm);
        PortalUserStore portalUsers = new PortalUserStore(portalUserMapper);
        UserAttributePortImpl userAttributes = new UserAttributePortImpl(portalUserMapper, platformCache);
        InternalAppSecretCipher secretCipher =
                new InternalAppSecretCipher("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        MybatisConfigService configs = new MybatisConfigService(sysConfigMapper, platformCache);
        DictAppService dictApp = TransactionalProxies.proxy(
                new DictAppService(dictTypeMapper, dictEntryMapper, platformCache, identityAudits, clock), txm);
        ConfigAppService configApp = TransactionalProxies.proxy(
                new ConfigAppService(sysConfigMapper, platformCache, identityAudits, clock), txm);
        CacheAdminAppService cacheAdmin = TransactionalProxies.proxy(
                new CacheAdminAppService(platformCache, identityAudits), txm);
        PasswordHasher hasher = new PasswordHasher();
        CaptchaService captchas = new CaptchaService(kv, configs);
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(kv, clock);
        AuthRateLimiter rates = new AuthRateLimiter(limiter, (key, def) -> 10_000);
        SessionService sessions = new SessionService();
        LoginAuditAppender audits = new LoginAuditAppender(publisher);
        AdminAuthService adminAuth = TransactionalProxies.proxy(
                new AdminAuthService(adminUsers, captchas, rates, hasher, sessions, audits, configs, clock), txm);
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
        PortalAuthService portalAuth = TransactionalProxies.proxy(
                new PortalAuthService(portalUsers, captchas, rates, hasher, sessions, pass, publisher, configs, clock),
                txm);
        RewardPort rewards = new RewardPort() {
            @Override
            public com.mkt.contract.GrantResult grant(
                    long prizeId,
                    long userId,
                    com.mkt.contract.GrantSource grantSource,
                    String sourceId,
                    com.mkt.contract.GrantContext ctx) {
                throw new UnsupportedOperationException("reward stub");
            }

            @Override
            public UserRewardSummary userSummary(long userId) {
                return new UserRewardSummary(0L, new PrizeSummary(0L, 0L));
            }

            @Override
            public boolean prizeEnabled(long prizeId) {
                return false;
            }
        };
        TaskReadPort taskReads = userId -> new com.mkt.contract.InstanceCounts(0L, 0L);
        AdminUserAppService adminUserApp = TransactionalProxies.proxy(
                new AdminUserAppService(
                        adminUserMapper,
                        adminUserRoleMapper,
                        roleMapper,
                        hasher,
                        sessions,
                        rbacCache,
                        identityAudits,
                        clock),
                txm);
        PortalUserAppService portalUserApp = TransactionalProxies.proxy(
                new PortalUserAppService(
                        portalUserMapper,
                        hasher,
                        sessions,
                        userAttributes,
                        rewards,
                        pass,
                        taskReads,
                        identityAudits,
                        clock),
                txm);
        InternalAppAppService internalAppApp = TransactionalProxies.proxy(
                new InternalAppAppService(internalAppMapper, secretCipher, identityAudits, clock), txm);
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
                adminUsers,
                roleApp,
                rbacCache,
                identityAudits,
                adminUserApp,
                portalUserApp,
                internalAppApp,
                userAttributes,
                secretCipher,
                dictApp,
                configApp,
                cacheAdmin,
                platformCache,
                txm,
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
            configuration.addMapper(RoleMapper.class);
            configuration.addMapper(PermissionMapper.class);
            configuration.addMapper(RolePermissionMapper.class);
            configuration.addMapper(AdminUserRoleMapper.class);
            configuration.addMapper(InternalAppMapper.class);
            configuration.addMapper(DictTypeMapper.class);
            configuration.addMapper(DictEntryMapper.class);
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
