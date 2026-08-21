package com.mkt.ad;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.ad.application.AdAdminAppService;
import com.mkt.ad.application.AdFreqStore;
import com.mkt.ad.application.AdPortalAppService;
import com.mkt.ad.application.MybatisAdMaterialStore;
import com.mkt.ad.application.MybatisAdPlacementStore;
import com.mkt.ad.application.MybatisAdPositionStore;
import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.mapper.AdMaterialMapper;
import com.mkt.ad.mapper.AdPositionMapper;
import com.mkt.ad.mapper.AdPositionMaterialMapper;
import com.mkt.ad.support.AdSettings;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

final class AdITSupport implements AutoCloseable {

    final HikariDataSource dataSource;
    final JdbcTemplate jdbc;
    final TransactionTemplate tx;
    final AdAdminAppService admin;
    final AdPortalAppService portal;
    final AdSettings settings;
    final MemoryKeyValueStore kv;
    final Clock clock;

    AdITSupport(MySQLContainer<?> mysql) throws Exception {
        this(mysql, Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC));
    }

    AdITSupport(MySQLContainer<?> mysql, Clock clock) throws Exception {
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
        MybatisAdPositionStore positions = new MybatisAdPositionStore(sql.getMapper(AdPositionMapper.class));
        MybatisAdMaterialStore materials = new MybatisAdMaterialStore(sql.getMapper(AdMaterialMapper.class));
        MybatisAdPlacementStore placements =
                new MybatisAdPlacementStore(sql.getMapper(AdPositionMaterialMapper.class));
        kv = new MemoryKeyValueStore();
        TwoLevelPlatformCache cache = new TwoLevelPlatformCache(kv);
        settings = new AdSettings();
        admin = new AdAdminAppService(positions, materials, placements, cache);
        portal = new AdPortalAppService(
                positions, materials, placements, cache, new AdFreqStore(kv), settings, clock);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(dataSource);
        tx = new TransactionTemplate(txm);
    }

    long publishImage(String code, int weight) {
        long positionId = admin.savePosition(
                        new AdPositionSaveCommand(null, code, code, AdForms.IMAGE, List.of("WEB"), "ENABLED"))
                .id();
        long materialId = admin.saveMaterial(new AdMaterialSaveCommand(
                        null,
                        code + "-m",
                        null,
                        "https://cdn.example/" + code + ".png",
                        "NONE",
                        Map.of(),
                        weight,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        "ENABLED"))
                .id();
        admin.bind(
                positionId,
                new AdPlacementSaveCommand(
                        materialId,
                        weight,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        List.of(),
                        "NONE",
                        null,
                        null,
                        "ENABLED"));
        return materialId;
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
        factoryBean.setMapperLocations(
                new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/ad/*.xml"));
        factoryBean.setTypeAliasesPackage("com.mkt.ad.entity");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(AdPositionMapper.class);
        configuration.addMapper(AdMaterialMapper.class);
        configuration.addMapper(AdPositionMaterialMapper.class);
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
