package com.mkt.tracking.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.infra.outbox.EventPublisher;
import com.mkt.infra.outbox.MemoryOutboxStore;
import com.mkt.infra.outbox.OutboxProducer;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.tracking.application.TrackAuditAppender;
import com.mkt.tracking.application.TrackMetadataAppService;
import com.mkt.tracking.testsupport.MemoryEventMetadataStore;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class TrackMetadataAdminControllerTest {

    private final MemoryOutboxStore outbox = new MemoryOutboxStore();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T00:00:00Z"));
        TrackMetadataAppService service = new TrackMetadataAppService(
                new MemoryEventMetadataStore(),
                new TrackAuditAppender(new EventPublisher(outbox, OutboxProducer.ADMIN)),
                clock);
        mvc = MockMvcBuilders.standaloneSetup(new TrackMetadataAdminController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clear();
        UserContext.clear();
    }

    @Test
    void crudAndDuplicateAndAudit() throws Exception {
        String body = "{"
                + "\"eventCode\":\"ops.custom.ping\","
                + "\"name\":\"探测\","
                + "\"propSchema\":[{\"name\":\"route\",\"type\":\"string\",\"required\":true}],"
                + "\"status\":\"ENABLED\","
                + "\"owner\":\"tracking\""
                + "}";
        mvc.perform(post("/admin/track/metadata").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1));

        mvc.perform(post("/admin/track/metadata").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("track.metadata.duplicate-code"));

        mvc.perform(get("/admin/track/metadata").param("eventCode", "ops.custom.ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].eventCode").value("ops.custom.ping"));

        mvc.perform(put("/admin/track/metadata/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"eventCode\":\"ops.custom.ping\",\"name\":\"探测停用\",\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        mvc.perform(get("/admin/track/metadata/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mvc.perform(delete("/admin/track/metadata/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        assertThat(outbox.all()).hasSize(3);
        assertThat(outbox.all()).allMatch(row -> "audit.log".equals(row.eventCode()));
    }
}
