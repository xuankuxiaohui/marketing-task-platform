package com.mkt.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.identity.application.IdentityAuditAppender;
import com.mkt.identity.command.ConfigCreateCommand;
import com.mkt.identity.command.ConfigUpdateCommand;
import com.mkt.identity.entity.SysConfigEntity;
import com.mkt.identity.query.ConfigQuery;
import com.mkt.identity.support.SystemErrorCodes;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.json.JsonUtil;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

class ConfigAppServiceTest {

    private final SysConfigMapper mapper = Mockito.mock(SysConfigMapper.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private PlatformCache cache;
    private ConfigAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache = new TwoLevelPlatformCache(new MemoryKeyValueStore());
        service = new ConfigAppService(
                mapper,
                cache,
                new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                new MutableClock(Instant.parse("2026-08-19T12:00:00Z")));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void createRejectsTypeMismatchAndMasksOnPage() {
        assertThatThrownBy(
                        () -> service.create(
                                new ConfigCreateCommand("k", "g", "nope", "NUMBER", false, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SystemErrorCodes.CONFIG_TYPE_MISMATCH);

        when(mapper.insert(any(SysConfigEntity.class))).thenAnswer(invocation -> {
            SysConfigEntity entity = invocation.getArgument(0);
            entity.setId(4L);
            return 1;
        });
        SysConfigEntity created =
                service.create(new ConfigCreateCommand("secret.token", "sec", "plain", "STRING", true, null));
        assertThat(created.maskedFlag()).isTrue();

        created.setId(4L);
        created.setUpdatedAt(java.time.LocalDateTime.of(2026, 8, 19, 12, 0));
        when(mapper.countPage(null, null)).thenReturn(1L);
        when(mapper.listPage(null, null, 0L, 20)).thenReturn(List.of(created));
        var page = service.page(new ConfigQuery(null, null, PageQuery.of(1, 20)));
        assertThat(page.records().get(0).configValue()).isEqualTo("******");
        assertThat(page.records().get(0).masked()).isTrue();
    }

    @Test
    void updateMissingValueKeepsOriginalAndNullValueRejected() {
        SysConfigEntity existing = new SysConfigEntity();
        existing.setId(1L);
        existing.setConfigKey("ratelimit.login");
        existing.setConfigGroup("auth");
        existing.setConfigValue("10");
        existing.setValueType("NUMBER");
        existing.setMasked(0);
        existing.setStatus("ENABLED");
        when(mapper.getByKey("ratelimit.login")).thenReturn(existing);
        when(mapper.updateById(any(SysConfigEntity.class))).thenReturn(1);

        ConfigUpdateCommand keep = JsonUtil.fromJson("{\"status\":\"DISABLED\"}", ConfigUpdateCommand.class);
        service.update("ratelimit.login", keep);
        assertThat(existing.getConfigValue()).isEqualTo("10");
        assertThat(existing.getStatus()).isEqualTo("DISABLED");

        ConfigUpdateCommand empty = JsonUtil.fromJson("{\"value\":\"\"}", ConfigUpdateCommand.class);
        assertThatThrownBy(() -> service.update("ratelimit.login", empty))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);

        ConfigUpdateCommand nil = JsonUtil.fromJson("{\"value\":null}", ConfigUpdateCommand.class);
        assertThatThrownBy(() -> service.update("ratelimit.login", nil))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);

        JsonNode five = JsonUtil.readTree("5");
        service.update("ratelimit.login", new ConfigUpdateCommand(null, null, null, null, null, five));
        assertThat(existing.getConfigValue()).isEqualTo("5");
        verify(mapper, Mockito.times(2)).updateById(existing);
        assertThat(keep.value()).isNull();
        assertThat(nil.value().isNull()).isTrue();
    }

    @Test
    void maskedDisplaySentinelKeepsStoredValue() {
        SysConfigEntity existing = new SysConfigEntity();
        existing.setId(3L);
        existing.setConfigKey("secret.token");
        existing.setConfigGroup("sec");
        existing.setConfigValue("plain-secret");
        existing.setValueType("STRING");
        existing.setMasked(1);
        existing.setStatus("ENABLED");
        when(mapper.getByKey("secret.token")).thenReturn(existing);
        when(mapper.updateById(any(SysConfigEntity.class))).thenReturn(1);

        service.update(
                "secret.token",
                JsonUtil.fromJson("{\"status\":\"DISABLED\",\"value\":\"******\"}", ConfigUpdateCommand.class));
        assertThat(existing.getConfigValue()).isEqualTo("plain-secret");
        assertThat(existing.getStatus()).isEqualTo("DISABLED");

        JsonNode next = JsonUtil.readTree("\"rotated\"");
        service.update("secret.token", new ConfigUpdateCommand(null, null, null, null, null, next));
        assertThat(existing.getConfigValue()).isEqualTo("rotated");
    }

    @Test
    void updateEvictsConfigCache() {
        SysConfigEntity existing = new SysConfigEntity();
        existing.setId(2L);
        existing.setConfigKey("k");
        existing.setConfigValue("1");
        existing.setValueType("NUMBER");
        existing.setMasked(0);
        existing.setStatus("ENABLED");
        when(mapper.getByKey("k")).thenReturn(existing);
        when(mapper.updateById(any(SysConfigEntity.class))).thenReturn(1);
        cache.put(CacheNamespace.CONFIG, "k", "1");
        service.update("k", JsonUtil.fromJson("{\"value\":\"2\"}", ConfigUpdateCommand.class));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        assertThat(cache.get(CacheNamespace.CONFIG, "k", String.class, () -> "fresh")).isEqualTo("fresh");
    }
}
