package com.mkt.identity.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.AdminAuthService;
import com.mkt.identity.application.AuthAttempt;
import com.mkt.identity.response.AdminLoginResponse;
import com.mkt.identity.support.AuthCookies;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AdminAuthControllerTest {

    private final AdminAuthService authService = Mockito.mock(AdminAuthService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        mvc = MockMvcBuilders.standaloneSetup(new AdminAuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginSetsHttpOnlySecureStrictCookieAndCsrf() throws Exception {
        when(authService.login(any(), any()))
                .thenReturn(AuthAttempt.ok(new AdminAuthService.IssuedAdminSession(
                        new AdminLoginResponse(1L, "超管", List.of("super-admin"), List.of(), true, "csrf-1"),
                        "admin:raw-token",
                        "csrf-1")));
        MockHttpServletResponse response = mvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"username\":\"admin\",\"password\":\"Abcdef12!x\",\"captchaId\":\"c\",\"captchaCode\":\"ab\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.csrfToken").value("csrf-1"))
                .andExpect(jsonPath("$.data.userId").value(1))
                .andReturn()
                .getResponse();
        String session = response.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(AuthCookies.SESSION + "="))
                .findFirst()
                .orElse("");
        String csrf = response.getHeaders("Set-Cookie").stream()
                .filter(h -> h.startsWith(AuthCookies.CSRF + "="))
                .findFirst()
                .orElse("");
        assertThat(session).contains("admin:raw-token");
        assertThat(session).contains("HttpOnly");
        assertThat(session).contains("Secure");
        assertThat(session).contains("SameSite=Strict");
        assertThat(csrf).contains("csrf-1");
        assertThat(csrf).doesNotContain("HttpOnly");
        assertThat(csrf).contains("SameSite=Strict");
    }

    @Test
    void logoutAndPassword() throws Exception {
        UserContext.set(new UserPrincipal(1L, "admin", "admin"));
        Mockito.doNothing().when(authService).logout(anyLong(), anyString(), any(), any());
        Mockito.doNothing().when(authService).changePassword(anyLong(), any(), any());
        mvc.perform(post("/admin/auth/logout").cookie(new jakarta.servlet.http.Cookie(AuthCookies.SESSION, "admin:t")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        mvc.perform(put("/admin/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new jakarta.servlet.http.Cookie(AuthCookies.SESSION, "admin:t"))
                        .content("{\"oldPassword\":\"Abcdef12!x\",\"newPassword\":\"NewPass12!x\"}"))
                .andExpect(status().isOk());
        UserContext.clear();
    }
}
