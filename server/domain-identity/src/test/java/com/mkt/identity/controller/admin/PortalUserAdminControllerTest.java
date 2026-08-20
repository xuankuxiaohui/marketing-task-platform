package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.contract.RiskListType;
import com.mkt.identity.application.PortalUserAppService;
import com.mkt.identity.response.PortalUserDetailResponse;
import com.mkt.identity.response.PortalUserView;
import com.mkt.identity.response.PrizeSummaryView;
import com.mkt.kernel.PageData;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PortalUserAdminControllerTest {

    private final PortalUserAppService appService = Mockito.mock(PortalUserAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new PortalUserAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void listDetailProfileDisableResetDelete() throws Exception {
        when(appService.page(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new PortalUserView(
                                5L,
                                "user_01",
                                "用户",
                                "GD",
                                "1",
                                "vip",
                                List.of("hot"),
                                "org-1",
                                "ENABLED",
                                Instant.parse("2026-08-01T00:00:00Z"),
                                null,
                                Instant.parse("2026-08-01T00:00:00Z")))));
        mvc.perform(get("/admin/identity/portal-users").param("province", "GD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].username").value("user_01"));

        when(appService.detail(5L))
                .thenReturn(new PortalUserDetailResponse(
                        5L,
                        "user_01",
                        "用户",
                        "GD",
                        "1",
                        "vip",
                        List.of("hot"),
                        "org-1",
                        "ENABLED",
                        Instant.parse("2026-08-01T00:00:00Z"),
                        null,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        1L,
                        2L,
                        30L,
                        new PrizeSummaryView(1L, 0L),
                        0L,
                        List.of(RiskListType.WHITE)));
        mvc.perform(get("/admin/identity/portal-users/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsBalance").value(30))
                .andExpect(jsonPath("$.data.prizeSummary.won").value(1))
                .andExpect(jsonPath("$.data.inProgressInstanceCount").value(1));

        mvc.perform(put("/admin/identity/portal-users/5/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"province\":\"BJ\",\"tags\":[\"a\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        mvc.perform(post("/admin/identity/portal-users/5/disable")).andExpect(status().isOk());
        mvc.perform(post("/admin/identity/portal-users/5/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"pass1234\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/admin/identity/portal-users/5")).andExpect(status().isOk());
    }
}
