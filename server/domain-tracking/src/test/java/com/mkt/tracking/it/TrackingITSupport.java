package com.mkt.tracking.it;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.config.GlobalConfig;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.mkt.infra.lock.PlatformLock;
import com.mkt.infra.outbox.AwaitOutboxDrain;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.EvtEventLogWriter;
import com.mkt.infra.outbox.JdbcOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.outbox.OutboxRelay;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.time.MutableClock;
import com.mkt.tracking.application.EventLogStore;
import com.mkt.tracking.application.EventMetadataStore;
import com.mkt.tracking.application.MybatisEventLogStore;
import com.mkt.tracking.application.MybatisEventMetadataStore;
import com.mkt.tracking.application.TrackBatchService;
import com.mkt.tracking.mapper.EvtEventLogMapper;
import com.mkt.tracking.mapper.EvtEventMetadataMapper;
import com.mkt.tracking.support.TrackDropCounters;
import com.mkt.tracking.support.TrackSettings;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

public final class TrackingITSupport {

    public final MutableClock clock;
    public final JdbcTemplate jdbc;
    public final JdbcOutboxStore outbox;
    public final EventPublisher publisher;
    public final OutboxRelay relay;
    public final AwaitOutboxDrain drain;
    public final TransactionTemplate tx;
    public final TrackBatchService batch;
    public final EventLogStore eventLogs;
    public final EventMetadataStore metadata;
    public final TrackSettings settings;
    public final TrackDropCounters drops;

    private TrackingITSupport(
            MutableClock clock,
            JdbcTemplate jdbc,
            JdbcOutboxStore outbox,
            EventPublisher publisher,
            OutboxRelay relay,
            AwaitOutboxDrain drain,
            TransactionTemplate tx,
            TrackBatchService batch,
            EventLogStore eventLogs,
            EventMetadataStore metadata,
            TrackSettings settings,
            TrackDropCounters drops) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.publisher = publisher;
        this.relay = relay;
        this.drain = drain;
        this.tx = tx;
        this.batch = batch;
        this.eventLogs = eventLogs;
        this.metadata = metadata;
        this.settings = settings;
        this.drops = drops;
    }

    public static MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("mkt_platform")
                .withUsername("mkt")
                .withPassword("mkt");
    }

    public static TrackingITSupport start(MySQLContainer<?> mysql, Instant now) {
        Flyway.configure()
                .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DriverManagerDataSource ds =
                new DriverManagerDataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        MutableClock clock = new MutableClock(now);
        JdbcOutboxStore outbox = new JdbcOutboxStore(jdbc);
        EventPublisher publisher = new EventPublisher(outbox, OutboxProducer.PORTAL);
        MemoryKeyValueStore kv = new MemoryKeyValueStore();
        PlatformLock locks = new PlatformLock(kv);
        EvtEventLogWriter evtWriter = new EvtEventLogWriter(jdbc, clock);
        OutboxRelay relay = new OutboxRelay(outbox, OutboxProducer.PORTAL, locks, clock, List.of(evtWriter));
        DataSourceTransactionManager txm = new DataSourceTransactionManager(ds);
        SqlSessionFactory factory = mybatis(ds);
        SqlSessionTemplate sqlSession = new SqlSessionTemplate(factory);
        EventLogStore eventLogs = new MybatisEventLogStore(sqlSession.getMapper(EvtEventLogMapper.class));
        EventMetadataStore metadata = new MybatisEventMetadataStore(sqlSession.getMapper(EvtEventMetadataMapper.class));
        TrackSettings settings = new TrackSettings();
        TrackDropCounters drops = new TrackDropCounters();
        TrackBatchService batch = new TrackBatchService(
                eventLogs, metadata, new SlidingWindowRateLimiter(kv, clock), settings, drops, clock);
        return new TrackingITSupport(
                clock,
                jdbc,
                outbox,
                publisher,
                relay,
                new AwaitOutboxDrain(outbox, OutboxProducer.PORTAL, relay, clock),
                new TransactionTemplate(txm),
                batch,
                eventLogs,
                metadata,
                settings,
                drops);
    }

    private static SqlSessionFactory mybatis(javax.sql.DataSource dataSource) {
        try {
            MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setTransactionFactory(new SpringManagedTransactionFactory());
            factoryBean.setTypeAliasesPackage("com.mkt.tracking.entity");
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(EvtEventLogMapper.class);
            configuration.addMapper(EvtEventMetadataMapper.class);
            factoryBean.setConfiguration(configuration);
            GlobalConfig globalConfig = new GlobalConfig();
            GlobalConfig.DbConfig dbConfig = new GlobalConfig.DbConfig();
            dbConfig.setIdType(IdType.ASSIGN_ID);
            globalConfig.setDbConfig(dbConfig);
            factoryBean.setGlobalConfig(globalConfig);
            SqlSessionFactory factory = factoryBean.getObject();
            if (factory == null) {
                throw new IllegalStateException("SqlSessionFactory is null");
            }
            return factory;
        } catch (Exception ex) {
            throw new IllegalStateException("tracking mybatis factory", ex);
        }
    }
}
