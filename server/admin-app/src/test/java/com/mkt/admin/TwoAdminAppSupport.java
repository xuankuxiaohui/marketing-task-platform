package com.mkt.admin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.redis.RedissonFactory;
import com.mkt.infra.redis.InfraRedisProperties;
import com.mkt.infra.redis.RedissonKeyValueStore;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.application.MybatisTaskChildStore;
import com.mkt.task.application.MybatisTaskCrowdStore;
import com.mkt.task.application.MybatisTaskDefinitionStore;
import com.mkt.task.application.MybatisTaskMutexGroupStore;
import com.mkt.task.application.MybatisTaskVersionSnapshotStore;
import com.mkt.task.application.TaskDefinitionAppService;
import com.mkt.task.application.TaskPublishAppService;
import com.mkt.task.command.ScheduleCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.mapper.TaskCrowdItemMapper;
import com.mkt.task.mapper.TaskCrowdMapper;
import com.mkt.task.mapper.TaskDefinitionMapper;
import com.mkt.task.mapper.TaskMutexGroupMapper;
import com.mkt.task.mapper.TaskPlatformActionMapper;
import com.mkt.task.mapper.TaskStepMapper;
import com.mkt.task.mapper.TaskStepPlatformActionMapper;
import com.mkt.task.mapper.TaskStepTransitionMapper;
import com.mkt.task.mapper.TaskVersionSnapshotMapper;
import com.mkt.task.schedule.PublishScanScheduler;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/** Two admin-app scheduler instances sharing MySQL + Redis (topology B). */
final class TwoAdminAppSupport implements AutoCloseable {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final RedissonClient redis;
    final PublishScanScheduler instanceA;
    final PublishScanScheduler instanceB;
    final TaskDefinitionAppService defs;
    final TaskPublishAppService publishesA;
    final TaskPublishAppService publishesB;
    final TransactionTemplate tx;

    TwoAdminAppSupport(MySQLContainer<?> mysql, GenericContainer<?> redisContainer) throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        hikari.setMaximumPoolSize(16);
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sql = sqlSession(dataSource);
        TaskSettings settings = new TaskSettings();
        settings.setStepMaxCount(10);
        MybatisTaskDefinitionStore definitions =
                new MybatisTaskDefinitionStore(sql.getMapper(TaskDefinitionMapper.class));
        MybatisTaskMutexGroupStore mutex =
                new MybatisTaskMutexGroupStore(sql.getMapper(TaskMutexGroupMapper.class));
        MybatisTaskCrowdStore crowds = new MybatisTaskCrowdStore(
                sql.getMapper(TaskCrowdMapper.class), sql.getMapper(TaskCrowdItemMapper.class));
        MybatisTaskVersionSnapshotStore snapshots =
                new MybatisTaskVersionSnapshotStore(sql.getMapper(TaskVersionSnapshotMapper.class));
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
                CLOCK);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
        publishesA = publishService(definitions, mutex, snapshots);
        publishesB = publishService(definitions, mutex, snapshots);
        redis = RedissonFactory.create(new InfraRedisProperties(
                redisContainer.getHost(), redisContainer.getMappedPort(6379), "", 2));
        RedissonKeyValueStore storeA = new RedissonKeyValueStore(redis);
        RedissonKeyValueStore storeB = new RedissonKeyValueStore(redis);
        instanceA = new PublishScanScheduler(publishesA, new PlatformLock(storeA), CLOCK);
        instanceB = new PublishScanScheduler(publishesB, new PlatformLock(storeB), CLOCK);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
    }

    long seedDueScheduled(String code) {
        long id = tx.execute(status -> defs.saveAggregate(legal(code)).id());
        tx.executeWithoutResult(
                status -> publishesA.schedule(id, new ScheduleCommand(Instant.parse("2026-08-20T00:00:00Z"))));
        jdbc.update(
                "UPDATE task_definition SET schedule_publish_at = ? WHERE id = ?",
                LocalDateTime.of(2026, 8, 18, 0, 0),
                id);
        return id;
    }

    private TaskPublishAppService publishService(
            MybatisTaskDefinitionStore definitions,
            MybatisTaskMutexGroupStore mutex,
            MybatisTaskVersionSnapshotStore snapshots) {
        return new TaskPublishAppService(
                definitions,
                mutex,
                snapshots,
                defs,
                prizeId -> true,
                CLOCK,
                null,
                null,
                new AlertWebhook(""),
                jdbc,
                tx);
    }

    static TaskDefinitionSaveCommand legal(String code) {
        return new TaskDefinitionSaveCommand(
                null,
                code,
                "每日签到",
                null,
                "daily",
                null,
                null,
                null,
                null,
                0,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(
                        new TaskStepCommand("go_page", "浏览", 1, "PASSIVE", null, null),
                        new TaskStepCommand("click", "点击", 2, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("go_page", "click", null, 0)),
                List.of());
    }

    @Override
    public void close() {
        UserContext.clear();
        if (redis != null) {
            redis.shutdown();
        }
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
