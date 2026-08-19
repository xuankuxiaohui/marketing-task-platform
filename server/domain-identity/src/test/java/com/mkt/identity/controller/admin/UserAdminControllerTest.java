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

import com.mkt.identity.application.AdminUserAppService;
import com.mkt.identity.entity.AdminUserEntity;
import com.mkt.identity.response.AdminUserView;
import com.mkt.identity.support.AuthErrorCodes;
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

class UserAdminControllerTest {

    private final AdminUserAppService appService = Mockito.mock(AdminUserAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new UserAdminController(appService))
                .setControllerAdvice(new GlobalExceptionHandler(), new SaTokenExceptionHandler())
                .build();
    }

    @Test
    void listCreateUpdateDisableResetAndSelfProtectedDelete() throws Exception {
        when(appService.page(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new AdminUserView(
                                2L,
                                "ops",
                                "运营",
                                "ENABLED",
                                List.of("ops"),
                                Instant.parse("2026-08-19T00:00:00Z"),
                                Instant.parse("2026-08-18T00:00:00Z")))));
        mvc.perform(get("/admin/identity/users").param("username", "ops"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.records[0].username").value("ops"));

        AdminUserEntity created = new AdminUserEntity();
        created.setId(9L);
        when(appService.create(any())).thenReturn(created);
        mvc.perform(post("/admin/identity/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ops_b\",\"nickname\":\"运营\",\"password\":\"Abcdef12!x\",\"roleIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(9));

        mvc.perform(put("/admin/identity/users/9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"运营2\",\"roleIds\":[2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));

        mvc.perform(post("/admin/identity/users/9/disable")).andExpect(status().isOk());
        mvc.perform(post("/admin/identity/users/9/enable")).andExpect(status().isOk());
        mvc.perform(post("/admin/identity/users/9/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Abcdef12!y\"}"))
                .andExpect(status().isOk());

        doThrow(new BusinessException(AuthErrorCodes.USER_SELF_PROTECTED)).when(appService).delete(eq(1L));
        mvc.perform(delete("/admin/identity/users/1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("auth.user.self-protected"));
    }
}
