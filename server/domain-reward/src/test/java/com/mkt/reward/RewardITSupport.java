package com.mkt.reward;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.reward.application.MybatisGrantRecordStore;
import com.mkt.reward.application.MybatisPrizeCategoryStore;
import com.mkt.reward.application.MybatisPrizeStore;
import com.mkt.reward.application.MybatisStockLogStore;
import com.mkt.reward.application.PrizeAppService;
import com.mkt.reward.application.PrizeCategoryAppService;
import com.mkt.reward.application.PrizeStockService;
import com.mkt.reward.application.SnapshotPrizeScanner;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.mapper.GrantRecordMapper;
import com.mkt.reward.mapper.PrizeCategoryMapper;
import com.mkt.reward.mapper.PrizeMapper;
import com.mkt.reward.mapper.StockLogMapper;
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
    final Clock clock;

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
        SnapshotPrizeScanner snapshots = new SnapshotPrizeScanner(jdbc);
        categories = new PrizeCategoryAppService(categoryStore, prizeStore, clock);
        prizes = new PrizeAppService(prizeStore, categoryStore, logs, snapshots, clock);
        stock = new PrizeStockService(prizeStore, categoryStore, grants, logs, clock);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
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
