package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.common.Result;
import com.marketing.task.domain.entity.TaskFilter;
import com.marketing.task.mapper.TaskFilterMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/task/{taskId}/filters")
@RequiredArgsConstructor
public class AdminFilterCrudController {
    private final TaskFilterMapper taskFilterMapper;

    @GetMapping
    public Result<List<TaskFilter>> list(@PathVariable Long taskId) {
        return Result.ok(taskFilterMapper.selectList(
                new LambdaQueryWrapper<TaskFilter>()
                        .eq(TaskFilter::getTaskId, taskId)
                        .orderByAsc(TaskFilter::getSeq)));
    }

    @GetMapping("/{filterId}")
    public Result<TaskFilter> getById(@PathVariable Long taskId, @PathVariable Long filterId) {
        TaskFilter filter = taskFilterMapper.selectById(filterId);
        if (filter == null || !filter.getTaskId().equals(taskId)) {
            return Result.fail(404, "过滤器不存在");
        }
        return Result.ok(filter);
    }

    @PostMapping
    public Result<TaskFilter> create(@PathVariable Long taskId, @Valid @RequestBody TaskFilter filter) {
        filter.setId(null);
        filter.setTaskId(taskId);
        if (filter.getSeq() == null) {
            Integer maxSeq = taskFilterMapper.selectList(
                    new LambdaQueryWrapper<TaskFilter>()
                            .eq(TaskFilter::getTaskId, taskId)
                            .orderByDesc(TaskFilter::getSeq)
                            .last("LIMIT 1"))
                    .stream()
                    .findFirst()
                    .map(TaskFilter::getSeq)
                    .orElse(null);
            filter.setSeq(maxSeq == null ? 1 : maxSeq + 1);
        }
        taskFilterMapper.insert(filter);
        return Result.ok(filter);
    }

    @PutMapping("/{filterId}")
    public Result<TaskFilter> update(@PathVariable Long taskId, @PathVariable Long filterId,
                                     @Valid @RequestBody TaskFilter filter) {
        TaskFilter existing = taskFilterMapper.selectById(filterId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(404, "过滤器不存在");
        }
        filter.setId(filterId);
        filter.setTaskId(taskId);
        taskFilterMapper.updateById(filter);
        return Result.ok(taskFilterMapper.selectById(filterId));
    }

    @DeleteMapping("/{filterId}")
    public Result<Void> delete(@PathVariable Long taskId, @PathVariable Long filterId) {
        TaskFilter existing = taskFilterMapper.selectById(filterId);
        if (existing == null || !existing.getTaskId().equals(taskId)) {
            return Result.fail(404, "过滤器不存在");
        }
        taskFilterMapper.deleteById(filterId);
        return Result.ok(null);
    }
}
