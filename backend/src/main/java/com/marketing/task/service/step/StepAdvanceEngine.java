package com.marketing.task.service.step;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.UserTaskInstance;
import com.marketing.task.domain.entity.UserTaskStepProgress;
import com.marketing.task.domain.enums.InstanceStatus;
import com.marketing.task.domain.enums.StepProgressStatus;
import com.marketing.task.domain.enums.StepType;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.UserTaskInstanceMapper;
import com.marketing.task.mapper.UserTaskStepProgressMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StepAdvanceEngine {
    private final TaskStepMapper taskStepMapper;
    private final UserTaskInstanceMapper instanceMapper;
    private final UserTaskStepProgressMapper progressMapper;
    private final List<StepHandler> handlers;

    @Transactional
    public UserTaskInstance enter(UserTaskInstance instance) {
        cascade(instance);
        return instanceMapper.selectById(instance.getId());
    }

    @Transactional
    public UserTaskInstance click(UserTaskInstance instance, Long stepId) {
        TaskStep step = taskStepMapper.selectById(stepId);
        if (step == null || !step.getTaskId().equals(instance.getTaskId())) {
            throw new BusinessException("步骤不存在");
        }
        if (!StepType.CLICK.name().equals(step.getType())) {
            throw new BusinessException("当前步骤不是CLICK类型");
        }
        completeStep(instance, step);
        cascade(instance);
        return instanceMapper.selectById(instance.getId());
    }

    private void cascade(UserTaskInstance instance) {
        List<TaskStep> steps = taskStepMapper.selectList(new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, instance.getTaskId())
                .orderByAsc(TaskStep::getSeq));
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
            completeStep(instance, current);
            if (stepType == StepType.REWARD) {
                markRewarded(instance);
                return;
            }
            instance.setCurrentStepSeq(instance.getCurrentStepSeq() + 1);
            instanceMapper.updateById(instance);
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
        instance.setCurrentStepSeq(step.getSeq() + 1);
        instanceMapper.updateById(instance);
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
