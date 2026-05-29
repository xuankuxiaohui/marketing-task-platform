package com.marketing.system.service.auth;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.system.domain.entity.AdminUser;
import com.marketing.system.mapper.AdminUserMapper;
import com.marketing.security.AuthenticationException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
    private PasswordEncoder passwordEncoder;

    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, AdminUser.class);
    }

    @AfterEach
    void closeStaticMock() {
        if (stpUtilMock != null) {
            stpUtilMock.close();
        }
    }

    private AdminAuthService createService() {
        return new AdminAuthService(adminUserMapper, passwordEncoder);
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
        AdminAuthService service = createService();
        AdminUser user = createUser(1L, "admin", "$2a$hash", true);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("admin123", "$2a$hash")).thenReturn(true);

        stpUtilMock = mockStatic(StpUtil.class);
        stpUtilMock.when(() -> StpUtil.login(eq("1"), any(SaLoginModel.class))).then(invocation -> null);
        stpUtilMock.when(StpUtil::getTokenValue).thenReturn("sa-token-xxx");

        AdminAuthService.LoginResult result = service.login("admin", "admin123");

        assertEquals("sa-token-xxx", result.token());
        assertEquals("1", result.userId());
        assertEquals("admin", result.username());
        assertEquals("Nick1", result.nickname());
        verify(adminUserMapper).selectOne(any(LambdaQueryWrapper.class));
        verify(passwordEncoder).matches("admin123", "$2a$hash");
    }

    @Test
    void login_shouldUseUsernameAsNickname_whenNicknameIsNull() {
        AdminAuthService service = createService();
        AdminUser user = createUser(2L, "admin2", "$2a$hash", true);
        user.setNickname(null);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        stpUtilMock = mockStatic(StpUtil.class);
        stpUtilMock.when(() -> StpUtil.login(eq("2"), any(SaLoginModel.class))).then(invocation -> null);
        stpUtilMock.when(StpUtil::getTokenValue).thenReturn("token");

        AdminAuthService.LoginResult result = service.login("admin2", "pass");

        assertEquals("admin2", result.nickname());
    }

    @Test
    void login_shouldThrowAuthenticationException_whenUserNotFound() {
        AdminAuthService service = createService();
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(AuthenticationException.class, () -> service.login("nobody", "pass"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenPasswordWrong() {
        AdminAuthService service = createService();
        AdminUser user = createUser(1L, "admin", "$2a$hash", true);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThrows(AuthenticationException.class, () -> service.login("admin", "wrong"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenAccountDisabled() {
        AdminAuthService service = createService();
        AdminUser user = createUser(1L, "admin", "$2a$hash", false);
        when(adminUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThrows(AuthenticationException.class, () -> service.login("admin", "admin123"));
    }
}
