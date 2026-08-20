package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.identity.command.DictEntryCreateCommand;
import com.mkt.identity.command.DictEntryUpdateCommand;
import com.mkt.identity.command.DictTypeCreateCommand;
import com.mkt.identity.command.DictTypeUpdateCommand;
import com.mkt.identity.entity.DictEntryEntity;
import com.mkt.identity.entity.DictTypeEntity;
import com.mkt.identity.mapper.DictEntryMapper;
import com.mkt.identity.mapper.DictTypeMapper;
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
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class DictAppServiceTest {

    private final DictTypeMapper types = Mockito.mock(DictTypeMapper.class);
    private final DictEntryMapper entries = Mockito.mock(DictEntryMapper.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private PlatformCache cache;
    private DictAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache = new TwoLevelPlatformCache(new MemoryKeyValueStore());
        service = new DictAppService(
                types,
                entries,
                cache,
                new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                new MutableClock(Instant.parse("2026-08-19T12:00:00Z")));
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void createTypeAndListEntriesUsesCacheThenEvictsOnDisable() {
        when(types.insert(any(DictTypeEntity.class))).thenAnswer(invocation -> {
            DictTypeEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return 1;
        });
        DictTypeEntity created = service.createType(new DictTypeCreateCommand("color", "颜色", null));
        assertThat(created.getCode()).isEqualTo("color");
        when(types.insert(any(DictTypeEntity.class))).thenThrow(new DuplicateKeyException("uk"));
        assertThatThrownBy(() -> service.createType(new DictTypeCreateCommand("color", "颜色", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SystemErrorCodes.DICT_DUPLICATE_CODE);

        DictTypeEntity type = new DictTypeEntity();
        type.setId(3L);
        type.setCode("color");
        type.setStatus("ENABLED");
        when(types.getByCode("color")).thenReturn(type);
        DictEntryEntity red = new DictEntryEntity();
        red.setLabel("红");
        red.setValue("red");
        red.setSort(1);
        when(entries.listEnabledByTypeId(3L)).thenReturn(List.of(red));
        assertThat(service.listEnabledEntries("color")).extracting(item -> item.value()).containsExactly("red");
        assertThat(service.listEnabledEntries("color")).hasSize(1);
        verify(entries, Mockito.times(1)).listEnabledByTypeId(3L);

        type.setStatus("DISABLED");
        when(types.selectById(3L)).thenReturn(type);
        when(types.updateById(any(DictTypeEntity.class))).thenReturn(1);
        service.updateType(3L, new DictTypeUpdateCommand("颜色", "DISABLED", null));
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        assertThat(service.listEnabledEntries("color")).isEmpty();
    }

    @Test
    void deleteTypeRejectedWhenEntriesExist() {
        DictTypeEntity type = new DictTypeEntity();
        type.setId(1L);
        type.setCode("province");
        type.setStatus("ENABLED");
        when(types.selectById(1L)).thenReturn(type);
        when(entries.countByTypeId(1L)).thenReturn(2);
        assertThatThrownBy(() -> service.deleteType(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
        verify(types, never()).deleteById(1L);
    }

    @Test
    void entryDuplicateValueRejected() {
        DictTypeEntity type = new DictTypeEntity();
        type.setId(8L);
        type.setCode("tag");
        type.setStatus("ENABLED");
        when(types.getByCode("tag")).thenReturn(type);
        when(entries.insert(any(DictEntryEntity.class))).thenThrow(new DuplicateKeyException("uk"));
        assertThatThrownBy(
                        () -> service.createEntry(new DictEntryCreateCommand("tag", "热门", "hot", 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(SystemErrorCodes.DICT_ENTRY_DUPLICATE_VALUE);
        assertThatThrownBy(
                        () -> service.createEntry(new DictEntryCreateCommand("tag", "坏", "bad value", 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void pageTypesAndUpdateEntry() {
        DictTypeEntity type = new DictTypeEntity();
        type.setId(1L);
        type.setCode("province");
        type.setName("省份");
        type.setStatus("ENABLED");
        when(types.countAll()).thenReturn(1L);
        when(types.listPage(0L, 20)).thenReturn(List.of(type));
        assertThat(service.pageTypes(PageQuery.of(1, 20)).total()).isEqualTo(1);

        DictEntryEntity entry = new DictEntryEntity();
        entry.setId(9L);
        entry.setTypeId(1L);
        entry.setValue("GD");
        when(entries.selectById(9L)).thenReturn(entry);
        when(types.selectById(1L)).thenReturn(type);
        when(entries.updateById(any(DictEntryEntity.class))).thenReturn(1);
        service.updateEntry(9L, new DictEntryUpdateCommand("广东", "GD", 2, "ENABLED", null));
        assertThat(entry.getLabel()).isEqualTo("广东");
        assertThat(entry.getSort()).isEqualTo(2);
    }
}
