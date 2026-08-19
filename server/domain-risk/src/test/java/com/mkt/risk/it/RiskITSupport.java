package com.mkt.risk.it;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.JdbcOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.InfraRedisProperties;
import com.mkt.infra.redis.KeyValueStore;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.infra.redis.RedissonFactory;
import com.mkt.infra.redis.RedissonKeyValueStore;
import com.mkt.kernel.time.MutableClock;
import com.mkt.risk.application.RiskAuditAppender;
import com.mkt.risk.application.RiskCaseAppService;
import com.mkt.risk.application.RiskHandleLogStore;
import com.mkt.risk.application.RiskListAppService;
import com.mkt.risk.application.RiskListItemStore;
import com.mkt.risk.support.ListLookup;
import com.mkt.risk.support.RiskListProjection;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.redisson.api.RedissonClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public final class RiskITSupport implements AutoCloseable {

    public final MutableClock clock;
    public final JdbcTemplate jdbc;
    public final KeyValueStore kv;
    public final JdbcOutboxStore outbox;
    public final TransactionTemplate tx;
    public final RiskListAppService lists;
    public final RiskCaseAppService cases;
    public final ListLookup lookup;
    public final RiskListProjection projection;
    public final RiskListItemStore listStore;
    public final RiskHandleLogStore handleStore;
    private final RedissonClient redisson;

    private RiskITSupport(
            MutableClock clock,
            JdbcTemplate jdbc,
            KeyValueStore kv,
            JdbcOutboxStore outbox,
            TransactionTemplate tx,
            RiskListAppService lists,
            RiskCaseAppService cases,
            ListLookup lookup,
            RiskListProjection projection,
            RiskListItemStore listStore,
            RiskHandleLogStore handleStore,
            RedissonClient redisson) {
        this.clock = clock;
        this.jdbc = jdbc;
        this.kv = kv;
        this.outbox = outbox;
        this.tx = tx;
        this.lists = lists;
        this.cases = cases;
        this.lookup = lookup;
        this.projection = projection;
        this.listStore = listStore;
        this.handleStore = handleStore;
        this.redisson = redisson;
    }

    public static MySQLContainer<?> mysql() {
        return new MySQLContainer<>("mysql:8.0")
                .withDatabaseName("mkt_platform")
                .withUsername("mkt")
                .withPassword("mkt");
    }

    public static GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
    }

    public static RiskITSupport start(MySQLContainer<?> mysql, Instant now) {
        return start(mysql, now, new MemoryKeyValueStore(), null);
    }

    public static RiskITSupport start(MySQLContainer<?> mysql, GenericContainer<?> redis, Instant now) {
        InfraRedisProperties props =
                new InfraRedisProperties(redis.getHost(), redis.getMappedPort(6379), "", 2);
        RedissonClient client = RedissonFactory.create(props);
        return start(mysql, now, new RedissonKeyValueStore(client), client);
    }

    private static RiskITSupport start(
            MySQLContainer<?> mysql, Instant now, KeyValueStore kv, RedissonClient redisson) {
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
        EventPublisher publisher = new EventPublisher(outbox, OutboxProducer.ADMIN);
        RiskMybatisStores mybatis = RiskMybatisStores.create(ds);
        RiskListItemStore listStore = mybatis.lists;
        RiskHandleLogStore handleStore = mybatis.handles;
        RiskListProjection projection = new RiskListProjection(kv, listStore, clock);
        RiskAuditAppender audit = new RiskAuditAppender(publisher);
        RiskListAppService lists = new RiskListAppService(listStore, projection, audit, clock);
        RiskCaseAppService cases =
                new RiskCaseAppService(mybatis.hits, handleStore, listStore, projection, audit, clock);
        DataSourceTransactionManager txm = new DataSourceTransactionManager(ds);
        return new RiskITSupport(
                clock,
                jdbc,
                kv,
                outbox,
                new TransactionTemplate(txm),
                lists,
                cases,
                new ListLookup(projection, clock),
                projection,
                listStore,
                handleStore,
                redisson);
    }

    @Override
    public void close() {
        if (redisson != null) {
            redisson.shutdown();
        }
    }
}
