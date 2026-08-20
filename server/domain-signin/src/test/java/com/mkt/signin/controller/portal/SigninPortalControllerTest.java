package com.mkt.signin.controller.portal;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.signin.application.SigninAdminAppService;
import com.mkt.signin.application.SigninPortalAppService;
import com.mkt.signin.command.SigninActivitySaveCommand;
import com.mkt.signin.command.SigninPublishCommand;
import com.mkt.signin.command.SigninTierCommand;
import com.mkt.signin.support.SigninSettings;
import com.mkt.signin.testsupport.MemorySigninStores;
import com.mkt.signin.testsupport.RecordingRewardPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SigninPortalControllerTest {

    private MockMvc mvc;
    private long activityId;
    private SlidingWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        MemorySigninStores stores = new MemorySigninStores();
        RecordingRewardPort rewards = new RecordingRewardPort();
        SigninSettings settings = new SigninSettings();
        Clock clock = Clock.fixed(Instant.parse("2026-08-20T04:00:00Z"), ZoneOffset.UTC);
        SigninAdminAppService admin =
                new SigninAdminAppService(stores.activities, stores.snapshots, stores.records, rewards, clock);
        SigninPortalAppService portal = new SigninPortalAppService(
                stores.activities, stores.snapshots, stores.records, rewards, settings, clock);
        activityId = admin.save(new SigninActivitySaveCommand(
                        null,
                        "daily_check",
                        "每日签到",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T16:00:00Z"),
                        List.of(new SigninTierCommand(1, 10L))))
                .id();
        admin.publish(activityId, new SigninPublishCommand(true, false));
        UserContext.set(new UserPrincipal(9L, "client", "bob"));
        limiter = mock(SlidingWindowRateLimiter.class);
        when(limiter.tryAcquire(eq(RateLimitDim.USER), anyString(), anyInt(), anyInt())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<SlidingWindowRateLimiter> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(limiter);
        mvc = MockMvcBuilders.standaloneSetup(new SigninPortalController(portal, provider, settings))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void listCalendarCheckinAndCatchup() throws Exception {
        mvc.perform(get("/api/common/signin/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].code").value("daily_check"));

        mvc.perform(get("/api/common/signin/" + activityId + "/calendar").param("yearMonth", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.consecutiveDays").value(0))
                .andExpect(jsonPath("$.data.catchupCostPoints").value(100));

        mvc.perform(post("/api/common/signin/" + activityId + "/checkin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.alreadySigned").value(false))
                .andExpect(jsonPath("$.data.source").value("CHECKIN"));

        mvc.perform(post("/api/common/signin/" + activityId + "/catchup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"signDate\":\"2026-08-19\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("CATCHUP"));
    }

    @Test
    void rateLimitRejectsWrite() throws Exception {
        when(limiter.tryAcquire(eq(RateLimitDim.USER), anyString(), anyInt(), anyInt())).thenReturn(false);
        mvc.perform(post("/api/common/signin/" + activityId + "/checkin"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("signin.signin.rate-limited"));
    }
}
