package com.mkt.risk.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.risk.application.RiskAuditAppender;
import com.mkt.risk.application.RiskCaseAppService;
import com.mkt.risk.support.RiskListPermissions;
import com.mkt.risk.support.RiskListProjection;
import com.mkt.risk.support.RiskPermissionGuard;
import com.mkt.risk.testsupport.MemoryRiskHandleLogStore;
import com.mkt.risk.testsupport.MemoryRiskHitLogStore;
import com.mkt.risk.testsupport.MemoryRiskListItemStore;
import com.mkt.risk.testsupport.RecordingRiskPermissionGuard;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RiskCaseAdminControllerTest {

    private MockMvc mvc;
    private final RecordingRiskPermissionGuard permissions = new RecordingRiskPermissionGuard();

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        MemoryRiskListItemStore lists = new MemoryRiskListItemStore();
        RiskCaseAppService service = new RiskCaseAppService(
                new MemoryRiskHitLogStore(),
                new MemoryRiskHandleLogStore(),
                lists,
                new RiskListProjection(new MemoryKeyValueStore(), lists, clock),
                new RiskAuditAppender(new EventPublisher(new MemoryOutboxStore(), OutboxProducer.ADMIN)),
                clock);
        mvc = MockMvcBuilders.standaloneSetup(new RiskCaseAdminController(service, permissions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void handleAndQueryHits() throws Exception {
        mvc.perform(post("/admin/risk/cases/handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"action\":\"ADD_BLACK\",\"toWhitelist\":false,\"reason\":\"abuse\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.ok").value(true));

        mvc.perform(post("/admin/risk/cases/handle")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":9,\"action\":\"ADD_BLACK\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));

        mvc.perform(get("/admin/risk/hits")).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        assertThat(permissions.required).contains(RiskListPermissions.CASE_HANDLE, RiskListPermissions.CASE_QUERY);
    }

    @Test
    void hitsWithoutPermissionIs403() throws Exception {
        RiskPermissionGuard deny = permission -> {
            throw new com.mkt.kernel.BusinessException(com.mkt.kernel.CommonErrorCodes.PERMISSION_DENIED);
        };
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        MemoryRiskListItemStore lists = new MemoryRiskListItemStore();
        RiskCaseAppService service = new RiskCaseAppService(
                new MemoryRiskHitLogStore(),
                new MemoryRiskHandleLogStore(),
                lists,
                new RiskListProjection(new MemoryKeyValueStore(), lists, clock),
                new RiskAuditAppender(new EventPublisher(new MemoryOutboxStore(), OutboxProducer.ADMIN)),
                clock);
        MockMvc denied = MockMvcBuilders.standaloneSetup(new RiskCaseAdminController(service, deny))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        denied.perform(get("/admin/risk/hits"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
    }
}
