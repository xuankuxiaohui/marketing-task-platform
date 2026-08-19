package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.task.command.CrowdImportCommand;
import com.mkt.task.command.CrowdSaveCommand;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import com.mkt.task.testsupport.MemoryTaskDefinitionStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskCrowdAppServiceTest {

    private MemoryTaskCrowdStore store;
    private UserAttributePort users;
    private TaskCrowdAppService service;
    private TaskSettings settings;

    @BeforeEach
    void setUp() {
        store = new MemoryTaskCrowdStore();
        users = mock(UserAttributePort.class);
        settings = new TaskSettings();
        settings.setCrowdMaxSize(3);
        service = new TaskCrowdAppService(
                store,
                new MemoryTaskDefinitionStore(),
                users,
                settings,
                mock(PlatformCache.class),
                Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
        when(users.attributes(10001L)).thenReturn(attrs());
        when(users.attributes(10002L)).thenReturn(attrs());
        when(users.attributes(10003L)).thenReturn(attrs());
        when(users.attributes(10004L)).thenReturn(UserAttributes.notFound());
    }

    @Test
    void importDedupsAndSkipsInvalid() {
        long id = service.create(new CrowdSaveCommand("vip_pack", "VIP", "ENABLED")).id();
        var result = service.importUsers(id, new CrowdImportCommand("10001\n10001\nabc\n10004\n10002\n"));
        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.deduplicated()).isEqualTo(1);
        assertThat(result.invalid()).isEqualTo(2);
        assertThat(store.getById(id).getItemCount()).isEqualTo(2);
    }

    @Test
    void importRejectsWhenExceedingMax() {
        settings.setCrowdMaxSize(2);
        long id = service.create(new CrowdSaveCommand("vip_pack", "VIP", "ENABLED")).id();
        assertThatThrownBy(() -> service.importUsers(id, new CrowdImportCommand("10001\n10002\n10003\n")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.CROWD_SIZE_EXCEEDED);
        assertThat(store.getById(id).getItemCount()).isEqualTo(0);
    }

    private static UserAttributes attrs() {
        return new UserAttributes("GD", "user", "1", 1, List.of(), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE);
    }
}
