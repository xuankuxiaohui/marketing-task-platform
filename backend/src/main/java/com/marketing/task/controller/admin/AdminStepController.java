package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.mapper.TaskStepMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/task/{taskId}/steps")
@RequiredArgsConstructor
public class AdminStepController {
    private final TaskStepMapper taskStepMapper;

    @GetMapping
    public Result<List<TaskStep>> list(@PathVariable Long taskId) {
        return Result.ok(taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>()
                        .eq(TaskStep::getTaskId, taskId)
                        .orderByAsc(TaskStep::getSeq)));
    }

    @GetMapping("/{stepId}")
    public Result<TaskStep> getById(@PathVariable Long taskId, @PathVariable Long stepId) {
        TaskStep step = taskStepMapper.selectById(stepId);
        if (step == null || !step.getTaskId().equals(taskId)) {
            return Result.fail(404, "步骤不存在");
        }
        return Result.ok(step);
    }

    @PostMapping
    public Result<TaskStep> create(@PathVariable Long taskId, @Valid @RequestBody TaskStep step) {
        step.setId(null);
        step.setTaskId(taskId);
        if (step.getSeq() == null) {
            Integer maxSeq = taskStepMapper.selectMaxSeq(taskId);
            step.setSeq(maxSeq == null ? 1 : maxSeq + 1);
        }
        taskStepMapper.insert(step);
        return Result.ok(step);
    }

    @PutMapping("/{stepId}")
    public Result<TaskStep> update(@PathVariable Long taskId, @PathVariable Long stepId,
                                   @Valid @RequestBody TaskStep step) {
        TaskStep existing = taskStepMapper.selectById(stepId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(404, "步骤不存在");
        }
        step.setId(stepId);
        step.setTaskId(taskId);
        taskStepMapper.updateById(step);
        return Result.ok(taskStepMapper.selectById(stepId));
    }

    @DeleteMapping("/{stepId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long stepId) {
        TaskStep existing = taskStepMapper.selectById(stepId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(404, "步骤不存在");
        }
        taskStepMapper.deleteById(stepId);
        return Result.ok(null);
    }
}
