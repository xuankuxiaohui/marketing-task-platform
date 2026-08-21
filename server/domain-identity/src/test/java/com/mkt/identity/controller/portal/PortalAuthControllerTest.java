package com.mkt.identity.controller.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.identity.application.AuthAttempt;
import com.mkt.identity.application.PortalAuthService;
import com.mkt.identity.response.PortalAuthResponse;
import com.mkt.identity.response.PortalProfileResponse;
import com.mkt.identity.response.UsernameAvailableResponse;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class PortalAuthControllerTest {

    private final PortalAuthService authService = Mockito.mock(PortalAuthService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        mvc = MockMvcBuilders.standaloneSetup(new PortalAuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerLoginLogoutAndUsernameAvailable() throws Exception {
        when(authService.usernameAvailable(anyString(), anyString()))
                .thenReturn(new UsernameAvailableResponse(true, null));
        when(authService.register(any(), any())).thenReturn(new PortalAuthResponse("client:t", 9L, "用户9", false));
        when(authService.login(any(), any()))
                .thenReturn(AuthAttempt.ok(new PortalAuthResponse("client:t2", 9L, "用户9", false)));
        doNothing().when(authService).logout(any());

        mvc.perform(get("/api/common/auth/username-available").param("username", "bob_01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.available").value(true));
        mvc.perform(post("/api/common/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"username\":\"bob_01\",\"password\":\"abcdefg1\",\"captchaId\":\"c\",\"captchaCode\":\"ab\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("client:t"));
        mvc.perform(post("/api/common/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Device-Id", "550e8400-e29b-41d4-a716-446655440000")
                        .content(
                                "{\"username\":\"bob_01\",\"password\":\"abcdefg1\",\"captchaId\":\"c\",\"captchaCode\":\"ab\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(9));
        mvc.perform(post("/api/common/auth/logout").header("Authorization", "Bearer client:t2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
    }

    @Test
    void profileAndPassword() throws Exception {
        UserContext.set(new UserPrincipal(9L, "client", "bob_01"));
        when(authService.profile(9L))
                .thenReturn(new PortalProfileResponse(9L, "bob_01", "用户9", "BJ", "3", "vip", List.of("a"), 12L, false));
        Mockito.doNothing().when(authService).updateNickname(anyLong(), anyString());
        Mockito.doNothing().when(authService).changePassword(anyLong(), any(), any());
        mvc.perform(get("/api/common/auth/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsBalance").value(12))
                .andExpect(jsonPath("$.data.nickname").value("用户9"));
        mvc.perform(put("/api/common/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"新昵称\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        mvc.perform(put("/api/common/auth/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer client:t2")
                        .content("{\"oldPassword\":\"abcdefg1\",\"newPassword\":\"newpass12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        UserContext.clear();
    }
}
