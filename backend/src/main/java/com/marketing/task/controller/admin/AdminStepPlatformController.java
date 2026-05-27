package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskStepPlatform;
import com.marketing.task.domain.vo.TaskStepPlatformVO;
import com.marketing.task.mapper.TaskStepPlatformMapper;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/task/{taskId}/steps/{stepId}/platforms")
@RequiredArgsConstructor
public class AdminStepPlatformController {
    private final TaskStepPlatformMapper taskStepPlatformMapper;
    private final TaskDefinitionCacheService cacheService;

    @GetMapping
    public Result<List<TaskStepPlatformVO>> list(@PathVariable Long taskId, @PathVariable Long stepId) {
        List<TaskStepPlatform> platforms = taskStepPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskStepPlatform>()
                        .eq(TaskStepPlatform::getStepId, stepId));
        return Result.ok(platforms.stream().map(TaskStepPlatformVO::from).toList());
    }

    @PostMapping
    public Result<TaskStepPlatformVO> create(@PathVariable Long taskId, @PathVariable Long stepId,
                                             @Valid @RequestBody TaskStepPlatformVO vo) {
        TaskStepPlatform stepPlatform = vo.toEntity();
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
        return Result.ok(TaskStepPlatformVO.from(stepPlatform));
    }

    @DeleteMapping("/{stepPlatformId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long stepId,
                               @PathVariable Long stepPlatformId) {
        TaskStepPlatform existing = taskStepPlatformMapper.selectById(stepPlatformId);
        if (existing == null || !existing.getStepId().equals(stepId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "步骤端配置不存在");
        }
        taskStepPlatformMapper.deleteById(stepPlatformId);
        cacheService.evict(taskId);
        return Result.ok(null);
    }

}
