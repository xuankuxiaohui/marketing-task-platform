package com.mkt.risk.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.contract.RiskAction;
import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.risk.command.RiskRuleUpdateCommand;
import com.mkt.risk.support.RiskErrorCodes;
import com.mkt.risk.testsupport.MemoryRuleConfigStore;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RiskRuleAppServiceTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-19T08:00:00Z"));
    private final MemoryRuleConfigStore store = new MemoryRuleConfigStore();
    private RiskRuleAppService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        service = new RiskRuleAppService(
                store, new RiskAuditAppender(new EventPublisher(new MemoryOutboxStore(), OutboxProducer.ADMIN)), clock);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void listReturnsSixRules() {
        assertThat(service.list()).hasSize(6);
        assertThat(service.list()).extracting(row -> row.ruleCode()).contains("R-a", "R-f");
    }

    @Test
    void updatePersistsAndRejectsOutOfRange() {
        var updated = service.update("R-f", new RiskRuleUpdateCommand(false, 80L, 60L, RiskAction.MARK));
        assertThat(updated.enabled()).isFalse();
        assertThat(updated.threshold()).isEqualTo(80L);
        assertThat(updated.action()).isEqualTo("MARK");
        assertThat(store.getByCode("R-f").enabledFlag()).isFalse();
        assertThatThrownBy(() -> service.update("R-f", new RiskRuleUpdateCommand(true, 1L, 60L, RiskAction.REJECT)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RiskErrorCodes.RULE_RANGE_VIOLATED);
        assertThatThrownBy(() -> service.update("R-e", new RiskRuleUpdateCommand(true, 5L, 60L, RiskAction.REJECT)))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(RiskErrorCodes.RULE_RANGE_VIOLATED);
    }
}
