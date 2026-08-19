package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.config.ConfigAppService;
import com.mkt.identity.entity.SysConfigEntity;
import com.mkt.identity.response.ConfigView;
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

class ConfigAdminControllerTest {

    private final ConfigAppService appService = Mockito.mock(ConfigAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ConfigAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void pageCreateUpdate() throws Exception {
        when(appService.page(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new ConfigView(
                                1L,
                                "ratelimit.login",
                                "auth",
                                "10",
                                "NUMBER",
                                false,
                                "ENABLED",
                                null,
                                Instant.parse("2026-08-19T00:00:00Z")))));
        mvc.perform(get("/admin/system/configs").param("key", "ratelimit.login"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].configKey").value("ratelimit.login"));

        SysConfigEntity created = new SysConfigEntity();
        created.setId(8L);
        when(appService.create(any())).thenReturn(created);
        mvc.perform(post("/admin/system/configs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"configKey\":\"feature.x\",\"configGroup\":\"g\",\"configValue\":\"true\",\"valueType\":\"BOOL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(8));

        mvc.perform(put("/admin/system/configs/feature.x")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());
        Mockito.verify(appService).update(eq("feature.x"), any());
    }
}
