package com.marketing.task.service.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.AdminUser;
import com.marketing.task.mapper.AdminUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminUserManagementService service;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, AdminUser.class);
    }

    @BeforeEach
    void setUp() {
        service = new AdminUserManagementService(adminUserMapper, passwordEncoder);
    }

    @Test
    void create_withValidData_shouldPersist() {
        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(adminUserMapper.insert(any(AdminUser.class))).thenReturn(1);

        AdminUser result = service.create("newuser", "password123", "新用户");

        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals("$2a$encoded", result.getPasswordHash());
        assertEquals("新用户", result.getNickname());
        assertTrue(result.getEnabled());

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserMapper).insert(captor.capture());
        assertEquals("newuser", captor.getValue().getUsername());
    }

    @Test
    void create_withDuplicateUsername_shouldThrow() {
        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.create("admin", "password123", "管理员"));
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    void create_shouldEncodePassword() {
        when(adminUserMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(passwordEncoder.encode("mypassword")).thenReturn("$2a$hashed");
        when(adminUserMapper.insert(any(AdminUser.class))).thenReturn(1);

        service.create("user1", "mypassword", null);

        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserMapper).insert(captor.capture());
        assertEquals("$2a$hashed", captor.getValue().getPasswordHash());
        verify(passwordEncoder).encode("mypassword");
    }
}
