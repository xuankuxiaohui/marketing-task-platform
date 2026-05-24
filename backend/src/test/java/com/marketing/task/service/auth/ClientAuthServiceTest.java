package com.marketing.task.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.ClientUser;
import com.marketing.task.mapper.ClientUserMapper;
import com.marketing.task.security.AuthenticationException;
import com.marketing.task.security.ClientJwtProvider;
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
class ClientAuthServiceTest {

    @Mock
    private ClientUserMapper clientUserMapper;
    @Mock
    private ClientJwtProvider clientJwtProvider;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ClientAuthService service;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, ClientUser.class);
    }

    private ClientAuthService createService() {
        return new ClientAuthService(clientUserMapper, clientJwtProvider, passwordEncoder);
    }

    private ClientUser createUser(Long id, String username, String passwordHash, Boolean enabled) {
        ClientUser user = new ClientUser();
        user.setId(id);
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setNickname("Nick" + id);
        user.setProvince("BJ");
        user.setRole("vip");
        user.setTags("vip,active");
        user.setOrgId("org_001");
        user.setLevel(5);
        user.setEnabled(enabled);
        return user;
    }

    @Test
    void login_shouldReturnTokenAndUserInfo_whenCredentialsValid() {
        service = createService();
        ClientUser user = createUser(1L, "demo", "$2a$hash", true);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("demo123", "$2a$hash")).thenReturn(true);
        when(clientJwtProvider.issue(any())).thenReturn("jwt-token-xxx");

        ClientAuthService.LoginResult result = service.login("demo", "demo123");

        assertEquals("jwt-token-xxx", result.token());
        assertEquals("1", result.userId());
        assertEquals("demo", result.username());
    }

    @Test
    void login_shouldThrowAuthenticationException_whenUserNotFound() {
        service = createService();
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(AuthenticationException.class, () -> service.login("nobody", "pass"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenPasswordWrong() {
        service = createService();
        ClientUser user = createUser(1L, "demo", "$2a$hash", true);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThrows(AuthenticationException.class, () -> service.login("demo", "wrong"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenAccountDisabled() {
        service = createService();
        ClientUser user = createUser(1L, "demo", "$2a$hash", false);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThrows(AuthenticationException.class, () -> service.login("demo", "demo123"));
    }

    @Test
    void register_shouldCreateUserAndReturnLoginResult() {
        service = createService();
        ClientUser user = createUser(3L, "newuser", "$2a$newhash", true);
        user.setNickname(null);

        when(clientUserMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(clientUserMapper.insert(any(ClientUser.class))).thenReturn(1);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.encode("password")).thenReturn("$2a$newhash");
        when(passwordEncoder.matches("password", "$2a$newhash")).thenReturn(true);
        when(clientJwtProvider.issue(any())).thenReturn("jwt-newuser-token");

        ClientAuthService.LoginResult result = service.register(
                new ClientAuthService.RegisterRequest("newuser", "password", null,
                        "BJ", "vip", "vip,active", "org_001", 5));

        assertEquals("jwt-newuser-token", result.token());
        verify(clientUserMapper).insert(any(ClientUser.class));
    }

    @Test
    void register_shouldThrowBusinessException_whenUsernameExists() {
        service = createService();
        when(clientUserMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.register(
                new ClientAuthService.RegisterRequest("duplicate", "pass", null,
                        null, null, null, null, null)));
    }

    @Test
    void getById_shouldReturnUser() {
        service = createService();
        ClientUser user = createUser(1L, "demo", "hash", true);
        when(clientUserMapper.selectById(1L)).thenReturn(user);

        ClientUser result = service.getById(1L);

        assertEquals("demo", result.getUsername());
    }
}
