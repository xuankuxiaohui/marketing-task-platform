package com.marketing.task.controller.internal;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.domain.dto.CallbackRequest;
import com.marketing.task.domain.dto.ProgressRequest;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.vo.UserTaskInstanceVO;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.service.step.StepAdvanceEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal/task")
@RequiredArgsConstructor
public class InternalCallbackController {
    private final UserTaskInstanceMapper instanceMapper;
    private final StepAdvanceEngine stepAdvanceEngine;

    @PostMapping("/callback")
    public Result<UserTaskInstanceVO> callback(@Valid @RequestBody CallbackRequest request) {
        UserTaskInstance instance = resolveInstance(request.getInstanceId(),
                request.getUserId(), request.getTaskId(), request.getCycleKey());
        return Result.ok(UserTaskInstanceVO.from(stepAdvanceEngine.callback(instance, request.getCallbackEventKey())));
    }

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
