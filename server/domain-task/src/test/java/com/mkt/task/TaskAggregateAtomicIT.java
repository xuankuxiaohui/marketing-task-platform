package com.mkt.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.application.MybatisTaskChildStore;
import com.mkt.task.application.MybatisTaskCrowdStore;
import com.mkt.task.application.MybatisTaskDefinitionStore;
import com.mkt.task.application.MybatisTaskMutexGroupStore;
import com.mkt.task.application.TaskDefinitionAppService;
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
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** R11.1: failed aggregate save leaves zero rows (CI / Testcontainers). */
@Testcontainers
class TaskAggregateAtomicIT {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("mkt_platform")
            .withUsername("mkt")
            .withPassword("mkt");

    private HikariDataSource dataSource;
    private JdbcTemplate jdbc;
    private TaskDefinitionAppService service;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(MYSQL.getJdbcUrl());
        hikari.setUsername(MYSQL.getUsername());
        hikari.setPassword(MYSQL.getPassword());
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sql = sqlSession(dataSource);
        TaskSettings settings = new TaskSettings();
        settings.setStepMaxCount(3);
        service = new TaskDefinitionAppService(
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
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        tx = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void threeInvalidSavesLeaveZeroRows() {
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.saveAggregate(tooManySteps())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_COUNT_EXCEEDED);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.saveAggregate(missingTarget())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.DAG_CYCLE);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.saveAggregate(duplicateCodes())))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.STEP_CODE_DUPLICATE);
        assertThat(count("task_definition")).isZero();
        assertThat(count("task_step")).isZero();
        assertThat(count("task_step_transition")).isZero();
        assertThat(count("task_platform_action")).isZero();
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }

    private static TaskDefinitionSaveCommand tooManySteps() {
        List<TaskStepCommand> steps = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            steps.add(new TaskStepCommand("s" + i, "n", i, "CLICK", null, null));
        }
        return base("too_many_steps", steps, List.of());
    }

    private static TaskDefinitionSaveCommand missingTarget() {
        return base(
                "missing_target",
                List.of(new TaskStepCommand("only", "only", 1, "CLICK", null, null)),
                List.of(new TaskTransitionCommand("only", "ghost", null, 0)));
    }

    private static TaskDefinitionSaveCommand duplicateCodes() {
        return base(
                "dup_codes",
                List.of(
                        new TaskStepCommand("same", "a", 1, "CLICK", null, null),
                        new TaskStepCommand("same", "b", 2, "CLICK", null, null)),
                List.of());
    }

    private static TaskDefinitionSaveCommand base(
            String code, List<TaskStepCommand> steps, List<TaskTransitionCommand> edges) {
        return new TaskDefinitionSaveCommand(
                null, code, "n", null, null, null, null, null, null, 0, "NONE", null, null, null, null, null, null,
                steps, edges, List.of());
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
