package com.marketing.task.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.domain.entity.AdminUser;
import com.marketing.task.mapper.AdminUserMapper;
import com.marketing.task.security.AdminJwtProvider;
import com.marketing.task.security.AuthenticationException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private AdminJwtProvider adminJwtProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminAuthService service;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, AdminUser.class);
    }

    private AdminAuthService createService() {
        return new AdminAuthService(adminUserMapper, adminJwtProvider, passwordEncoder);
    }

    private AdminUser createUser(Long id, String username, String passwordHash, Boolean enabled) {
        AdminUser user = new AdminUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setNickname("Nick" + id);
        user.setEnabled(enabled);
        return user;
    }

    @Test
    void login_shouldReturnTokenAndUserInfo_whenCredentialsValid() {
        service = createService();
        AdminUser user = createUser(1L, "admin", "$2a$hash", true);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("admin123", "$2a$hash")).thenReturn(true);
        when(adminJwtProvider.issue("1")).thenReturn("jwt-token-xxx");

        AdminAuthService.LoginResult result = service.login("admin", "admin123");

        assertEquals("jwt-token-xxx", result.token());
        assertEquals("1", result.userId());
        assertEquals("admin", result.username());
        assertEquals("Nick1", result.nickname());
        verify(adminUserMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(passwordEncoder).matches("admin123", "$2a$hash");
        verify(adminJwtProvider).issue("1");
    }

    @Test
    void login_shouldUseUsernameAsNickname_whenNicknameIsNull() {
        service = createService();
        AdminUser user = createUser(2L, "admin2", "$2a$hash", true);
        user.setNickname(null);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(adminJwtProvider.issue(any())).thenReturn("token");

        AdminAuthService.LoginResult result = service.login("admin2", "pass");

        assertEquals("admin2", result.nickname());
    }

    @Test
    void login_shouldThrowAuthenticationException_whenUserNotFound() {
        service = createService();
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(AuthenticationException.class, () -> service.login("nobody", "pass"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenPasswordWrong() {
        service = createService();
        AdminUser user = createUser(1L, "admin", "$2a$hash", true);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThrows(AuthenticationException.class, () -> service.login("admin", "wrong"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenAccountDisabled() {
        service = createService();
        AdminUser user = createUser(1L, "admin", "$2a$hash", false);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThrows(AuthenticationException.class, () -> service.login("admin", "admin123"));
    }
}
