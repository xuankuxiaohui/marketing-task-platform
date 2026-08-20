package com.mkt.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.testsupport.MemoryPrizeEnabledLookup;
import com.mkt.task.application.MybatisTaskChildStore;
import com.mkt.task.application.MybatisTaskCrowdStore;
import com.mkt.task.application.MybatisTaskDefinitionStore;
import com.mkt.task.application.MybatisTaskMutexGroupStore;
import com.mkt.task.application.MybatisTaskVersionSnapshotStore;
import com.mkt.task.application.TaskDefinitionAppService;
import com.mkt.task.application.TaskPublishAppService;
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
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskSettings;
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

final class PublishITSupport implements AutoCloseable {

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final TaskDefinitionAppService defs;
    final TaskPublishAppService publishes;
    final TransactionTemplate tx;

    PublishITSupport(MySQLContainer<?> mysql) throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sql = sqlSession(dataSource);
        TaskSettings settings = new TaskSettings();
        settings.setStepMaxCount(10);
        Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
        defs = new TaskDefinitionAppService(
                new MybatisTaskDefinitionStore(sql.getMapper(TaskDefinitionMapper.class)),
                new MybatisTaskChildStore(
                        sql.getMapper(TaskStepMapper.class),
                        sql.getMapper(TaskStepTransitionMapper.class),
                        sql.getMapper(TaskPlatformActionMapper.class),
                        sql.getMapper(TaskStepPlatformActionMapper.class)),
                new MybatisTaskMutexGroupStore(sql.getMapper(TaskMutexGroupMapper.class)),
                new MybatisTaskCrowdStore(
                        sql.getMapper(TaskCrowdMapper.class), sql.getMapper(TaskCrowdItemMapper.class)),
                settings,
                clock);
        publishes = new TaskPublishAppService(
                new MybatisTaskDefinitionStore(sql.getMapper(TaskDefinitionMapper.class)),
                new MybatisTaskMutexGroupStore(sql.getMapper(TaskMutexGroupMapper.class)),
                new MybatisTaskVersionSnapshotStore(sql.getMapper(TaskVersionSnapshotMapper.class)),
                defs,
                new MemoryPrizeEnabledLookup(),
                clock,
                null,
                null,
                new AlertWebhook(""),
                jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
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

    static TaskDefinitionSaveCommand emptySteps(String code) {
        return new TaskDefinitionSaveCommand(
                null,
                code,
                "空步骤",
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
                List.of(),
                List.of(),
                List.of());
    }

    static TaskDefinitionSaveCommand disconnected(String code) {
        return new TaskDefinitionSaveCommand(
                null,
                code,
                "断图",
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
                        new TaskStepCommand("a", "a", 1, "CLICK", null, null),
                        new TaskStepCommand("b", "b", 2, "CLICK", null, null)),
                List.of(),
                List.of());
    }

    static TaskDefinitionSaveCommand renamed(TaskDefinitionSaveCommand base, long id, String name) {
        return new TaskDefinitionSaveCommand(
                id,
                base.code(),
                name,
                base.description(),
                base.category(),
                base.iconUrl(),
                base.badgeText(),
                base.startTime(),
                base.endTime(),
                base.sortWeight(),
                base.cycleType(),
                base.cronExpr(),
                base.specialStart(),
                base.specialEnd(),
                base.mutexGroupId(),
                base.gray(),
                base.filter(),
                base.steps(),
                base.transitions(),
                base.actions());
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
