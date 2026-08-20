package com.mkt.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.contract.AccountStatus;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.JdbcOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.application.MybatisTaskChildStore;
import com.mkt.task.application.MybatisTaskCrowdStore;
import com.mkt.task.application.MybatisTaskDefinitionStore;
import com.mkt.task.application.MybatisTaskInstanceStore;
import com.mkt.task.application.MybatisTaskMutexGroupStore;
import com.mkt.task.application.MybatisTaskVersionSnapshotStore;
import com.mkt.task.application.TaskClaimAppService;
import com.mkt.task.application.TaskDefinitionAppService;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.application.TaskPublishAppService;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.mapper.TaskCrowdItemMapper;
import com.mkt.task.mapper.TaskCrowdMapper;
import com.mkt.task.mapper.TaskDefinitionMapper;
import com.mkt.task.mapper.TaskInstanceMapper;
import com.mkt.task.mapper.TaskInstanceStepMapper;
import com.mkt.task.mapper.TaskMutexGroupMapper;
import com.mkt.task.mapper.TaskPlatformActionMapper;
import com.mkt.task.mapper.TaskStepMapper;
import com.mkt.task.mapper.TaskStepPlatformActionMapper;
import com.mkt.task.mapper.TaskStepTransitionMapper;
import com.mkt.task.mapper.TaskVersionSnapshotMapper;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryPrizeEnabledLookup;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

final class ClaimITSupport implements AutoCloseable {

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final TaskDefinitionAppService defs;
    final TaskPublishAppService publishes;
    final TaskClaimAppService claims;
    final TaskPortalAppService portal;
    final TransactionTemplate tx;
    final EventPublisher publisher;
    final Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);

    ClaimITSupport(MySQLContainer<?> mysql, UserAttributePort users, RiskCheckPort risk, EventPublisher publisher)
            throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        hikari.setMaximumPoolSize(64);
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sql = sqlSession(dataSource);
        TaskSettings settings = new TaskSettings();
        MybatisTaskDefinitionStore definitions = new MybatisTaskDefinitionStore(sql.getMapper(TaskDefinitionMapper.class));
        MybatisTaskMutexGroupStore mutex = new MybatisTaskMutexGroupStore(sql.getMapper(TaskMutexGroupMapper.class));
        MybatisTaskCrowdStore crowds =
                new MybatisTaskCrowdStore(sql.getMapper(TaskCrowdMapper.class), sql.getMapper(TaskCrowdItemMapper.class));
        MybatisTaskVersionSnapshotStore snapshots =
                new MybatisTaskVersionSnapshotStore(sql.getMapper(TaskVersionSnapshotMapper.class));
        MybatisTaskInstanceStore instances = new MybatisTaskInstanceStore(
                sql.getMapper(TaskInstanceMapper.class), sql.getMapper(TaskInstanceStepMapper.class));
        defs = new TaskDefinitionAppService(
                definitions,
                new MybatisTaskChildStore(
                        sql.getMapper(TaskStepMapper.class),
                        sql.getMapper(TaskStepTransitionMapper.class),
                        sql.getMapper(TaskPlatformActionMapper.class),
                        sql.getMapper(TaskStepPlatformActionMapper.class)),
                mutex,
                crowds,
                settings,
                clock);
        publishes = new TaskPublishAppService(
                definitions,
                mutex,
                snapshots,
                defs,
                new MemoryPrizeEnabledLookup(),
                clock,
                null,
                null,
                new AlertWebhook(""),
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
        JdbcOutboxStore outbox = new JdbcOutboxStore(jdbc);
        this.publisher = publisher != null ? publisher : new EventPublisher(outbox, OutboxProducer.PORTAL);
        claims = new TaskClaimAppService(
                definitions,
                snapshots,
                instances,
                crowds,
                mutex,
                users,
                risk,
                this.publisher,
                clock,
                settings);
        portal = new TaskPortalAppService(definitions, snapshots, instances, crowds, users, risk, clock);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
    }

    static UserAttributes active() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }

    static UserAttributePort activeUsers() {
        return new UserAttributePort() {
            @Override
            public UserAttributes attributes(long userId) {
                return active();
            }

            @Override
            public UserAttributes lockAndGet(long userId) {
                return active();
            }
        };
    }

    static RiskCheckPort passRisk() {
        return new RiskCheckPort() {
            @Override
            public RiskVerdict check(com.mkt.contract.RiskScene scene, com.mkt.contract.RiskSubject subject) {
                return new RiskVerdict(RiskAction.PASS);
            }

            @Override
            public com.mkt.contract.UserRiskSummary userSummary(long userId) {
                return new com.mkt.contract.UserRiskSummary(0L, List.of());
            }
        };
    }

    long publishLegal(String code) {
        long taskId = tx.execute(status -> defs.saveAggregate(PublishITSupport.legal(code)).id());
        tx.executeWithoutResult(status -> publishes.publish(taskId, new PublishCommand(null, null)));
        return taskId;
    }

    @Override
    public void close() {
        UserContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static SqlSessionTemplate sqlSession(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/task/*.xml"));
        factoryBean.setTypeAliasesPackage("com.mkt.task.entity");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(TaskDefinitionMapper.class);
        configuration.addMapper(TaskStepMapper.class);
        configuration.addMapper(TaskStepTransitionMapper.class);
        configuration.addMapper(TaskPlatformActionMapper.class);
        configuration.addMapper(TaskStepPlatformActionMapper.class);
        configuration.addMapper(TaskMutexGroupMapper.class);
        configuration.addMapper(TaskCrowdMapper.class);
        configuration.addMapper(TaskCrowdItemMapper.class);
        configuration.addMapper(TaskVersionSnapshotMapper.class);
        configuration.addMapper(TaskInstanceMapper.class);
        configuration.addMapper(TaskInstanceStepMapper.class);
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
    }
}
