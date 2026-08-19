package com.mkt.risk.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.mkt.risk.application.RiskListAppService;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.support.RiskListPermissions;
import com.mkt.risk.support.RiskListProjection;
import com.mkt.risk.support.RiskPermissionGuard;
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

class RiskListAdminControllerTest {

    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private final RecordingRiskPermissionGuard permissions = new RecordingRiskPermissionGuard();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        MemoryRiskListItemStore store = new MemoryRiskListItemStore();
        RiskListProjection projection = new RiskListProjection(new MemoryKeyValueStore(), store, clock);
        RiskListAppService service = new RiskListAppService(
                store, projection, new RiskAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)), clock);
        mvc = MockMvcBuilders.standaloneSetup(new RiskListAdminController(service, permissions))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void addThenDuplicateThenListThenDelete() throws Exception {
        mvc.perform(post("/admin/risk/list-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dimension\":\"USER\",\"listType\":\"BLACK\",\"listValue\":\"5\",\"reason\":\"bot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));

        String dup = mvc.perform(post("/admin/risk/list-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dimension\":\"USER\",\"listType\":\"BLACK\",\"listValue\":\"5\",\"reason\":\"bot\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("risk.list.duplicate-returned"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(dup).contains("listValue");
        assertThat(permissions.required).contains(RiskListPermissions.BLACK_ADD);

        mvc.perform(get("/admin/risk/list-items").param("dimension", "USER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mvc.perform(delete("/admin/risk/list-items/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"done\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void listTypePermissionDeniedIs403() throws Exception {
        RiskPermissionGuard deny = permission -> {
            throw new com.mkt.kernel.BusinessException(com.mkt.kernel.CommonErrorCodes.PERMISSION_DENIED);
        };
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        MemoryRiskListItemStore store = new MemoryRiskListItemStore();
        RiskListAppService service = new RiskListAppService(
                store,
                new RiskListProjection(new MemoryKeyValueStore(), store, clock),
                new RiskAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                clock);
        MockMvc denied = MockMvcBuilders.standaloneSetup(new RiskListAdminController(service, deny))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        denied.perform(get("/admin/risk/list-items").param("listType", "BLACK"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
    }

    @Test
    void importInvalidReasonRejected() throws Exception {
        mvc.perform(post("/admin/risk/list-items/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dimension\":\"IP\",\"listType\":\"BLACK\",\"content\":\"1.1.1.1\",\"reason\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
    }

    @Test
    void queryWithoutListTypeRequiresBothQueryCodes() throws Exception {
        permissions.required.clear();
        mvc.perform(get("/admin/risk/list-items"))
                .andExpect(status().isOk());
        assertThat(permissions.required)
                .containsExactly(RiskListPermissions.BLACK_QUERY, RiskListPermissions.WHITE_QUERY);
    }

    @Test
    void whiteImportRejected() throws Exception {
        permissions.required.clear();
        mvc.perform(post("/admin/risk/list-items/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"dimension\":\"USER\",\"listType\":\"WHITE\",\"content\":\"9\",\"reason\":\"vip\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        assertThat(permissions.required).isEmpty();
    }

    @Test
    void importSucceeds() throws Exception {
        mvc.perform(post("/admin/risk/list-items/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"dimension\":\"IP\",\"listType\":\"BLACK\",\"content\":\"1.1.1.1\\nbad\",\"reason\":\"batch\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imported").value(1))
                .andExpect(jsonPath("$.data.invalid").value(1));
        assertThat(outbox.all()).isNotEmpty();
    }
}
