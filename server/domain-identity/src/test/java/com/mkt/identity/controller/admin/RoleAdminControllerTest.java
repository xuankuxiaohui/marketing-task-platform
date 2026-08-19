package com.mkt.identity.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.RoleAppService;
import com.mkt.identity.entity.RoleEntity;
import com.mkt.identity.support.AuthErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.identity.response.RoleView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RoleAdminControllerTest {

    private final RoleAppService appService = Mockito.mock(RoleAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new RoleAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void listCreateUpdateAssignAndBuiltInDelete() throws Exception {
        when(appService.page(any()))
                .thenReturn(new PageData<>(
                        1, List.of(new RoleView(1L, "super-admin", "超管", "ENABLED", 1, Instant.parse("2026-08-19T00:00:00Z")))));
        mvc.perform(get("/admin/identity/roles").param("all", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].code").value("super-admin"));

        RoleEntity created = new RoleEntity();
        created.setId(9L);
        when(appService.create(any())).thenReturn(created);
        mvc.perform(post("/admin/identity/roles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ops-a\",\"name\":\"运营\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));

        mvc.perform(put("/admin/identity/roles/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"运营\",\"description\":\"d\",\"status\":\"ENABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        mvc.perform(put("/admin/identity/roles/9/permissions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"permissionIds\":[4,10]}"))
                .andExpect(status().isOk());

        doThrow(new BusinessException(AuthErrorCodes.ROLE_BUILT_IN)).when(appService).delete(eq(1L));
        mvc.perform(delete("/admin/identity/roles/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.role.built-in"));
    }
}
