package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.SessionAdminAppService;
import com.mkt.identity.response.SessionView;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
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

class SessionAdminControllerTest {

    private final SessionAdminAppService appService = Mockito.mock(SessionAdminAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SessionAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void listAndKick() throws Exception {
        when(appService.page(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new SessionView(
                                "ab12",
                                "alice",
                                "admin",
                                Instant.parse("2026-08-19T12:00:00Z"),
                                Instant.parse("2026-08-19T12:01:00Z"),
                                "10.0.0.1",
                                "dev-1"))));
        mvc.perform(get("/admin/identity/sessions").param("accountType", "admin").param("account", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].tokenLast4").value("ab12"))
                .andExpect(jsonPath("$.data.records[0].accountType").value("admin"));

        mvc.perform(post("/admin/identity/sessions/kick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"admin\",\"account\":\"alice\",\"tokenLast4\":\"ab12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        doThrow(new BusinessException(CommonErrorCodes.NOT_FOUND)).when(appService).kick(any());
        mvc.perform(post("/admin/identity/sessions/kick")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountType\":\"admin\",\"account\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("common.not-found"));
    }
}
