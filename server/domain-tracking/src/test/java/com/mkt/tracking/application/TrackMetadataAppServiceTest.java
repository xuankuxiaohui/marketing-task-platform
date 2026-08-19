package com.mkt.tracking.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.tracking.command.TrackMetadataSaveCommand;
import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.domain.PropSchemaItem;
import com.mkt.tracking.query.TrackMetadataQuery;
import com.mkt.tracking.response.TrackMetadataResponse;
import com.mkt.tracking.support.TrackErrorCodes;
import com.mkt.tracking.testsupport.MemoryEventMetadataStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TrackMetadataAppServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T08:00:00Z"));
    private final MemoryEventMetadataStore store = new MemoryEventMetadataStore();
    private final MemoryOutboxStore outbox = new MemoryOutboxStore(clock);
    private TrackMetadataAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        service = new TrackMetadataAppService(
                store, new TrackAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)), clock);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void createWritesAuditAndIsQueryable() {
        TrackMetadataResponse created = service.create(save("ops.custom.ping", "探测", "ENABLED"));
        assertThat(created.id()).isPositive();
        assertThat(store.statusOf("ops.custom.ping")).isEqualTo(MetadataStatus.ENABLED);
        assertThat(outbox.all()).hasSize(1);
        assertThat(outbox.all().get(0).eventCode()).isEqualTo("audit.log");
        assertThat(outbox.all().get(0).payload()).contains("metadata-create");
        assertThat(service.page(new TrackMetadataQuery("ops.custom.ping", null, PageQuery.of(1, 20))).total())
                .isEqualTo(1);
    }

    @Test
    void duplicateCodeRejected() {
        service.create(save("ops.custom.ping", "探测", "ENABLED"));
        assertThatThrownBy(() -> service.create(save("ops.custom.ping", "再登记", "ENABLED")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TrackErrorCodes.METADATA_DUPLICATE_CODE);
        assertThat(store.countByQuery("ops.custom.ping", null)).isEqualTo(1);
    }

    @Test
    void updateDisableLinksToStatusOf() {
        long id = service.create(save("ops.custom.ping", "探测", "ENABLED")).id();
        service.update(id, save("ops.custom.ping", "探测", "DISABLED"));
        assertThat(store.statusOf("ops.custom.ping")).isEqualTo(MetadataStatus.DISABLED);
        assertThat(outbox.all().get(1).payload()).contains("metadata-update");
    }

    @Test
    void updateRejectsEventCodeRename() {
        long id = service.create(save("ops.custom.ping", "探测", "ENABLED")).id();
        assertThatThrownBy(() -> service.update(id, save("ops.custom.pong", "探测", "ENABLED")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.PARAM_INVALID);
    }

    @Test
    void deleteRemovesAndAudits() {
        long id = service.create(save("ops.custom.ping", "探测", "ENABLED")).id();
        service.delete(id);
        assertThat(store.statusOf("ops.custom.ping")).isEqualTo(MetadataStatus.MISSING);
        assertThat(outbox.all().get(1).payload()).contains("metadata-delete");
    }

    @Test
    void missingIdIsNotFound() {
        assertThatThrownBy(() -> service.get(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.NOT_FOUND);
    }

    private static TrackMetadataSaveCommand save(String code, String name, String status) {
        return new TrackMetadataSaveCommand(
                code, name, List.of(new PropSchemaItem("route", "string", true, null)), status, "tracking", "ut");
    }
}
