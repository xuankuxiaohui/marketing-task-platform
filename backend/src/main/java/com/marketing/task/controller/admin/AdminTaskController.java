package com.marketing.task.controller.admin;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.common.Result;
import com.marketing.task.domain.dto.TaskAggregateDTO;
import com.marketing.task.domain.entity.Task;
import com.marketing.task.domain.vo.TaskAdminVO;
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
    public Result<IPage<TaskAdminVO>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "20") long size) {
        IPage<Task> taskPage = taskMapper.selectPage(Page.of(page, size), null);
        return Result.ok(taskPage.convert(TaskAdminVO::from));
    }

    @PostMapping
    public Result<TaskAdminVO> save(@Valid @RequestBody TaskAggregateDTO dto) {
        return Result.ok(taskService.saveAggregate(dto));
    }

    @GetMapping("/{id}")
    public Result<TaskAdminVO> getById(@PathVariable Long id) {
        return Result.ok(TaskAdminVO.from(taskService.requireTask(id)));
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
