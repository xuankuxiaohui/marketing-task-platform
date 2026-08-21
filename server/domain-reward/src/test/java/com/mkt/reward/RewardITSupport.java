package com.mkt.reward;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.contract.AccountStatus;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.contract.UserRiskSummary;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.reward.application.ClaimAppService;
import com.mkt.reward.application.FulfillmentService;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.application.GrantFailureLedger;
import com.mkt.reward.application.MybatisGrantRecordStore;
import com.mkt.reward.application.MybatisPointsStore;
import com.mkt.reward.application.MybatisPrizeCategoryStore;
import com.mkt.reward.application.MybatisPrizeStore;
import com.mkt.reward.application.MybatisReconBatchStore;
import com.mkt.reward.application.MybatisReconItemStore;
import com.mkt.reward.application.MybatisStockLogStore;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.application.PrizeAppService;
import com.mkt.reward.application.PrizeCategoryAppService;
import com.mkt.reward.application.PrizeStockService;
import com.mkt.reward.application.ReconAppService;
import com.mkt.reward.application.SnapshotPrizeScanner;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.mapper.GrantRecordMapper;
import com.mkt.reward.mapper.PntAccountMapper;
import com.mkt.reward.mapper.PntTransactionMapper;
import com.mkt.reward.mapper.PrizeCategoryMapper;
import com.mkt.reward.mapper.PrizeMapper;
import com.mkt.reward.mapper.ReconBatchMapper;
import com.mkt.reward.mapper.ReconItemMapper;
import com.mkt.reward.mapper.StockLogMapper;
import com.mkt.reward.support.RewardGrantSettings;
import com.mkt.reward.support.RewardRuntimeSettings;
import com.mkt.reward.testsupport.RecordingPointsPort;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

final class RewardITSupport implements AutoCloseable {

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final PrizeAppService prizes;
    final PrizeCategoryAppService categories;
    final PrizeStockService stock;
    final GrantAppService grant;
    final ClaimAppService claims;
    final FulfillmentService fulfillment;
    final ReconAppService recon;
    final RecordingPointsPort points;
    final PointsAppService pointsLedger;
    final ItRisk risk;
    final Clock clock;
    final RewardRuntimeSettings runtime;

    RewardITSupport(MySQLContainer<?> mysql) throws Exception {
        this(mysql, Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
    }

    RewardITSupport(MySQLContainer<?> mysql, Clock clock) throws Exception {
        this.clock = clock;
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        hikari.setMaximumPoolSize(64);
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sql = sqlSession(dataSource);
        MybatisPrizeCategoryStore categoryStore =
                new MybatisPrizeCategoryStore(sql.getMapper(PrizeCategoryMapper.class));
        MybatisPrizeStore prizeStore = new MybatisPrizeStore(sql.getMapper(PrizeMapper.class));
        MybatisStockLogStore logs = new MybatisStockLogStore(sql.getMapper(StockLogMapper.class));
        MybatisGrantRecordStore grants = new MybatisGrantRecordStore(sql.getMapper(GrantRecordMapper.class));
        MybatisReconBatchStore batches = new MybatisReconBatchStore(sql.getMapper(ReconBatchMapper.class));
        MybatisReconItemStore reconItems = new MybatisReconItemStore(sql.getMapper(ReconItemMapper.class));
        SnapshotPrizeScanner snapshots = new SnapshotPrizeScanner(jdbc);
        categories = new PrizeCategoryAppService(categoryStore, prizeStore, clock);
        prizes = new PrizeAppService(prizeStore, categoryStore, logs, snapshots, clock);
        stock = new PrizeStockService(prizeStore, categoryStore, grants, logs, clock);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        EventPublisher events = new EventPublisher(new MemoryOutboxStore(clock), OutboxProducer.PORTAL);
        RewardGrantSettings settings = new RewardGrantSettings();
        runtime = new RewardRuntimeSettings();
        GrantFailureLedger ledger = new GrantFailureLedger(grants, events, clock, settings, txm);
        points = new RecordingPointsPort();
        MybatisPointsStore pointsStore = new MybatisPointsStore(
                sql.getMapper(PntAccountMapper.class), sql.getMapper(PntTransactionMapper.class));
        pointsLedger = new PointsAppService(pointsStore, txm, clock);
        fulfillment = new FulfillmentService(points, grants, prizeStore, categoryStore, events, runtime, clock);
        risk = new ItRisk();
        grant = new GrantAppService(
                prizeStore,
                categoryStore,
                grants,
                logs,
                new ItUsers(),
                risk,
                events,
                ledger,
                fulfillment,
                null,
                clock);
        claims = new ClaimAppService(
                grants, prizeStore, categoryStore, fulfillment, (PlatformLock) null, runtime, clock);
        recon = new ReconAppService(
                batches, reconItems, grants, prizeStore, categoryStore, grant, fulfillment, runtime, clock);
    }

    long enablePoints(String code, int totalStock, int pointsAmount) {
        var created = tx.execute(status -> prizes.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                "POINTS",
                Map.of("points", pointsAmount),
                null,
                totalStock,
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

    static final class ItUsers implements UserAttributePort {
        @Override
        public UserAttributes attributes(long userId) {
            return new UserAttributes("GD", "user", "1", 1, java.util.List.of(), Instant.EPOCH, AccountStatus.ACTIVE);
        }

        @Override
        public UserAttributes lockAndGet(long userId) {
            return attributes(userId);
        }
    }

    static final class ItRisk implements RiskCheckPort {
        volatile boolean reject;

        @Override
        public RiskVerdict check(RiskScene scene, RiskSubject subject) {
            return new RiskVerdict(reject ? RiskAction.REJECT : RiskAction.PASS);
        }

        @Override
        public UserRiskSummary userSummary(long userId) {
            return new UserRiskSummary(0L, java.util.List.of());
        }
    }

    long enableAlipay(String code, int totalStock, int dailyLimit, int totalLimit, int faceFen) {
        var created = tx.execute(status -> prizes.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                "ALIPAY_RED",
                Map.of("faceFen", faceFen),
                null,
                totalStock,
                dailyLimit,
                totalLimit,
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

    long enableManualCoupon(String code, int totalStock, int expireHours) {
        var created = tx.execute(status -> prizes.create(new PrizeSaveCommand(
                code,
                code,
                null,
                null,
                "COUPON",
                Map.of(),
                null,
                totalStock,
                0,
                0,
                null,
                null,
                null,
                "MANUAL",
                null,
                expireHours,
                null,
                null)));
        tx.executeWithoutResult(status -> prizes.enable(created.id(), new PrizeConfirmCommand(true)));
        return created.id();
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    private static SqlSessionTemplate sqlSession(DataSource dataSource) throws Exception {
        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/reward/*.xml"));
        factoryBean.setTypeAliasesPackage("com.mkt.reward.entity");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PrizeCategoryMapper.class);
        configuration.addMapper(PrizeMapper.class);
        configuration.addMapper(StockLogMapper.class);
        configuration.addMapper(GrantRecordMapper.class);
        configuration.addMapper(ReconBatchMapper.class);
        configuration.addMapper(ReconItemMapper.class);
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
}
