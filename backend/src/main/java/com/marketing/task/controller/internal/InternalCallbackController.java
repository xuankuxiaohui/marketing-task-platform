package com.marketing.task.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.common.Result;
import com.marketing.task.domain.dto.CallbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.task.domain.dto.ProgressRequest;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.vo.UserTaskInstanceVO;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.step.StepAdvanceEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Internal", description = "内部回调接口")
@RestController
@RequestMapping("/api/internal/task")
@RequiredArgsConstructor
public class InternalCallbackController {
    private final UserTaskInstanceMapper instanceMapper;
    private final StepAdvanceEngine stepAdvanceEngine;

    @Operation(summary = "任务回调")
    @PostMapping("/callback")
    public Result<UserTaskInstanceVO> callback(@Valid @RequestBody CallbackRequest request) {
        UserTaskInstance instance = resolveInstance(request.getInstanceId(),
                request.getUserId(), request.getTaskId(), request.getCycleKey());
        return Result.ok(UserTaskInstanceVO.from(stepAdvanceEngine.callback(instance, request.getCallbackEventKey())));
    }

    @Operation(summary = "任务进度上报")
    @PostMapping("/progress")
    public Result<UserTaskInstanceVO> progress(@Valid @RequestBody ProgressRequest request) {
        UserTaskInstance instance = resolveInstance(request.getInstanceId(),
                request.getUserId(), request.getTaskId(), request.getCycleKey());
        return Result.ok(UserTaskInstanceVO.from(stepAdvanceEngine.progress(instance, request.getStepId(), request.getProgressValue())));
    }

    private UserTaskInstance resolveInstance(Long instanceId, String userId, Long taskId, String cycleKey) {
        if (instanceId != null) {
            UserTaskInstance inst = instanceMapper.selectById(instanceId);
            if (inst == null) throw new BusinessException(ErrorCode.INSTANCE_NOT_FOUND);
            return inst;
        }
        if (userId == null || taskId == null || cycleKey == null) {
            throw new BusinessException(ErrorCode.INSTANCE_PARAM_INSUFFICIENT);
        }
        UserTaskInstance inst = instanceMapper.selectOne(new LambdaQueryWrapper<UserTaskInstance>()
                .eq(UserTaskInstance::getUserId, userId)
                .eq(UserTaskInstance::getTaskId, taskId)
                .eq(UserTaskInstance::getCycleKey, cycleKey));
        if (inst == null) throw new BusinessException(ErrorCode.INSTANCE_NOT_FOUND);
        return inst;
    }
}
