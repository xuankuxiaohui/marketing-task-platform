package com.mkt.activity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.activity.application.ActivityAdminAppService;
import com.mkt.activity.application.ActivityPortalAppService;
import com.mkt.activity.application.MybatisActivityActivityStore;
import com.mkt.activity.application.MybatisActivityParticipationStore;
import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.mapper.ActActivityMapper;
import com.mkt.activity.mapper.ActParticipationMapper;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.activity.testsupport.RecordingRewardPort;
import com.mkt.activity.testsupport.StubUserAttributePort;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
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

final class ActivityITSupport implements AutoCloseable {

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final ActivityAdminAppService admin;
    final ActivityPortalAppService portal;
    final RecordingRewardPort rewards;
    final StubUserAttributePort users;
    final Clock clock;

    ActivityITSupport(MySQLContainer<?> mysql) throws Exception {
        this(mysql, Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC));
    }

    ActivityITSupport(MySQLContainer<?> mysql, Clock clock) throws Exception {
        this.clock = clock;
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(mysql.getJdbcUrl());
        hikari.setUsername(mysql.getUsername());
        hikari.setPassword(mysql.getPassword());
        hikari.setMaximumPoolSize(128);
        dataSource = new HikariDataSource(hikari);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        SqlSessionTemplate sql = sqlSession(dataSource);
        MybatisActivityActivityStore activities =
                new MybatisActivityActivityStore(sql.getMapper(ActActivityMapper.class));
        MybatisActivityParticipationStore participations =
                new MybatisActivityParticipationStore(sql.getMapper(ActParticipationMapper.class));
        rewards = new RecordingRewardPort();
        users = new StubUserAttributePort();
        admin = new ActivityAdminAppService(activities, participations, rewards, new ActivitySettings(), clock);
        portal = new ActivityPortalAppService(activities, participations, rewards, users, clock);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    long publishLimited(int globalDaily) {
        long id = admin.save(new ActivitySaveCommand(
                        null,
                        "quota_act",
                        "限量活动",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        "<p>limited</p>",
                        new ActivityGrayCommand("NONE", null),
                        List.of(),
                        10L,
                        List.of(),
                        List.of(),
                        false,
                        7,
                        null,
                        null,
                        globalDaily,
                        List.of()))
                .id();
        admin.publish(id, new ActivityPublishCommand(true, false));
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
                .getResources("classpath*:mapper/activity/*.xml"));
        factoryBean.setTypeAliasesPackage("com.mkt.activity.entity");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(ActActivityMapper.class);
        configuration.addMapper(ActParticipationMapper.class);
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
