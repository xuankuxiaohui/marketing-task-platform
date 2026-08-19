package com.mkt.tracking.controller.portal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.tracking.application.TrackBatchService;
import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.support.TrackDropCounters;
import com.mkt.tracking.support.TrackSettings;
import com.mkt.tracking.testsupport.MemoryEventLogStore;
import com.mkt.tracking.testsupport.MemoryEventMetadataStore;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class TrackBatchControllerTest {

    private MemoryEventLogStore logs;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        logs = new MemoryEventLogStore();
        MutableClock clock = new MutableClock(Instant.parse("2026-08-19T12:00:00Z"));
        TrackBatchService service = new TrackBatchService(
                logs,
                new MemoryEventMetadataStore().put("page.view", MetadataStatus.ENABLED),
                new SlidingWindowRateLimiter(new MemoryKeyValueStore(), clock),
                new TrackSettings(),
                new TrackDropCounters(),
                clock);
        mvc = MockMvcBuilders.standaloneSetup(new TrackBatchController(service), new BusinessProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilter(
                        (request, response, chain) -> {
                            jakarta.servlet.http.HttpServletRequest httpReq =
                                    (jakarta.servlet.http.HttpServletRequest) request;
                            jakarta.servlet.http.HttpServletResponse httpRes =
                                    (jakarta.servlet.http.HttpServletResponse) response;
                            if ("true".equals(httpReq.getHeader("X-Track-Outage"))) {
                                httpRes.setStatus(503);
                                return;
                            }
                            chain.doFilter(request, response);
                        },
                        "/api/common/track/*")
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void anonymousBatchPartialAccept() throws Exception {
        mvc.perform(post("/api/common/track/batch")
                        .header("X-Device-Id", "11111111-1111-4111-8111-111111111111")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"events\":[{\"code\":\"page.view\",\"props\":{\"route\":\"/home\"},\"clientTime\":\"t\"},{\"code\":\"NOPE\",\"props\":{}}],\"platform\":\"WEB\",\"appVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.accepted").value(1))
                .andExpect(jsonPath("$.data.dropped").value(1));
        assertThat(logs.rows()).hasSize(1);
        assertThat(logs.rows().get(0).getUserId()).isNull();
        assertThat(logs.rows().get(0).getDeviceId()).isEqualTo("11111111-1111-4111-8111-111111111111");
    }

    @Test
    void loginIdentityUsesUserContext() throws Exception {
        UserContext.set(new UserPrincipal(88L, "client", "u88"));
        mvc.perform(post("/api/common/track/batch")
                        .header("X-Device-Id", "dev-88")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[{\"code\":\"page.view\",\"props\":{}}],\"platform\":\"WEB\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accepted").value(1));
        assertThat(logs.rows().get(0).getUserId()).isEqualTo(88L);
    }

    @Test
    void overflowIs400() throws Exception {
        StringBuilder body = new StringBuilder("{\"events\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                body.append(',');
            }
            body.append("{\"code\":\"page.view\",\"props\":{}}");
        }
        body.append("]}");
        mvc.perform(post("/api/common/track/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("track.batch.overflow"));
    }

    @Test
    void illegalJsonIsParamInvalid() throws Exception {
        mvc.perform(post("/api/common/track/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
    }

    @Test
    void trackOutageDoesNotBlockBusinessProbe() throws Exception {
        mvc.perform(post("/api/common/track/batch")
                        .header("X-Track-Outage", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":[]}"))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/common/probe/ok")).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0));
    }

    @RestController
    @RequestMapping("/api/common/probe")
    static class BusinessProbeController {

        @GetMapping("/ok")
        Result<String> ok() {
            return Result.ok("ok");
        }
    }
}
