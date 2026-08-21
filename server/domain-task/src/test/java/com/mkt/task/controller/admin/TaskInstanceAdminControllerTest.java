package com.mkt.task.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.task.application.TaskInstanceAppService;
import com.mkt.task.command.InstanceAbandonCommand;
import com.mkt.task.domain.InstanceStatuses;
import com.mkt.task.response.AdminInstanceDetailResponse;
import com.mkt.task.response.AdminInstanceView;
import com.mkt.task.response.InstanceAbandonResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaskInstanceAdminControllerTest {

    @Test
    void delegatesPageDetailAndAbandon() {
        TaskInstanceAppService service = mock(TaskInstanceAppService.class);
        when(service.page(any())).thenReturn(new PageData<>(0, List.of()));
        AdminInstanceView view = new AdminInstanceView(
                1L, 2L, "once", 9L, 1, "NONE", InstanceStatuses.IN_PROGRESS, null, null, Instant.EPOCH,
                Instant.EPOCH, null, null, 0);
        when(service.get(1L)).thenReturn(new AdminInstanceDetailResponse(view, List.of(), List.of()));
        when(service.abandonAdmin(anyLong())).thenReturn(new InstanceAbandonResponse(InstanceStatuses.ABANDONED));
        TaskInstanceAdminController controller = new TaskInstanceAdminController(service);
        assertThat(controller.page(null, null, null, null, null, null, 1, 20).code()).isEqualTo(0);
        Result<AdminInstanceDetailResponse> detail = controller.get(1L);
        assertThat(detail.data().instance().id()).isEqualTo(1L);
        assertThat(controller.abandon(1L, new InstanceAbandonCommand("运营终止")).data().instanceStatus())
                .isEqualTo(InstanceStatuses.ABANDONED);
    }
}
