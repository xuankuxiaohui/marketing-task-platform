package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskStep;
import com.marketing.task.domain.vo.TaskStepVO;
import com.marketing.task.mapper.TaskStepMapper;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/task/{taskId}/steps")
@RequiredArgsConstructor
public class AdminStepController {
    private final TaskStepMapper taskStepMapper;
    private final TaskDefinitionCacheService cacheService;

    @GetMapping
    public Result<List<TaskStepVO>> list(@PathVariable Long taskId) {
        List<TaskStep> steps = taskStepMapper.selectList(
                new LambdaQueryWrapper<TaskStep>()
                        .eq(TaskStep::getTaskId, taskId)
                        .orderByAsc(TaskStep::getSeq));
        return Result.ok(steps.stream().map(TaskStepVO::from).toList());
    }

    @GetMapping("/{stepId}")
    public Result<TaskStepVO> getById(@PathVariable Long taskId, @PathVariable Long stepId) {
        TaskStep step = taskStepMapper.selectById(stepId);
        if (step == null || !step.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "步骤不存在");
        }
        return Result.ok(TaskStepVO.from(step));
    }

    @PostMapping
    public Result<TaskStepVO> create(@PathVariable Long taskId, @Valid @RequestBody TaskStepVO vo) {
        TaskStep step = vo.toEntity();
        step.setId(null);
        step.setTaskId(taskId);
        if (step.getSeq() == null) {
            Integer maxSeq = taskStepMapper.selectMaxSeq(taskId);
            step.setSeq(maxSeq == null ? 1 : maxSeq + 1);
        }
        taskStepMapper.insert(step);
        cacheService.evict(taskId);
        return Result.ok(TaskStepVO.from(step));
    }

    @PutMapping("/{stepId}")
    public Result<TaskStepVO> update(@PathVariable Long taskId, @PathVariable Long stepId,
                                     @Valid @RequestBody TaskStepVO vo) {
        TaskStep existing = taskStepMapper.selectById(stepId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "步骤不存在");
        }
        TaskStep step = vo.toEntity();
        step.setId(stepId);
        step.setTaskId(taskId);
        taskStepMapper.updateById(step);
        cacheService.evict(taskId);
        return Result.ok(TaskStepVO.from(taskStepMapper.selectById(stepId)));
    }

    @DeleteMapping("/{stepId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long stepId) {
        TaskStep existing = taskStepMapper.selectById(stepId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "步骤不存在");
        }
        taskStepMapper.deleteById(stepId);
        cacheService.evict(taskId);
        return Result.ok(null);
    }

    @GetMapping("/check-code")
    public Result<Map<String, Boolean>> checkCode(@PathVariable Long taskId,
                                                   @RequestParam String code,
                                                   @RequestParam(required = false) Long excludeStepId) {
        var query = new LambdaQueryWrapper<TaskStep>()
                .eq(TaskStep::getTaskId, taskId)
                .eq(TaskStep::getCode, code);
        if (excludeStepId != null) {
            query.ne(TaskStep::getId, excludeStepId);
        }
        boolean valid = taskStepMapper.selectCount(query) == 0;
        return Result.ok(Map.of("valid", valid));
    }

    @PutMapping("/reorder")
    public Result<Void> reorder(@PathVariable Long taskId,
                                @RequestBody List<Map<String, Object>> items) {
        for (int i = 0; i < items.size(); i++) {
            Map<String, Object> item = items.get(i);
            Long stepId = ((Number) item.get("id")).longValue();
            int seq = ((Number) item.get("seq")).intValue();
            TaskStep step = new TaskStep();
            step.setId(stepId);
            step.setSeq(seq);
            taskStepMapper.updateById(step);
        }
        cacheService.evict(taskId);
        return Result.ok(null);
    }
}
