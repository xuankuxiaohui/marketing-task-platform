package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.kernel.BusinessException;
import com.mkt.task.command.MutexGroupSaveCommand;
import com.mkt.task.entity.TaskDefinitionEntity;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.testsupport.MemoryTaskDefinitionStore;
import com.mkt.task.testsupport.MemoryTaskMutexGroupStore;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskMutexGroupAppServiceTest {

    private MemoryTaskMutexGroupStore store;
    private MemoryTaskDefinitionStore definitions;
    private TaskMutexGroupAppService service;

    @BeforeEach
    void setUp() {
        store = new MemoryTaskMutexGroupStore();
        definitions = new MemoryTaskDefinitionStore();
        service = new TaskMutexGroupAppService(
                store, definitions, Clock.fixed(Instant.parse("2026-08-19T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createAndRejectDeleteWhenInUse() {
        var created = service.create(new MutexGroupSaveCommand("mutex_a", "组A", true));
        assertThat(created.crossCycle()).isTrue();
        TaskDefinitionEntity task = new TaskDefinitionEntity();
        task.setCode("t1");
        task.setName("t");
        task.setStatus("DRAFT");
        task.setDeleted(0);
        task.setMutexGroupId(created.id());
        task.setCycleType("DAILY");
        definitions.insert(task);
        assertThatThrownBy(() -> service.delete(created.id()))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(TaskErrorCodes.MUTEX_IN_USE);
    }
}
