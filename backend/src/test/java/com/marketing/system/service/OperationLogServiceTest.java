package com.marketing.system.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.marketing.system.domain.entity.AdminUser;
import com.marketing.system.domain.entity.OperationLog;
import com.marketing.system.mapper.AdminUserMapper;
import com.marketing.system.mapper.OperationLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OperationLogServiceTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @Mock
    private AdminUserMapper adminUserMapper;

    private OperationLogService service;

    @BeforeAll
    static void initMybatisPlus() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        TableInfoHelper.initTableInfo(assistant, OperationLog.class);
    }

    @BeforeEach
    void setUp() {
        service = new OperationLogService(operationLogMapper, adminUserMapper);
    }

    @Test
    void shouldInsertOperationLogOnRecord() {
        service.record("1", "CREATE", "TASK", 100L, "测试任务", "{\"name\":\"test\"}");

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        OperationLog log = captor.getValue();
        assertEquals("1", log.getOperatorId());
        assertEquals("CREATE", log.getOperationType());
        assertEquals("TASK", log.getTargetType());
        assertEquals(100L, log.getTargetId());
        assertEquals("测试任务", log.getTargetName());
        assertEquals("{\"name\":\"test\"}", log.getDetail());
    }

    @Test
    void shouldResolveOperatorNameFromAdminUser() {
        AdminUser user = new AdminUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickname("管理员");
        when(adminUserMapper.selectById(1L)).thenReturn(user);

        service.record("1", "UPDATE", "TASK", 100L, "任务", null);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertEquals("管理员", captor.getValue().getOperatorName());
    }

    @Test
    void shouldFallbackToUsernameWhenNicknameNull() {
        AdminUser user = new AdminUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setNickname(null);
        when(adminUserMapper.selectById(1L)).thenReturn(user);

        service.record("1", "UPDATE", "TASK", 100L, "任务", null);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertEquals("admin", captor.getValue().getOperatorName());
    }

    @Test
    void shouldUseOperatorIdWhenUserNotFound() {
        when(adminUserMapper.selectById(1L)).thenReturn(null);

        service.record("1", "DELETE", "MUTEX_GROUP", 200L, "互斥组", null);

        ArgumentCaptor<OperationLog> captor = ArgumentCaptor.forClass(OperationLog.class);
        verify(operationLogMapper).insert(captor.capture());
        assertEquals("1", captor.getValue().getOperatorName());
    }

    @Test
    void shouldNotThrowOnMapperException() {
        doThrow(new RuntimeException("DB error")).when(operationLogMapper).insert(any(OperationLog.class));

        assertDoesNotThrow(() ->
                service.record("1", "PUBLISH", "TASK", 300L, "发布任务", null)
        );

        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    void shouldRecordAllOperationTypes() {
        String[] types = {"CREATE", "UPDATE", "PUBLISH", "OFFLINE", "DELETE"};
        for (String type : types) {
            service.record("1", type, "TASK", 1L, "test", null);
        }
        verify(operationLogMapper, times(types.length)).insert(any(OperationLog.class));
    }
}
