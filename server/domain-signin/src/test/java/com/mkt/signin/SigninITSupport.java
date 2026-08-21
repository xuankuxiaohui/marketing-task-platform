package com.mkt.signin;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.signin.application.MybatisSigninActivityStore;
import com.mkt.signin.application.MybatisSigninRecordStore;
import com.mkt.signin.application.MybatisSigninSnapshotStore;
import com.mkt.signin.application.SigninAdminAppService;
import com.mkt.signin.application.SigninPortalAppService;
import com.mkt.signin.command.SigninActivitySaveCommand;
import com.mkt.signin.command.SigninPublishCommand;
import com.mkt.signin.command.SigninTierCommand;
import com.mkt.signin.mapper.SgnActivityMapper;
import com.mkt.signin.mapper.SgnActivitySnapshotMapper;
import com.mkt.signin.mapper.SgnRecordMapper;
import com.mkt.signin.support.SigninSettings;
import com.mkt.signin.testsupport.RecordingRewardPort;
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
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

final class SigninITSupport implements AutoCloseable {

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final SigninAdminAppService admin;
    final SigninPortalAppService portal;
    final RecordingRewardPort rewards;
    final SigninSettings settings;
    final Clock clock;

    SigninITSupport(MySQLContainer<?> mysql) throws Exception {
        this(mysql, Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC));
    }

    SigninITSupport(MySQLContainer<?> mysql, Clock clock) throws Exception {
        this.clock = clock;
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        hikari.setMaximumPoolSize(64);
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sql = sqlSession(dataSource);
        MybatisSigninActivityStore activities =
                new MybatisSigninActivityStore(sql.getMapper(SgnActivityMapper.class));
        MybatisSigninSnapshotStore snapshots =
                new MybatisSigninSnapshotStore(sql.getMapper(SgnActivitySnapshotMapper.class));
        MybatisSigninRecordStore records = new MybatisSigninRecordStore(sql.getMapper(SgnRecordMapper.class));
        rewards = new RecordingRewardPort();
        settings = new SigninSettings();
        admin = new SigninAdminAppService(activities, snapshots, records, rewards, clock);
        portal = new SigninPortalAppService(activities, snapshots, records, rewards, settings, clock);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    long publishDaily() {
        long id = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L), new SigninTierCommand(3, 11L))))
                .id();
        admin.publish(id, new SigninPublishCommand(true, false));
        return id;
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
                .getResources("classpath*:mapper/signin/*.xml"));
        factoryBean.setTypeAliasesPackage("com.mkt.signin.entity");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(SgnActivityMapper.class);
        configuration.addMapper(SgnActivitySnapshotMapper.class);
        configuration.addMapper(SgnRecordMapper.class);
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
