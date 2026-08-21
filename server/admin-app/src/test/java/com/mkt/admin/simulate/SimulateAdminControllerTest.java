package com.mkt.admin.simulate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.admin.controller.admin.SimulateAdminController;
import com.mkt.kernel.PageData;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.response.TaskProgressResponse;
import com.mkt.task.response.TaskStartResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulateAdminControllerTest {

    @Test
    void endpointsWrapServiceResults() {
        SimulateTaskService service = mock(SimulateTaskService.class);
        when(service.list(8L, null, 1, 20)).thenReturn(new PageData<>(0, List.of()));
        when(service.detail(8L, 3L))
                .thenReturn(new TaskDetailResponse("NONE", null, null, List.of(), null, List.of(), null));
        when(service.start(any(), anyString())).thenReturn(new TaskStartResponse(11L, "IN_PROGRESS", null));
        when(service.click(any(), anyString()))
                .thenReturn(new TaskClickResponse(11L, "COMPLETED", "IN_PROGRESS", null, List.of()));
        when(service.callback(any())).thenReturn(new TaskCallbackResponse(11L, "cb", "COMPLETED", "IN_PROGRESS"));
        when(service.progress(any())).thenReturn(new TaskProgressResponse(11L, "pg", 1, 1, "COMPLETED"));
        when(service.flow(any(), anyString()))
                .thenReturn(new SimulateFlowResponse(11L, "COMPLETED", List.of(), List.of()));
        when(service.reverse(any())).thenReturn(new SimulateReverseResponse(11L, 1, 1, 0, false));
        SimulateAdminController controller = new SimulateAdminController(service);
        assertThat(controller.list(8L, null, 1, 20).success()).isTrue();
        assertThat(controller.detail(8L, 3L).success()).isTrue();
        assertThat(controller.start(new SimulateStartCommand(8L, 3L), null).data().instanceId()).isEqualTo(11L);
        assertThat(controller.click(new SimulateClickCommand(8L, 11L, "c1"), null).success()).isTrue();
        assertThat(controller.callback(new SimulateCallbackCommand(8L, 11L, "cb", "b1")).success()).isTrue();
        assertThat(controller.progress(new SimulateProgressCommand(8L, 11L, "pg", 1, "r1")).success()).isTrue();
        assertThat(controller.flow(new SimulateFlowCommand(8L, 3L), null).data().instanceId()).isEqualTo(11L);
        assertThat(controller.reverse(new SimulateReverseCommand(11L)).data().channelRevoked()).isFalse();
    }
}
