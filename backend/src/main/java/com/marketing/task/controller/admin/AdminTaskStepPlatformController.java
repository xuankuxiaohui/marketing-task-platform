package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.vo.TaskStepPlatformVO;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/task/{taskId}")
@RequiredArgsConstructor
public class AdminTaskStepPlatformController {
    private final TaskStepPlatformMapper taskStepPlatformMapper;
    private final TaskStepMapper taskStepMapper;
    private final TaskDefinitionCacheService cacheService;

    @GetMapping("/step-platforms")
    public Result<List<TaskStepPlatformVO>> listAll(@PathVariable Long taskId) {
        List<TaskStepPlatform> configs = taskStepPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskStepPlatform>()
                        .apply("step_id IN (SELECT id FROM task_step WHERE task_id = {0})", taskId));
        return Result.ok(configs.stream().map(TaskStepPlatformVO::from).toList());
    }

    @PutMapping("/step-platforms")
    public Result<Void> batchSave(@PathVariable Long taskId,
                                  @RequestBody List<TaskStepPlatformVO> configs) {
        List<TaskStep> steps = taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>().eq(TaskStep::getTaskId, taskId));
        Set<Long> validStepIds = steps.stream().map(TaskStep::getId).collect(Collectors.toSet());

        for (TaskStepPlatformVO vo : configs) {
            if (vo.getStepId() == null || !validStepIds.contains(vo.getStepId())) continue;
            TaskStepPlatform entity = vo.toEntity();
            entity.setId(null);

            TaskStepPlatform existing = taskStepPlatformMapper.selectOne(
                    new LambdaQueryWrapper<TaskStepPlatform>()
                            .eq(TaskStepPlatform::getStepId, vo.getStepId())
                            .eq(TaskStepPlatform::getPlatform, vo.getPlatform()));

            if (existing != null) {
                entity.setId(existing.getId());
                taskStepPlatformMapper.updateById(entity);
            } else {
                taskStepPlatformMapper.insert(entity);
            }
        }
        cacheService.evict(taskId);
        return Result.ok(null);
    }
}
