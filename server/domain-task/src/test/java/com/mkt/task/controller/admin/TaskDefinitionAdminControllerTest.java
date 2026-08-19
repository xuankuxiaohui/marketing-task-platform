package com.mkt.task.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.task.application.TaskCrowdAppService;
import com.mkt.task.application.TaskDefinitionAppService;
import com.mkt.task.application.TaskExpressionAppService;
import com.mkt.task.application.TaskMutexGroupAppService;
import com.mkt.task.command.CrowdImportCommand;
import com.mkt.task.command.CrowdSaveCommand;
import com.mkt.task.command.ExpressionValidateCommand;
import com.mkt.task.command.MutexGroupSaveCommand;
import com.mkt.task.command.TaskCopyCommand;
import com.mkt.task.response.CrowdImportResponse;
import com.mkt.task.response.CrowdResponse;
import com.mkt.task.response.ExpressionValidateResponse;
import com.mkt.task.response.MutexGroupResponse;
import com.mkt.task.response.OkResponse;
import com.mkt.task.response.TaskDefinitionSaveResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskDefinitionAdminControllerTest {

    @Test
    void controllersDelegate() {
        TaskDefinitionAppService defs = mock(TaskDefinitionAppService.class);
        when(defs.page(any())).thenReturn(new PageData<>(0, List.of()));
        when(defs.saveAggregate(any())).thenReturn(new TaskDefinitionSaveResponse(1, "a", 0, "DRAFT"));
        when(defs.copy(any(Long.class), any())).thenReturn(new TaskDefinitionSaveResponse(2, "b", 0, "DRAFT"));
        TaskDefinitionAdminController defCtl = new TaskDefinitionAdminController(defs);
        assertThat(defCtl.page(null, null, null, null, 1, 20).code()).isEqualTo(0);
        Result<TaskDefinitionSaveResponse> copied = defCtl.copy(1, new TaskCopyCommand("copy_me", "副本"));
        assertThat(copied.data().id()).isEqualTo(2);
        assertThat(defCtl.delete(1).data()).isEqualTo(OkResponse.yes());

        TaskExpressionAppService expr = mock(TaskExpressionAppService.class);
        when(expr.validate(any())).thenReturn(new ExpressionValidateResponse(true, null, List.of()));
        assertThat(new TaskExpressionAdminController(expr)
                        .validate(new ExpressionValidateCommand("1 = 1", "FILTER"))
                        .data()
                        .valid())
                .isTrue();

        TaskMutexGroupAppService mutex = mock(TaskMutexGroupAppService.class);
        when(mutex.page(any())).thenReturn(new PageData<>(0, List.of()));
        when(mutex.create(any())).thenReturn(new MutexGroupResponse(1, "mutex_a", "n", false, Instant.EPOCH));
        TaskMutexGroupAdminController mutexCtl = new TaskMutexGroupAdminController(mutex);
        assertThat(mutexCtl.create(new MutexGroupSaveCommand("mutex_a", "n", false)).data().id()).isEqualTo(1);
        assertThat(mutexCtl.page(1, 20).data().total()).isZero();

        TaskCrowdAppService crowds = mock(TaskCrowdAppService.class);
        when(crowds.create(any())).thenReturn(new CrowdResponse(3, "vip_1", "v", 0, "ENABLED", Instant.EPOCH));
        when(crowds.importUsers(any(Long.class), any())).thenReturn(new CrowdImportResponse(1, 0, 0));
        TaskCrowdAdminController crowdCtl = new TaskCrowdAdminController(crowds);
        assertThat(crowdCtl.create(new CrowdSaveCommand("vip_1", "v", "ENABLED")).data().id()).isEqualTo(3);
        assertThat(crowdCtl.importUsers(3, new CrowdImportCommand("1")).data().imported()).isEqualTo(1);
    }
}
