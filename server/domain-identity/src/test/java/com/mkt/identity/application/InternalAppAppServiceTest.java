package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.identity.command.InternalAppCreateCommand;
import com.mkt.identity.entity.InternalAppEntity;
import com.mkt.identity.mapper.InternalAppMapper;
import com.mkt.identity.response.InternalAppCreatedResponse;
import com.mkt.identity.response.InternalAppRotateResponse;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class InternalAppAppServiceTest {

    static final String TEST_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private final InternalAppMapper apps = Mockito.mock(InternalAppMapper.class);
    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private InternalAppSecretCipher cipher;
    private InternalAppAppService service;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cipher = new InternalAppSecretCipher(TEST_KEY);
        clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
        service = new InternalAppAppService(
                apps, cipher, new IdentityAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)), clock);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void createPersistsCipherAndReturnsPlainSecretOnce() {
        when(apps.insert(any(InternalAppEntity.class))).thenAnswer(invocation -> {
            InternalAppEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return 1;
        });
        InternalAppCreatedResponse created = service.create(new InternalAppCreateCommand("合作方"));
        assertThat(created.id()).isEqualTo(3L);
        assertThat(created.appId()).hasSize(16);
        assertThat(created.secret()).hasSize(43).matches("[A-Za-z0-9]{43}");
        ArgumentCaptor<InternalAppEntity> captor = ArgumentCaptor.forClass(InternalAppEntity.class);
        verify(apps).insert(captor.capture());
        InternalAppEntity stored = captor.getValue();
        assertThat(stored.getSecretCipher()).isNotEqualTo(created.secret());
        assertThat(cipher.decrypt(stored.getSecretCipher())).isEqualTo(created.secret());
        assertThat(outbox.all()).isNotEmpty();
    }

    @Test
    void rotateKeepsPreviousSecretFor24Hours() {
        InternalAppEntity existing = new InternalAppEntity();
        existing.setId(3L);
        existing.setAppId("appidabcdefghij");
        existing.setAppName("合作方");
        existing.setSecretCipher(cipher.encrypt("oldSecretoldSecretoldSecretoldSecretoldSecre"));
        existing.setStatus("ENABLED");
        when(apps.selectById(3L)).thenReturn(existing);
        when(apps.updateById(any(InternalAppEntity.class))).thenReturn(1);
        InternalAppRotateResponse rotated = service.rotate(3L);
        assertThat(rotated.secret()).hasSize(43);
        assertThat(rotated.prevExpireAt()).isEqualTo(Instant.parse("2026-08-20T12:00:00Z"));
        assertThat(cipher.decrypt(existing.getPrevSecretCipher()))
                .isEqualTo("oldSecretoldSecretoldSecretoldSecretoldSecre");
        assertThat(cipher.decrypt(existing.getSecretCipher())).isEqualTo(rotated.secret());
        List<String> live = cipher.decryptActive(existing, Instant.parse("2026-08-20T11:59:59Z"));
        assertThat(live).containsExactly(rotated.secret(), "oldSecretoldSecretoldSecretoldSecretoldSecre");
        assertThat(cipher.decryptActive(existing, Instant.parse("2026-08-20T12:00:00Z")))
                .containsExactly(rotated.secret());
    }

    @Test
    void disableEnableAndMissingId() {
        InternalAppEntity existing = new InternalAppEntity();
        existing.setId(3L);
        existing.setAppId("appidabcdefghij");
        existing.setStatus("ENABLED");
        when(apps.selectById(3L)).thenReturn(existing);
        when(apps.updateById(any(InternalAppEntity.class))).thenReturn(1);
        service.disable(3L);
        assertThat(existing.getStatus()).isEqualTo("DISABLED");
        service.enable(3L);
        assertThat(existing.getStatus()).isEqualTo("ENABLED");
        when(apps.selectById(9L)).thenReturn(null);
        assertThatThrownBy(() -> service.disable(9L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.NOT_FOUND);
    }

    @Test
    void pageOmitsSecrets() {
        InternalAppEntity existing = new InternalAppEntity();
        existing.setId(3L);
        existing.setAppId("appidabcdefghij");
        existing.setAppName("合作方");
        existing.setSecretCipher("cipher");
        existing.setStatus("ENABLED");
        existing.setCreatedAt(LocalDateTime.of(2026, 8, 19, 12, 0));
        when(apps.countAll()).thenReturn(1L);
        when(apps.listPage(0L, 20)).thenReturn(List.of(existing));
        var page = service.page(PageQuery.of(1, 20));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).appId()).isEqualTo("appidabcdefghij");
        assertThat(page.records().get(0).appName()).isEqualTo("合作方");
    }
}
