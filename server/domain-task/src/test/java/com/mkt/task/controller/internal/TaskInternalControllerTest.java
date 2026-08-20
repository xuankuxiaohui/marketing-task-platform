package com.mkt.task.controller.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.command.InternalCallbackCommand;
import com.mkt.task.command.InternalProgressCommand;
import com.mkt.task.response.TaskCallbackResponse;
import com.mkt.task.response.TaskProgressResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskInternalControllerTest {

    private TaskStepAppService steps;
    private TaskInternalController controller;

    @BeforeEach
    void setUp() {
        steps = mock(TaskStepAppService.class);
        controller = new TaskInternalController(steps);
    }

    @Test
    void callbackDelegatesAndReturnsSnapshot() {
        InternalCallbackCommand command = new InternalCallbackCommand(9L, null, null, null, "cb", "biz-2");
        when(steps.callback(command)).thenReturn(new TaskCallbackResponse(9L, "cb", "COMPLETED", "COMPLETED"));
        assertThat(controller.callback(command).data().stepStatus()).isEqualTo("COMPLETED");
        verify(steps).callback(command);
    }

    @Test
    void progressDelegates() {
        InternalProgressCommand command = new InternalProgressCommand(9L, null, null, null, "p", 3, "r1");
        when(steps.progress(command)).thenReturn(new TaskProgressResponse(9L, "p", 3, 10, "ACTIVE"));
        assertThat(controller.progress(command).data().progressCurrent()).isEqualTo(3);
        verify(steps).progress(command);
    }
}
