package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.CacheAdminAppService;
import com.mkt.identity.response.CacheEvictResponse;
import com.mkt.infra.cache.CacheErrorCodes;
import com.mkt.infra.cache.CacheStatsView;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CacheAdminControllerTest {

    private final CacheAdminAppService appService = Mockito.mock(CacheAdminAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CacheAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void statsAndEvictAndSessionForbidden() throws Exception {
        when(appService.stats())
                .thenReturn(new PageData<>(1, List.of(CacheStatsView.na("identity:session"))));
        mvc.perform(get("/admin/system/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].namespace").value("identity:session"));

        when(appService.evict(any())).thenReturn(new CacheEvictResponse(1, 1));
        mvc.perform(post("/admin/system/cache/evict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"NAMESPACE\",\"namespace\":\"dict\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evictedRedis").value(1));

        when(appService.evict(any())).thenThrow(new BusinessException(CacheErrorCodes.SESSION_FORBIDDEN));
        mvc.perform(post("/admin/system/cache/evict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"level\":\"NAMESPACE\",\"namespace\":\"identity:session\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("system.cache.session-forbidden"));
    }
}
