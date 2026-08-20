package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.InternalAppAppService;
import com.mkt.identity.response.InternalAppCreatedResponse;
import com.mkt.identity.response.InternalAppRotateResponse;
import com.mkt.identity.response.InternalAppView;
import com.mkt.identity.support.InternalAppErrorCodes;
import com.mkt.kernel.BusinessException;
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

class InternalAppAdminControllerTest {

    private final InternalAppAppService appService = Mockito.mock(InternalAppAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InternalAppAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void queryAddRotateDisableEnable() throws Exception {
        when(appService.page(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new InternalAppView(
                                1L,
                                "appidabcdefghij",
                                "合作方",
                                "ENABLED",
                                Instant.parse("2026-08-20T12:00:00Z"),
                                Instant.parse("2026-08-19T12:00:00Z")))));
        mvc.perform(get("/admin/identity/internal-apps"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].appId").value("appidabcdefghij"))
                .andExpect(jsonPath("$.data.records[0].secret").doesNotExist());

        when(appService.create(any())).thenReturn(new InternalAppCreatedResponse(2L, "appidabcdefghik", "s".repeat(43)));
        mvc.perform(post("/admin/identity/internal-apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"新合作方\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.secret").value("s".repeat(43)));

        when(appService.rotate(2L))
                .thenReturn(new InternalAppRotateResponse("t".repeat(43), Instant.parse("2026-08-20T12:00:00Z")));
        mvc.perform(post("/admin/identity/internal-apps/2/rotate-secret"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prevExpireAt").exists());

        mvc.perform(post("/admin/identity/internal-apps/2/disable")).andExpect(status().isOk());
        mvc.perform(post("/admin/identity/internal-apps/2/enable")).andExpect(status().isOk());

        when(appService.create(any())).thenThrow(new BusinessException(InternalAppErrorCodes.DUPLICATE));
        mvc.perform(post("/admin/identity/internal-apps")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"appName\":\"重复\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("internal.app.duplicate"));
    }
}
