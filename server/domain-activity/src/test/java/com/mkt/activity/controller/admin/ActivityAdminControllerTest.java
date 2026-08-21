package com.mkt.activity.controller.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.activity.application.ActivityAdminAppService;
import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.activity.testsupport.MemoryActivityStores;
import com.mkt.activity.testsupport.RecordingRewardPort;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.web.GlobalExceptionHandler;
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

class ActivityAdminControllerTest {

    private MockMvc mvc;
    private long activityId;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MemoryActivityStores stores = new MemoryActivityStores();
        ActivityAdminAppService admin = new ActivityAdminAppService(
                stores.activities,
                stores.participations,
                new RecordingRewardPort(),
                new ActivitySettings(),
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC));
        activityId = admin.save(new ActivitySaveCommand(
                        null,
                        "summer",
                        "夏季专题",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        "<p>hello</p>",
                        new ActivityGrayCommand("NONE", null),
                        List.of(),
                        10L,
                        List.of(),
                        List.of(),
                        false,
                        7,
                        null,
                        null,
                        null,
                        List.of()))
                .id();
        mvc = MockMvcBuilders.standaloneSetup(new ActivityAdminController(admin))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void pageGetPublishScheduleOfflineAndStats() throws Exception {
        mvc.perform(get("/admin/activity/activities").param("code", "summer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value("summer"));

        mvc.perform(get("/admin/activity/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(activityId));

        mvc.perform(post("/admin/activity/activities/" + activityId + "/schedule")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"publishAt\":\"2026-08-21T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"));

        mvc.perform(post("/admin/activity/activities/" + activityId + "/publish")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirm\":true,\"early\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.version").value(1));

        mvc.perform(get("/admin/activity/participations").param("activityId", String.valueOf(activityId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(get("/admin/activity/activities/" + activityId + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(post("/admin/activity/activities/" + activityId + "/offline"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFFLINE"));
    }

    @Test
    void deleteDraft() throws Exception {
        mvc.perform(delete("/admin/activity/activities/" + activityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }
}
