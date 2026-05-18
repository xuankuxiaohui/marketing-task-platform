package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskPlatform;
import com.marketing.task.mapper.TaskPlatformMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/task/{taskId}/platforms")
@RequiredArgsConstructor
public class AdminPlatformController {
    private final TaskPlatformMapper taskPlatformMapper;

    @GetMapping
    public Result<List<TaskPlatform>> list(@PathVariable Long taskId) {
        return Result.ok(taskPlatformMapper.selectList(
                new LambdaQueryWrapper<TaskPlatform>()
                        .eq(TaskPlatform::getTaskId, taskId)));
    }

    @GetMapping("/{platformId}")
    public Result<TaskPlatform> getById(@PathVariable Long taskId, @PathVariable Long platformId) {
        TaskPlatform platform = taskPlatformMapper.selectById(platformId);
        if (platform == null || !platform.getTaskId().equals(taskId)) {
            return Result.fail(404, "端配置不存在");
        }
        return Result.ok(platform);
    }

    @PostMapping
    public Result<TaskPlatform> create(@PathVariable Long taskId, @Valid @RequestBody TaskPlatform platform) {
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
        return Result.ok(platform);
    }

    @PutMapping("/{platformId}")
    public Result<TaskPlatform> update(@PathVariable Long taskId, @PathVariable Long platformId,
                                       @Valid @RequestBody TaskPlatform platform) {
        TaskPlatform existing = taskPlatformMapper.selectById(platformId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(404, "端配置不存在");
        }
        platform.setId(platformId);
        platform.setTaskId(taskId);
        taskPlatformMapper.updateById(platform);
        return Result.ok(taskPlatformMapper.selectById(platformId));
    }

    @DeleteMapping("/{platformId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long platformId) {
        TaskPlatform existing = taskPlatformMapper.selectById(platformId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(404, "端配置不存在");
        }
        taskPlatformMapper.deleteById(platformId);
        return Result.ok(null);
    }
}
