package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/task/{taskId}/steps/{stepId}/platforms")
@RequiredArgsConstructor
public class AdminStepPlatformController {
    private final TaskStepPlatformMapper taskStepPlatformMapper;

    @GetMapping
    public Result<List<TaskStepPlatform>> list(@PathVariable Long taskId, @PathVariable Long stepId) {
        return Result.ok(taskStepPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskStepPlatform>()
                        .eq(TaskStepPlatform::getStepId, stepId)));
    }

    @PostMapping
    public Result<TaskStepPlatform> create(@PathVariable Long taskId, @PathVariable Long stepId,
                                           @Valid @RequestBody TaskStepPlatform stepPlatform) {
        stepPlatform.setId(null);
        stepPlatform.setStepId(stepId);

        TaskStepPlatform existing = taskStepPlatformMapper.selectOne(
                new LambdaQueryWrapper<TaskStepPlatform>()
                        .eq(TaskStepPlatform::getStepId, stepId)
                        .eq(TaskStepPlatform::getPlatform, stepPlatform.getPlatform()));

        if (existing != null) {
            stepPlatform.setId(existing.getId());
            taskStepPlatformMapper.updateById(stepPlatform);
        } else {
            taskStepPlatformMapper.insert(stepPlatform);
        }
        return Result.ok(stepPlatform);
    }

    @DeleteMapping("/{stepPlatformId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long stepId,
                               @PathVariable Long stepPlatformId) {
        TaskStepPlatform existing = taskStepPlatformMapper.selectById(stepPlatformId);
        if (existing == null || !existing.getStepId().equals(stepId)) {
            return Result.fail(404, "步骤端配置不存在");
        }
        taskStepPlatformMapper.deleteById(stepPlatformId);
        return Result.ok(null);
    }
}
