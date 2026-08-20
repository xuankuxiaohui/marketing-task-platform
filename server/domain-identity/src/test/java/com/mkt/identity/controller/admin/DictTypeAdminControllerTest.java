package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.DictAppService;
import com.mkt.identity.entity.DictTypeEntity;
import com.mkt.identity.response.DictEntryOption;
import com.mkt.identity.response.DictTypeView;
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

class DictTypeAdminControllerTest {

    private final DictAppService appService = Mockito.mock(DictAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DictTypeAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void pageCreateUpdateDeleteAndEntries() throws Exception {
        when(appService.pageTypes(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new DictTypeView(
                                1L, "province", "省份", "ENABLED", null, Instant.parse("2026-08-19T00:00:00Z")))));
        mvc.perform(get("/admin/system/dict-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].code").value("province"));

        DictTypeEntity created = new DictTypeEntity();
        created.setId(9L);
        when(appService.createType(any())).thenReturn(created);
        mvc.perform(post("/admin/system/dict-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"color\",\"name\":\"颜色\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));

        mvc.perform(put("/admin/system/dict-types/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"颜色\",\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk());

        mvc.perform(delete("/admin/system/dict-types/9")).andExpect(status().isOk());

        when(appService.listEnabledEntries("province")).thenReturn(List.of(new DictEntryOption("广东", "GD", 1)));
        mvc.perform(get("/admin/system/dict-types/province/entries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].value").value("GD"));
    }
}
