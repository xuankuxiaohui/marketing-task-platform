package com.marketing.system.service.auth;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.common.BusinessException;
import com.marketing.system.domain.entity.ClientUser;
import com.marketing.system.mapper.ClientUserMapper;
import com.marketing.security.AuthenticationException;
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
    private PasswordEncoder passwordEncoder;
    @Mock
    private StpLogic clientStpLogic;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, ClientUser.class);
    }

    private ClientAuthService createService() {
        return new ClientAuthService(clientUserMapper, passwordEncoder, clientStpLogic);
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
        ClientAuthService service = createService();
        ClientUser user = createUser(1L, "demo", "$2a$hash", true);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("demo123", "$2a$hash")).thenReturn(true);
        when(clientStpLogic.getTokenValue()).thenReturn("sa-token-xxx");

        ClientAuthService.LoginResult result = service.login("demo", "demo123");

        assertEquals("sa-token-xxx", result.token());
        assertEquals("1", result.userId());
        assertEquals("demo", result.username());
        assertEquals("BJ", result.province());
        assertEquals("vip", result.role());
        verify(clientStpLogic).login(eq("1"), any(SaLoginModel.class));
        verify(clientStpLogic).getTokenValue();
    }

    @Test
    void login_shouldThrowAuthenticationException_whenUserNotFound() {
        ClientAuthService service = createService();
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThrows(AuthenticationException.class, () -> service.login("nobody", "pass"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenPasswordWrong() {
        ClientAuthService service = createService();
        ClientUser user = createUser(1L, "demo", "$2a$hash", true);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThrows(AuthenticationException.class, () -> service.login("demo", "wrong"));
    }

    @Test
    void login_shouldThrowAuthenticationException_whenAccountDisabled() {
        ClientAuthService service = createService();
        ClientUser user = createUser(1L, "demo", "$2a$hash", false);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        assertThrows(AuthenticationException.class, () -> service.login("demo", "demo123"));
    }

    @Test
    void register_shouldCreateUserAndReturnLoginResult() {
        ClientAuthService service = createService();
        ClientUser user = createUser(3L, "newuser", "$2a$newhash", true);
        user.setNickname(null);

        when(clientUserMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(false);
        when(clientUserMapper.insert(any(ClientUser.class))).thenReturn(1);
        when(clientUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.encode("password")).thenReturn("$2a$newhash");
        when(passwordEncoder.matches("password", "$2a$newhash")).thenReturn(true);
        when(clientStpLogic.getTokenValue()).thenReturn("sa-newuser-token");

        ClientAuthService.LoginResult result = service.register(
                new ClientAuthService.RegisterRequest("newuser", "password", null,
                        "BJ", "vip", "vip,active", "org_001", 5));

        assertEquals("sa-newuser-token", result.token());
        verify(clientUserMapper).insert(any(ClientUser.class));
    }

    @Test
    void register_shouldThrowBusinessException_whenUsernameExists() {
        ClientAuthService service = createService();
        when(clientUserMapper.exists(any(LambdaQueryWrapper.class))).thenReturn(true);

        assertThrows(BusinessException.class, () -> service.register(
                new ClientAuthService.RegisterRequest("duplicate", "pass", null,
                        null, null, null, null, null)));
    }

    @Test
    void getById_shouldReturnUser() {
        ClientAuthService service = createService();
        ClientUser user = createUser(1L, "demo", "hash", true);
        when(clientUserMapper.selectById(1L)).thenReturn(user);

        ClientUser result = service.getById(1L);

        assertEquals("demo", result.getUsername());
    }
}
