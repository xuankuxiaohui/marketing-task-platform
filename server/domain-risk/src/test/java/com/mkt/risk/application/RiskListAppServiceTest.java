package com.mkt.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.RiskListType;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.risk.command.RiskListItemCreateCommand;
import com.mkt.risk.command.RiskListItemImportCommand;
import com.mkt.risk.domain.ListDecision;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskListKeys;
import com.mkt.risk.query.RiskListItemQuery;
import com.mkt.risk.support.ListLookup;
import com.mkt.risk.support.RiskListProjection;
import com.mkt.risk.testsupport.MemoryRiskListItemStore;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RiskListAppServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T08:00:00Z"));
    private final MemoryRiskListItemStore store = new MemoryRiskListItemStore();
    private final MemoryKeyValueStore redis = new MemoryKeyValueStore();
    private final MemoryOutboxStore outbox = new MemoryOutboxStore(clock);
    private RiskListAppService service;
    private ListLookup lookup;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        RiskListProjection projection = new RiskListProjection(redis, store, clock);
        service = new RiskListAppService(
                store, projection, new RiskAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)), clock);
        lookup = new ListLookup(projection, clock);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void addWritesProjectionAndAudit() {
        RiskListAddResult result = service.add(new RiskListItemCreateCommand(
                RiskDimension.USER, RiskListType.BLACK, "88", "spam", null, false, null));
        assertThat(result.duplicate()).isFalse();
        assertThat(result.item().id()).isPositive();
        assertThat(redis.get(RiskListKeys.of(RiskDimension.USER, RiskListType.BLACK, "88")))
                .isEqualTo(RiskListKeys.PERMANENT);
        assertThat(outbox.all()).hasSize(1);
        assertThat(outbox.all().get(0).eventCode()).isEqualTo("audit.log");
        assertThat(lookup.decide(
                        com.mkt.contract.RiskScene.CLAIM,
                        new com.mkt.contract.RiskSubject(88L, "9.9.9.9", "d", null)))
                .isEqualTo(ListDecision.REJECT);
    }

    @Test
    void addDuplicateReturnsExisting() {
        service.add(new RiskListItemCreateCommand(
                RiskDimension.IP, RiskListType.BLACK, "1.2.3.4", "bot", null, null, null));
        RiskListAddResult again = service.add(new RiskListItemCreateCommand(
                RiskDimension.IP, RiskListType.BLACK, "1.2.3.4", "again", null, null, null));
        assertThat(again.duplicate()).isTrue();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void importReportsInvalidAndImported() {
        var report = service.importItems(new RiskListItemImportCommand(
                RiskDimension.USER, RiskListType.BLACK, "11\nbad\n11\n12", "batch"));
        assertThat(report.imported()).isEqualTo(2);
        assertThat(report.invalid()).isEqualTo(1);
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    void importRejectsWhitelist() {
        assertThatThrownBy(() -> service.importItems(new RiskListItemImportCommand(
                        RiskDimension.USER, RiskListType.WHITE, "11", "vip")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void removeDeletesAndUnlinks() {
        long id = service.add(new RiskListItemCreateCommand(
                        RiskDimension.DEVICE, RiskListType.BLACK, "dev-1", "lost", null, null, null))
                .item()
                .id();
        service.remove(id, "cleared");
        assertThat(store.getById(id)).isNull();
        assertThat(redis.get(RiskListKeys.of(RiskDimension.DEVICE, RiskListType.BLACK, "dev-1"))).isNull();
    }

    @Test
    void removeMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.remove(99L, "x")).isInstanceOf(BusinessException.class);
    }

    @Test
    void getReturnsExisting() {
        long id = service.add(new RiskListItemCreateCommand(
                        RiskDimension.USER, RiskListType.BLACK, "44", "x", null, null, null))
                .item()
                .id();
        assertThat(service.get(id).listValue()).isEqualTo("44");
        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void pageFiltersByDimension() {
        service.add(new RiskListItemCreateCommand(
                RiskDimension.USER, RiskListType.BLACK, "1", "a", null, null, null));
        service.add(new RiskListItemCreateCommand(
                RiskDimension.IP, RiskListType.BLACK, "8.8.8.8", "b", null, null, null));
        var page = service.page(new RiskListItemQuery(
                RiskDimension.USER, RiskListType.BLACK, null, null, null, PageQuery.of(1, 20)));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records()).hasSize(1);
    }

    @Test
    void denyLoginOnlyOnUserBlack() {
        assertThatThrownBy(() -> service.add(new RiskListItemCreateCommand(
                        RiskDimension.IP, RiskListType.BLACK, "1.1.1.1", "x", null, true, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void rejectPastExpireAndNonCanonicalUser() {
        Instant past = clock.instant().minusSeconds(1);
        assertThatThrownBy(() -> service.add(new RiskListItemCreateCommand(
                        RiskDimension.USER, RiskListType.BLACK, "88", "old", past, false, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.add(new RiskListItemCreateCommand(
                        RiskDimension.USER, RiskListType.BLACK, "+88", "plus", null, false, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.add(new RiskListItemCreateCommand(
                        RiskDimension.USER, RiskListType.BLACK, "088", "zero", null, false, null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void ipv6MixedCaseHitsAfterNormalize() {
        service.add(new RiskListItemCreateCommand(
                RiskDimension.IP, RiskListType.BLACK, "2001:DB8::1", "v6", null, false, null));
        assertThat(lookup.decide(
                        com.mkt.contract.RiskScene.LOGIN,
                        new com.mkt.contract.RiskSubject(1L, "2001:db8::1", "d", null)))
                .isEqualTo(ListDecision.REJECT);
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void emptyUserContextIsPermissionDenied() {
        UserContext.clear();
        assertThatThrownBy(() -> service.add(new RiskListItemCreateCommand(
                        RiskDimension.USER, RiskListType.BLACK, "7", "x", null, false, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).errorCode().code())
                        .isEqualTo("common.permission-denied"));
    }
}
