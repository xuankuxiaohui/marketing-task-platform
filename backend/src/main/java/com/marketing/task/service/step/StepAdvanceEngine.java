package com.marketing.task.service.step;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.marketing.common.BusinessException;
import com.marketing.common.ErrorCode;
import com.marketing.common.EventType;
import com.marketing.context.UserContext;
import com.marketing.context.UserContextHolder;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepTransition;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.entity.UserTaskStepProgress;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.StepProgressStatus;
import com.marketing.task.domain.enums.StepType;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.TaskStepTransitionMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.mapper.UserTaskStepProgressMapper;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.service.filter.FilterExpressionEngine;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StepAdvanceEngine {
    private final TaskStepMapper taskStepMapper;
    private final UserTaskInstanceMapper instanceMapper;
    private final UserTaskStepProgressMapper progressMapper;
    private final List<StepHandler> handlers;
    private final TaskDefinitionCacheService cacheService;
    private final EventTrackingService eventTrackingService;
    private final TaskStepTransitionMapper transitionMapper;
    private final FilterExpressionEngine filterExpressionEngine;

    @Transactional
    public UserTaskInstance enter(UserTaskInstance instance) {
        cascade(instance);
        return instanceMapper.selectById(instance.getId());
    }

    @Transactional
    public UserTaskInstance click(UserTaskInstance instance, Long stepId) {
        TaskStep step = taskStepMapper.selectById(stepId);
        if (step == null || !step.getTaskId().equals(instance.getTaskId())) {
            throw new BusinessException(ErrorCode.STEP_NOT_FOUND);
        }
        if (!StepType.CLICK.name().equals(step.getType())) {
            throw new BusinessException(ErrorCode.STEP_TYPE_MISMATCH, "当前步骤不是CLICK类型");
        }
        completeStep(instance, step);
        cascade(instance);
        return instanceMapper.selectById(instance.getId());
    }

    @Transactional
    public UserTaskInstance callback(UserTaskInstance instance, String callbackEventKey) {
        TaskStep step = taskStepMapper.selectOne(new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, instance.getTaskId())
                .eq(TaskStep::getSeq, instance.getCurrentStepSeq()));
        if (step == null) {
            throw new BusinessException(ErrorCode.STEP_NOT_FOUND);
        }
        if (!StepType.CALLBACK.name().equals(step.getType())) {
            throw new BusinessException(ErrorCode.STEP_TYPE_MISMATCH, "当前步骤不是CALLBACK类型");
        }
        if (step.getCallbackEventKey() == null || !step.getCallbackEventKey().equals(callbackEventKey)) {
            throw new BusinessException(ErrorCode.CALLBACK_KEY_MISMATCH);
        }
        completeStep(instance, step);
        cascade(instance);
        return instanceMapper.selectById(instance.getId());
    }

    @Transactional
    public UserTaskInstance progress(UserTaskInstance instance, Long stepId, int progressValue) {
        TaskStep step = taskStepMapper.selectById(stepId);
        if (step == null || !step.getTaskId().equals(instance.getTaskId())) {
            throw new BusinessException(ErrorCode.STEP_NOT_FOUND);
        }
        if (!StepType.PROGRESS.name().equals(step.getType())) {
            throw new BusinessException(ErrorCode.STEP_TYPE_MISMATCH, "当前步骤不是PROGRESS类型");
        }

        UserTaskStepProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<UserTaskStepProgress>()
                .eq(UserTaskStepProgress::getInstanceId, instance.getId())
                .eq(UserTaskStepProgress::getStepId, step.getId()));

        if (progress != null && StepProgressStatus.COMPLETED.name().equals(progress.getStatus())) {
            return instanceMapper.selectById(instance.getId());
        }

        if (progress == null) {
            progress = new UserTaskStepProgress();
            progress.setInstanceId(instance.getId());
            progress.setStepId(step.getId());
        }

        progress.setProgressValue(progressValue);

        if (step.getTargetValue() != null && progressValue >= step.getTargetValue()) {
            // Reach target: complete the step
            progress.setStatus(StepProgressStatus.COMPLETED.name());
            progress.setCompleteTime(LocalDateTime.now());
            if (progress.getId() == null) {
                progressMapper.insert(progress);
            } else {
                progressMapper.updateById(progress);
            }
            Integer nextSeq = resolveNextSeq(step, instance);
            instance.setCurrentStepSeq(nextSeq);
            instanceMapper.updateById(instance);
            cascade(instance);
        } else {
            // Still in progress
            progress.setStatus(StepProgressStatus.IN_PROGRESS.name());
            if (progress.getId() == null) {
                progressMapper.insert(progress);
            } else {
                progressMapper.updateById(progress);
            }
            markInProgress(instance);
        }

        return instanceMapper.selectById(instance.getId());
    }

    private void cascade(UserTaskInstance instance) {
        List<TaskStep> steps = cacheService.getSteps(instance.getTaskId());
        Map<StepType, StepHandler> handlerMap = handlerMapByType();
        while (true) {
            TaskStep current = steps.stream()
                    .filter(step -> step.getSeq().equals(instance.getCurrentStepSeq()))
                    .findFirst()
                    .orElse(null);
            if (current == null) {
                markCompleted(instance);
                return;
            }
            StepType stepType = StepType.valueOf(current.getType());
            if (stepType != StepType.PASSIVE && stepType != StepType.REWARD) {
                markInProgress(instance);
                return;
            }
            StepHandler handler = handlerMap.get(stepType);
            if (handler != null) {
                handler.onStepEnter(StepContext.builder().instance(instance).step(current).build());
            }
            int seqBefore = instance.getCurrentStepSeq();
            completeStep(instance, current);
            if (stepType == StepType.REWARD) {
                markRewarded(instance);
                return;
            }
            // Only advance if completeStep was a no-op (step already completed)
            if (instance.getCurrentStepSeq() == seqBefore) {
                Integer nextSeq = resolveNextSeq(current, instance);
                instance.setCurrentStepSeq(nextSeq);
                instanceMapper.updateById(instance);
            }
        }
    }

    private Map<StepType, StepHandler> handlerMapByType() {
        return handlers.stream().collect(Collectors.toMap(StepHandler::supports, Function.identity(), (a, b) -> a));
    }

    private void completeStep(UserTaskInstance instance, TaskStep step) {
        UserTaskStepProgress progress = progressMapper.selectOne(new LambdaQueryWrapper<UserTaskStepProgress>()
                .eq(UserTaskStepProgress::getInstanceId, instance.getId())
                .eq(UserTaskStepProgress::getStepId, step.getId()));
        if (progress != null && StepProgressStatus.COMPLETED.name().equals(progress.getStatus())) {
            return;
        }
        if (progress == null) {
            progress = new UserTaskStepProgress();
            progress.setInstanceId(instance.getId());
            progress.setStepId(step.getId());
            progress.setProgressValue(0);
        }
        progress.setStatus(StepProgressStatus.COMPLETED.name());
        progress.setCompleteTime(LocalDateTime.now());
        if (progress.getId() == null) {
            progressMapper.insert(progress);
        } else {
            progressMapper.updateById(progress);
        }
        Integer nextSeq = resolveNextSeq(step, instance);
        instance.setCurrentStepSeq(nextSeq);
        instanceMapper.updateById(instance);
        eventTrackingService.track(EventType.STEP_COMPLETED, instance.getTaskId(), instance.getId(),
                step.getId(), instance.getUserId(), null, Map.of("stepType", step.getType()));
    }

    /**
     * Resolve the next step sequence number based on conditional branching transitions.
     * If no transitions are defined for the step, falls back to linear seq+1 (backward compatible).
     * Transitions are evaluated in priority order (ASC). The first matching transition's target
     * step seq is returned. A transition with a null/blank conditionExpr is treated as the default
     * (always matches). If no transition matches, falls back to linear seq+1.
     */
    private Integer resolveNextSeq(TaskStep currentStep, UserTaskInstance instance) {
        List<TaskStepTransition> transitions = cacheService.getTransitions(instance.getTaskId());

        List<TaskStepTransition> stepTransitions = transitions.stream()
                .filter(t -> t.getStepId().equals(currentStep.getId()))
                .sorted(Comparator.comparingInt(TaskStepTransition::getPriority))
                .toList();

        if (stepTransitions.isEmpty()) {
            return currentStep.getSeq() + 1;
        }

        List<TaskStep> steps = cacheService.getSteps(instance.getTaskId());
        Map<Long, TaskStep> stepMap = steps.stream()
                .collect(Collectors.toMap(TaskStep::getId, Function.identity()));

        UserContext userContext = UserContextHolder.get();

        for (TaskStepTransition transition : stepTransitions) {
            if (transition.getConditionExpr() == null || transition.getConditionExpr().isBlank()) {
                // Default transition: no condition, always matches
                TaskStep target = stepMap.get(transition.getTargetStepId());
                if (target != null) {
                    return target.getSeq();
                }
            } else {
                try {
                    boolean matched = filterExpressionEngine.evaluate(transition.getConditionExpr(), userContext);
                    if (matched) {
                        TaskStep target = stepMap.get(transition.getTargetStepId());
                        if (target != null) {
                            return target.getSeq();
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Transition evaluation error for step {}: {}", currentStep.getId(), ex.getMessage());
                }
            }
        }

        // Fallback: linear advance
        return currentStep.getSeq() + 1;
    }

    private void markInProgress(UserTaskInstance instance) {
        if (InstanceStatus.PENDING.name().equals(instance.getStatus())) {
            instance.setStatus(InstanceStatus.IN_PROGRESS.name());
            if (instance.getStartTime() == null) {
                instance.setStartTime(LocalDateTime.now());
            }
            instanceMapper.updateById(instance);
        }
    }

    private void markCompleted(UserTaskInstance instance) {
        instanceMapper.update(null, new LambdaUpdateWrapper<UserTaskInstance>()
                .eq(UserTaskInstance::getId, instance.getId())
                .set(UserTaskInstance::getStatus, InstanceStatus.COMPLETED.name())
                .set(UserTaskInstance::getCompleteTime, LocalDateTime.now()));
    }

    private void markRewarded(UserTaskInstance instance) {
        instanceMapper.update(null, new LambdaUpdateWrapper<UserTaskInstance>()
                .eq(UserTaskInstance::getId, instance.getId())
                .set(UserTaskInstance::getStatus, InstanceStatus.REWARDED.name())
                .set(UserTaskInstance::getCompleteTime, LocalDateTime.now())
                .set(UserTaskInstance::getRewardTime, LocalDateTime.now()));
    }
}
