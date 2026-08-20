package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.AuditQueryAppService;
import com.mkt.identity.response.AuditView;
import com.mkt.kernel.PageData;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuditAdminControllerTest {

    private final AuditQueryAppService appService = Mockito.mock(AuditQueryAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuditAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void pageReturnsRecords() throws Exception {
        when(appService.page(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new AuditView(
                                9L,
                                "auth",
                                "login",
                                null,
                                "alice",
                                "10.0.0.1",
                                "ua",
                                "{}",
                                "FAILURE",
                                null,
                                12,
                                "trace",
                                Instant.parse("2026-08-19T12:00:00Z")))));
        mvc.perform(get("/admin/system/audits").param("action", "login").param("result", "FAILURE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].operatorName").value("alice"))
                .andExpect(jsonPath("$.data.records[0].result").value("FAILURE"));
    }
}
