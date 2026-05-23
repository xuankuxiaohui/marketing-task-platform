package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.domain.vo.TaskPlatformVO;
import com.marketing.task.mapper.TaskPlatformMapper;
import com.marketing.task.service.task.TaskDefinitionCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/task/{taskId}/platforms")
@RequiredArgsConstructor
public class AdminPlatformController {
    private final TaskPlatformMapper taskPlatformMapper;
    private final TaskDefinitionCacheService cacheService;

    @GetMapping
    public Result<List<TaskPlatformVO>> list(@PathVariable Long taskId) {
        List<TaskPlatform> platforms = taskPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskPlatform>()
                        .eq(TaskPlatform::getTaskId, taskId));
        return Result.ok(platforms.stream().map(TaskPlatformVO::from).toList());
    }

    @GetMapping("/{platformId}")
    public Result<TaskPlatformVO> getById(@PathVariable Long taskId, @PathVariable Long platformId) {
        TaskPlatform platform = taskPlatformMapper.selectById(platformId);
        if (platform == null || !platform.getTaskId().equals(taskId)) {
            return Result.fail(ErrorCode.NOT_FOUND, "端配置不存在");
        }
        return Result.ok(TaskPlatformVO.from(platform));
    }

    @PostMapping
    public Result<TaskPlatformVO> create(@PathVariable Long taskId, @Valid @RequestBody TaskPlatformVO vo) {
        TaskPlatform platform = vo.toEntity();
        platform.setId(null);
        platform.setTaskId(taskId);

        TaskPlatform existing = taskPlatformMapper.selectOne(
                new LambdaQueryWrapper<TaskPlatform>()
                        .eq(TaskPlatform::getTaskId, taskId)
                        .eq(TaskPlatform::getPlatform, platform.getPlatform()));

        if (existing != null) {
            platform.setId(existing.getId());
            taskPlatformMapper.updateById(platform);
        } else {
            taskPlatformMapper.insert(platform);
        }
        cacheService.evict(taskId);
        return Result.ok(TaskPlatformVO.from(platform));
    }

    @PutMapping("/{platformId}")
    public Result<TaskPlatformVO> update(@PathVariable Long taskId, @PathVariable Long platformId,
                                         @Valid @RequestBody TaskPlatformVO vo) {
        TaskPlatform existing = taskPlatformMapper.selectById(platformId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(ErrorCode.NOT_FOUND, "端配置不存在");
        }
        TaskPlatform platform = vo.toEntity();
        platform.setId(platformId);
        platform.setTaskId(taskId);
        taskPlatformMapper.updateById(platform);
        cacheService.evict(taskId);
        return Result.ok(TaskPlatformVO.from(taskPlatformMapper.selectById(platformId)));
    }

    @DeleteMapping("/{platformId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long platformId) {
        TaskPlatform existing = taskPlatformMapper.selectById(platformId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(ErrorCode.NOT_FOUND, "端配置不存在");
        }
        taskPlatformMapper.deleteById(platformId);
        cacheService.evict(taskId);
        return Result.ok(null);
    }
}
