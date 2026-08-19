package com.mkt.identity.controller.portal;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.DictAppService;
import com.mkt.identity.response.DictEntryOption;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DictPortalControllerTest {

    private final DictAppService appService = Mockito.mock(DictAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DictPortalController(appService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void disabledTypeReturnsEmptyArray() throws Exception {
        when(appService.listEnabledEntries("province")).thenReturn(List.of());
        mvc.perform(get("/api/common/dict/province"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isEmpty());

        when(appService.listEnabledEntries("user_role"))
                .thenReturn(List.of(new DictEntryOption("VIP", "vip", 1)));
        mvc.perform(get("/api/common/dict/user_role"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].label").value("VIP"))
                .andExpect(jsonPath("$.data[0].value").value("vip"));
    }
}
