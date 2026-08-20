package com.mkt.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.contract.AccountStatus;
import com.mkt.contract.GrantSource;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.JdbcOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.reward.application.FulfillmentService;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.GrantFailureLedger;
import com.mkt.reward.application.MybatisGrantRecordStore;
import com.mkt.reward.application.MybatisPointsStore;
import com.mkt.reward.application.MybatisPrizeCategoryStore;
import com.mkt.reward.application.MybatisPrizeStore;
import com.mkt.reward.application.MybatisStockLogStore;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.application.PrizeAppService;
import com.mkt.reward.application.SnapshotPrizeScanner;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.mapper.GrantRecordMapper;
import com.mkt.reward.mapper.PntAccountMapper;
import com.mkt.reward.mapper.PntTransactionMapper;
import com.mkt.reward.mapper.PrizeCategoryMapper;
import com.mkt.reward.mapper.PrizeMapper;
import com.mkt.reward.mapper.StockLogMapper;
import com.mkt.reward.points.PointsPort;
import com.mkt.reward.points.PointsPortImpl;
import com.mkt.reward.port.RewardPortImpl;
import com.mkt.reward.support.RewardGrantSettings;
import com.mkt.reward.support.RewardRuntimeSettings;
import com.mkt.task.application.JdbcInstanceEventStore;
import com.mkt.task.application.MybatisTaskChildStore;
import com.mkt.task.application.MybatisTaskCrowdStore;
import com.mkt.task.application.MybatisTaskDefinitionStore;
import com.mkt.task.application.MybatisTaskInstanceStore;
import com.mkt.task.application.MybatisTaskMutexGroupStore;
import com.mkt.task.application.MybatisTaskProgressReportStore;
import com.mkt.task.application.MybatisTaskVersionSnapshotStore;
import com.mkt.task.application.TaskClaimAppService;
import com.mkt.task.application.TaskDefinitionAppService;
import com.mkt.task.application.TaskInstanceAppService;
import com.mkt.task.application.TaskPublishAppService;
import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.command.PublishCommand;
import com.mkt.task.command.TaskDefinitionSaveCommand;
import com.mkt.task.command.TaskStepCommand;
import com.mkt.task.command.TaskTransitionCommand;
import com.mkt.task.mapper.TaskCrowdItemMapper;
import com.mkt.task.mapper.TaskCrowdMapper;
import com.mkt.task.mapper.TaskDefinitionMapper;
import com.mkt.task.mapper.TaskInstanceMapper;
import com.mkt.task.mapper.TaskInstanceStepMapper;
import com.mkt.task.mapper.TaskMutexGroupMapper;
import com.mkt.task.mapper.TaskPlatformActionMapper;
import com.mkt.task.mapper.TaskProgressReportMapper;
import com.mkt.task.mapper.TaskStepMapper;
import com.mkt.task.mapper.TaskStepPlatformActionMapper;
import com.mkt.task.mapper.TaskStepTransitionMapper;
import com.mkt.task.mapper.TaskVersionSnapshotMapper;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.AlertWebhook;
import com.mkt.task.support.TaskSettings;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

/**
 * Topology A fixture for ScenarioMatrixIT: real RewardPort + internal step entries.
 */
final class ScenarioMatrixSupport implements AutoCloseable {

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final Clock clock = Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC);
    final TaskDefinitionAppService defs;
    final TaskPublishAppService publishes;
    final TaskClaimAppService claims;
    final TaskStepAppService steps;
    final TaskInstanceAppService instances;
    final PrizeAppService prizes;
    final RewardPortImpl rewards;
    final ItRisk risk = new ItRisk();
    final TogglePoints points;
    final AtomicLong seq = new AtomicLong(1);

    ScenarioMatrixSupport(MySQLContainer<?> mysql) throws Exception {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        hikari.setMaximumPoolSize(64);
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        SqlSessionTemplate sql = sqlSession(dataSource);
        TaskSettings settings = new TaskSettings();
        settings.setDailyLimitPerUser(100_000);
        MybatisTaskDefinitionStore definitions =
                new MybatisTaskDefinitionStore(sql.getMapper(TaskDefinitionMapper.class));
        MybatisTaskMutexGroupStore mutex =
                new MybatisTaskMutexGroupStore(sql.getMapper(TaskMutexGroupMapper.class));
        MybatisTaskCrowdStore crowds = new MybatisTaskCrowdStore(
                sql.getMapper(TaskCrowdMapper.class), sql.getMapper(TaskCrowdItemMapper.class));
        MybatisTaskVersionSnapshotStore snapshots =
                new MybatisTaskVersionSnapshotStore(sql.getMapper(TaskVersionSnapshotMapper.class));
        MybatisTaskInstanceStore instanceStore = new MybatisTaskInstanceStore(
                sql.getMapper(TaskInstanceMapper.class), sql.getMapper(TaskInstanceStepMapper.class));
        MybatisTaskChildStore children = new MybatisTaskChildStore(
                sql.getMapper(TaskStepMapper.class),
                sql.getMapper(TaskStepTransitionMapper.class),
                sql.getMapper(TaskPlatformActionMapper.class),
                sql.getMapper(TaskStepPlatformActionMapper.class));
        defs = new TaskDefinitionAppService(definitions, children, mutex, crowds, settings, clock);
        MybatisPrizeStore prizeStore = new MybatisPrizeStore(sql.getMapper(PrizeMapper.class));
        MybatisPrizeCategoryStore categories =
                new MybatisPrizeCategoryStore(sql.getMapper(PrizeCategoryMapper.class));
        MybatisStockLogStore stockLogs = new MybatisStockLogStore(sql.getMapper(StockLogMapper.class));
        MybatisGrantRecordStore grants = new MybatisGrantRecordStore(sql.getMapper(GrantRecordMapper.class));
        prizes = new PrizeAppService(prizeStore, categories, stockLogs, new SnapshotPrizeScanner(jdbc), clock);
        EventPublisher publisher = new EventPublisher(new JdbcOutboxStore(jdbc), OutboxProducer.PORTAL);
        GrantFailureLedger ledger = new GrantFailureLedger(grants, publisher, clock, new RewardGrantSettings(), txm);
        MybatisPointsStore pointsStore = new MybatisPointsStore(
                sql.getMapper(PntAccountMapper.class), sql.getMapper(PntTransactionMapper.class));
        PointsAppService ledgerPoints = new PointsAppService(pointsStore, txm, clock);
        points = new TogglePoints(new PointsPortImpl(ledgerPoints));
        FulfillmentService fulfillment = new FulfillmentService(
                points, grants, prizeStore, categories, publisher, new RewardRuntimeSettings(), clock);
        GrantAppService grant = new GrantAppService(
                prizeStore,
                categories,
                grants,
                stockLogs,
                activeUsers(),
                risk,
                publisher,
                ledger,
                fulfillment,
                null,
                clock);
        rewards = new RewardPortImpl(grant, prizeStore, ledgerPoints, grants);
        publishes = new TaskPublishAppService(
                definitions,
                mutex,
                snapshots,
                defs,
                rewards::prizeEnabled,
                clock,
                null,
                null,
                new AlertWebhook(""),
                jdbc,
                tx);
        MybatisTaskProgressReportStore progress =
                new MybatisTaskProgressReportStore(sql.getMapper(TaskProgressReportMapper.class));
        claims = new TaskClaimAppService(
                definitions,
                snapshots,
                instanceStore,
                crowds,
                mutex,
                activeUsers(),
                risk,
                publisher,
                rewards,
                clock,
                settings);
        steps = new TaskStepAppService(
                definitions,
                snapshots,
                instanceStore,
                crowds,
                progress,
                activeUsers(),
                risk,
                publisher,
                rewards,
                clock,
                settings);
        instances = new TaskInstanceAppService(
                instanceStore, snapshots, new JdbcInstanceEventStore(jdbc), publisher, risk, clock, tx);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
    }

    long nextUser() {
        return 10_000L + seq.getAndIncrement();
    }

    String nextCode(String prefix) {
        return prefix + "_" + seq.getAndIncrement();
    }

    long enablePoints(int stock) {
        String code = nextCode("pt");
        var created = tx.execute(status -> prizes.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                "POINTS",
                Map.of("points", 10),
                null,
                stock,
                0,
                0,
                null,
                null,
                null,
                "AUTO",
                null,
                null,
                null,
                null)));
        tx.executeWithoutResult(status -> prizes.enable(created.id(), new PrizeConfirmCommand(true)));
        return created.id();
    }

    void disablePrize(long prizeId) {
        tx.executeWithoutResult(status -> prizes.disable(prizeId, new PrizeConfirmCommand(true)));
    }

    long publish(String prefix, List<TaskStepCommand> stepDefs, List<TaskTransitionCommand> transitions) {
        String code = nextCode(prefix);
        TaskDefinitionSaveCommand command = new TaskDefinitionSaveCommand(
                null,
                code,
                code,
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
                stepDefs,
                transitions,
                List.of());
        long taskId = tx.execute(status -> defs.saveAggregate(command).id());
        tx.executeWithoutResult(status -> publishes.publish(taskId, new PublishCommand(null, null)));
        return taskId;
    }

    TaskStartResponse start(long taskId, long userId) {
        return tx.execute(status -> claims.start(taskId, userId, "203.0.113.1", null, "WEB"));
    }

    void click(long instanceId, String stepCode, long userId) {
        tx.executeWithoutResult(status -> steps.click(instanceId, stepCode, userId, "203.0.113.1", null, "WEB"));
    }

    void callback(long instanceId, String stepCode, String bizNo) {
        tx.executeWithoutResult(status -> steps.callback(
                new InternalCallbackCommand(instanceId, null, null, null, stepCode, bizNo)));
    }

    void progress(long instanceId, String stepCode, int value, String reportId) {
        tx.executeWithoutResult(status -> steps.progress(
                new InternalProgressCommand(instanceId, null, null, null, stepCode, value, reportId)));
    }

    void abandon(long instanceId, long userId) {
        tx.executeWithoutResult(status -> instances.abandonUser(instanceId, userId, "203.0.113.1", null));
    }

    void offline(long taskId) {
        tx.executeWithoutResult(status -> publishes.offline(taskId));
    }

    String instanceStatus(long instanceId) {
        return jdbc.queryForObject("SELECT status FROM task_instance WHERE id = ?", String.class, instanceId);
    }

    String stepStatus(long instanceId, String stepCode) {
        return jdbc.queryForObject(
                "SELECT status FROM task_instance_step WHERE instance_id = ? AND step_code = ?",
                String.class,
                instanceId,
                stepCode);
    }

    String skipReason(long instanceId, String stepCode) {
        return jdbc.queryForObject(
                "SELECT skip_reason FROM task_instance_step WHERE instance_id = ? AND step_code = ?",
                String.class,
                instanceId,
                stepCode);
    }

    Integer progressCurrent(long instanceId, String stepCode) {
        return jdbc.queryForObject(
                "SELECT progress_current FROM task_instance_step WHERE instance_id = ? AND step_code = ?",
                Integer.class,
                instanceId,
                stepCode);
    }

    long stepId(long instanceId, String stepCode) {
        Long id = jdbc.queryForObject(
                "SELECT id FROM task_instance_step WHERE instance_id = ? AND step_code = ?",
                Long.class,
                instanceId,
                stepCode);
        return id == null ? 0L : id;
    }

    Integer grantCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM rwd_grant_record", Integer.class);
    }

    String grantStatus(long stepId) {
        return jdbc.queryForObject(
                "SELECT status FROM rwd_grant_record WHERE grant_source = ? AND source_id = ?",
                String.class,
                GrantSource.TASK_STEP.name(),
                String.valueOf(stepId));
    }

    Integer remainingStock(long prizeId) {
        return jdbc.queryForObject("SELECT remaining_stock FROM rwd_prize WHERE id = ?", Integer.class, prizeId);
    }

    Integer instanceCount(long taskId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM task_instance WHERE task_id = ?", Integer.class, taskId);
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

    private static UserAttributes active() {
        return new UserAttributes(
                "GD", "user", "1", 1, List.of("vip"), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }

    @Override
    public void close() {
        UserContext.clear();
        if (dataSource != null) {
            dataSource.close();
        }
    }

    static final class ItRisk implements RiskCheckPort {
        volatile boolean rejectGrant;
        final List<RiskScene> hits = new CopyOnWriteArrayList<>();

        @Override
        public RiskVerdict check(RiskScene scene, RiskSubject subject) {
            hits.add(scene);
            if (rejectGrant && scene == RiskScene.GRANT) {
                return new RiskVerdict(RiskAction.REJECT);
            }
            return new RiskVerdict(RiskAction.PASS);
        }

        @Override
        public UserRiskSummary userSummary(long userId) {
            return new UserRiskSummary(0L, List.of());
        }
    }

    static final class TogglePoints implements PointsPort {
        private final PointsPort delegate;
        volatile boolean timeout;

        TogglePoints(PointsPort delegate) {
            this.delegate = delegate;
        }

        @Override
        public long earn(long userId, int amount, Instant expireAt, String sourceType, String sourceId) {
            if (timeout) {
                throw new RuntimeException("grant timeout");
            }
            return delegate.earn(userId, amount, expireAt, sourceType, sourceId);
        }
    }

    private static SqlSessionTemplate sqlSession(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        factoryBean.setMapperLocations(concat(
                resolver.getResources("classpath*:mapper/task/*.xml"),
                resolver.getResources("classpath*:mapper/reward/*.xml")));
        factoryBean.setTypeAliasesPackage("com.mkt.task.entity,com.mkt.reward.entity");
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
        configuration.addMapper(TaskProgressReportMapper.class);
        configuration.addMapper(PrizeCategoryMapper.class);
        configuration.addMapper(PrizeMapper.class);
        configuration.addMapper(StockLogMapper.class);
        configuration.addMapper(GrantRecordMapper.class);
        configuration.addMapper(PntAccountMapper.class);
        configuration.addMapper(PntTransactionMapper.class);
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

    private static Resource[] concat(Resource[] left, Resource[] right) {
        Resource[] merged = new Resource[left.length + right.length];
        System.arraycopy(left, 0, merged, 0, left.length);
        System.arraycopy(right, 0, merged, left.length, right.length);
        return merged;
    }
}
