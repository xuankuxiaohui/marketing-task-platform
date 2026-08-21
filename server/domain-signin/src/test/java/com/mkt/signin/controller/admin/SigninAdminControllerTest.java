package com.mkt.signin.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.signin.application.SigninAdminAppService;
import com.mkt.signin.command.SigninActivitySaveCommand;
import com.mkt.signin.command.SigninTierCommand;
import com.mkt.signin.testsupport.MemorySigninStores;
import com.mkt.signin.testsupport.RecordingRewardPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SigninAdminControllerTest {

    private MockMvc mvc;
    private SigninAdminAppService admin;
    private long activityId;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MemorySigninStores stores = new MemorySigninStores();
        admin = new SigninAdminAppService(
                stores.activities,
                stores.snapshots,
                stores.records,
                new RecordingRewardPort(),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
        activityId = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L))))
                .id();
        mvc = MockMvcBuilders.standaloneSetup(new SigninAdminController(admin))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void pageGetPublishScheduleOfflineAndRecords() throws Exception {
        mvc.perform(get("/admin/signin/activities").param("code", "daily_check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value("daily_check"));

        mvc.perform(get("/admin/signin/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(activityId));

        mvc.perform(post("/admin/signin/activities/" + activityId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publishAt\":\"2026-08-21T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        mvc.perform(post("/admin/signin/activities/" + activityId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true,\"early\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(get("/admin/signin/records").param("activityId", String.valueOf(activityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(post("/admin/signin/activities/" + activityId + "/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
    }

    @Test
    void deleteDraft() throws Exception {
        mvc.perform(delete("/admin/signin/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }
}
