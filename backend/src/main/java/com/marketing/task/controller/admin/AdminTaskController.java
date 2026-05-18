package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.Result;
import com.marketing.task.domain.dto.TaskAggregateDTO;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.mapper.TaskMapper;
import com.marketing.task.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/task")
@RequiredArgsConstructor
public class AdminTaskController {
    private final TaskMapper taskMapper;
    private final TaskService taskService;

    @GetMapping
    public Result<IPage<Task>> page(@RequestParam(defaultValue = "1") long page,
                                    @RequestParam(defaultValue = "20") long size) {
        return Result.ok(taskMapper.selectPage(Page.of(page, size), null));
    }

    @PostMapping
    public Result<Task> save(@Valid @RequestBody TaskAggregateDTO dto) {
        Task saved = taskService.saveAggregate(dto);
        return Result.ok(saved);
    }

    @GetMapping("/{id}")
    public Result<Task> getById(@PathVariable Long id) {
        return Result.ok(taskService.requireTask(id));
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        taskService.publish(id);
        return Result.ok(null);
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        taskService.offline(id);
        return Result.ok(null);
    }
}
