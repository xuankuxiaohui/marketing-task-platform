package com.mkt.activity.controller.portal;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.activity.application.ActivityAdminAppService;
import com.mkt.activity.application.ActivityPortalAppService;
import com.mkt.activity.command.ActivityGrayCommand;
import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.activity.testsupport.MemoryActivityStores;
import com.mkt.activity.testsupport.RecordingRewardPort;
import com.mkt.activity.testsupport.StubUserAttributePort;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ActivityPortalControllerTest {

    private MockMvc mvc;
    private long activityId;
    private String contentHash;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MemoryActivityStores stores = new MemoryActivityStores();
        RecordingRewardPort rewards = new RecordingRewardPort();
        ActivitySettings settings = new ActivitySettings();
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        ActivityAdminAppService admin = new ActivityAdminAppService(
                stores.activities, stores.participations, rewards, settings, clock);
        ActivityPortalAppService portal = new ActivityPortalAppService(
                stores.activities, stores.participations, rewards, new StubUserAttributePort(), clock);
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
        admin.publish(activityId, new ActivityPublishCommand(true, false));
        contentHash = admin.get(activityId).contentHash();
        UserContext.set(new UserPrincipal(9L, "client", "bob"));
        SlidingWindowRateLimiter limiter = mock(SlidingWindowRateLimiter.class);
        when(limiter.tryAcquire(eq(RateLimitDim.USER), anyString(), anyInt(), anyInt())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<SlidingWindowRateLimiter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(limiter);
        mvc = MockMvcBuilders.standaloneSetup(new ActivityPortalController(portal, provider, settings))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listDetailEtagAndParticipate() throws Exception {
        mvc.perform(get("/api/common/activity/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("summer"));

        mvc.perform(get("/api/common/activity/" + activityId))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"" + contentHash + "\""))
                .andExpect(jsonPath("$.data.richText").value(org.hamcrest.Matchers.containsString("<p>")));

        mvc.perform(get("/api/common/activity/" + activityId).header("If-None-Match", "\"" + contentHash + "\""))
                .andExpect(status().isNotModified());

        mvc.perform(post("/api/common/activity/" + activityId + "/participate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("PASS"))
                .andExpect(jsonPath("$.data.granted").value(true));
    }
}
