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
import com.mkt.risk.command.RiskCaseHandleCommand;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskHandleAction;
import com.mkt.risk.entity.RiskHitLogEntity;
import com.mkt.risk.query.RiskHitQuery;
import com.mkt.risk.support.RiskListProjection;
import com.mkt.risk.testsupport.MemoryRiskHandleLogStore;
import com.mkt.risk.testsupport.MemoryRiskHitLogStore;
import com.mkt.risk.testsupport.MemoryRiskListItemStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RiskCaseAppServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T08:00:00Z"));
    private final MemoryRiskListItemStore lists = new MemoryRiskListItemStore();
    private final MemoryRiskHitLogStore hits = new MemoryRiskHitLogStore();
    private final MemoryRiskHandleLogStore handles = new MemoryRiskHandleLogStore();
    private final MemoryOutboxStore outbox = new MemoryOutboxStore(clock);
    private RiskCaseAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(9L, "admin", "risk-op"));
        RiskListProjection projection = new RiskListProjection(new MemoryKeyValueStore(), lists, clock);
        service = new RiskCaseAppService(
                hits,
                handles,
                lists,
                projection,
                new RiskAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                clock);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void handleAddBlackWritesListHandleAndAudit() {
        service.handle(new RiskCaseHandleCommand(null, 77L, RiskHandleAction.ADD_BLACK, false, "abuse", null));
        assertThat(lists.getByUk(RiskDimension.USER.name(), RiskListType.BLACK.name(), "77")).isNotNull();
        assertThat(handles.listAll()).hasSize(1);
        assertThat(handles.listAll().get(0).getReason()).isEqualTo("abuse");
        assertThat(outbox.all()).hasSize(1);
        assertThat(outbox.all().get(0).eventCode()).isEqualTo("audit.log");
    }

    @Test
    void handleRemoveMovesToWhite() {
        service.handle(new RiskCaseHandleCommand(null, 77L, RiskHandleAction.ADD_BLACK, false, "abuse", null));
        service.handle(new RiskCaseHandleCommand(null, 77L, RiskHandleAction.REMOVE_BLACK, true, "false", null));
        assertThat(lists.getByUk(RiskDimension.USER.name(), RiskListType.BLACK.name(), "77")).isNull();
        assertThat(lists.getByUk(RiskDimension.USER.name(), RiskListType.WHITE.name(), "77")).isNotNull();
        assertThat(handles.listAll()).hasSize(2);
    }

    @Test
    void handleFalsePositiveOnlyWritesHandle() {
        service.handle(new RiskCaseHandleCommand(null, 5L, RiskHandleAction.MARK_FALSE_POSITIVE, false, "fp", null));
        assertThat(lists.size()).isZero();
        assertThat(handles.listAll()).hasSize(1);
        assertThat(handles.listAll().get(0).getAction()).isEqualTo("MARK_FALSE_POSITIVE");
    }

    @Test
    void handleResolvesUserFromHit() {
        RiskHitLogEntity hit = new RiskHitLogEntity();
        hit.setHitType("LIST");
        hit.setRuleCode("USER:BLACK");
        hit.setUserId(33L);
        hit.setContext("{}");
        hit.setHitValue("1");
        hit.setThreshold("1");
        hit.setActionResult("REJECTED");
        hit.setSimulated(0);
        hit.setOccurredAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        hit.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        hits.insert(hit);
        service.handle(new RiskCaseHandleCommand(hit.getId(), null, RiskHandleAction.ADD_BLACK, false, "from-hit", null));
        assertThat(lists.getByUk(RiskDimension.USER.name(), RiskListType.BLACK.name(), "33")).isNotNull();
    }

    @Test
    void pageHitsFilters() {
        RiskHitLogEntity hit = new RiskHitLogEntity();
        hit.setHitType("RULE");
        hit.setRuleCode("R-a");
        hit.setUserId(1L);
        hit.setContext("{}");
        hit.setHitValue("9");
        hit.setThreshold("3");
        hit.setActionResult("REJECTED");
        hit.setSimulated(0);
        hit.setOccurredAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        hit.setCreatedAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        hits.insert(hit);
        var page = service.pageHits(
                new RiskHitQuery("R-a", "RULE", null, 1L, "REJECTED", null, null, PageQuery.of(1, 10)));
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).ruleCode()).isEqualTo("R-a");
    }

    @Test
    void addBlackWithoutUserRejected() {
        assertThatThrownBy(() ->
                        service.handle(new RiskCaseHandleCommand(null, null, RiskHandleAction.ADD_BLACK, false, "x", null)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void removeBlackToWhitelistRequiresExistingBlack() {
        assertThatThrownBy(() -> service.handle(
                        new RiskCaseHandleCommand(null, 77L, RiskHandleAction.REMOVE_BLACK, true, "no-black", null)))
                .isInstanceOf(BusinessException.class);
        assertThat(lists.size()).isZero();
    }

    @Test
    void rejectNonPositiveUserAndPastExpire() {
        assertThatThrownBy(() -> service.handle(
                        new RiskCaseHandleCommand(null, 0L, RiskHandleAction.ADD_BLACK, false, "zero", null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.handle(new RiskCaseHandleCommand(
                        null, 5L, RiskHandleAction.ADD_BLACK, false, "old", clock.instant().minusSeconds(1))))
                .isInstanceOf(BusinessException.class);
    }
}
