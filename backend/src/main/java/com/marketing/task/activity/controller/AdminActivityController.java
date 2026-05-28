package com.marketing.task.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.activity.domain.dto.ActivityDetailVO;
import com.marketing.task.activity.domain.dto.ActivitySubModulesVO;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.service.ActivityService;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<Page<Activity>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return Result.ok(activityService.list(page, size, status));
    }

    @GetMapping("/{id}")
    public Result<ActivityDetailVO> getDetail(@PathVariable Long id) {
        return Result.ok(activityService.getDetail(id));
    }

    @GetMapping("/{id}/display-rule")
    public Result<ActivityDisplayRule> getDisplayRule(@PathVariable Long id) {
        return Result.ok(activityService.getDisplayRule(id));
    }

    @PostMapping
    public Result<Activity> create(@RequestBody Activity activity) {
        String operatorId = UserContextHolder.get().getUserId();
        Activity created = activityService.create(activity, operatorId);
        operationLogService.record(operatorId, "CREATE", "ACTIVITY", created.getId(), created.getName(), null);
        return Result.ok(created);
    }

    @PutMapping("/{id}")
    public Result<Activity> update(@PathVariable Long id, @RequestBody Activity activity) {
        String operatorId = UserContextHolder.get().getUserId();
        Activity updated = activityService.update(id, activity, operatorId);
        operationLogService.record(operatorId, "UPDATE", "ACTIVITY", id, updated.getName(), null);
        return Result.ok(updated);
    }

    @PutMapping("/{id}/display-rule")
    public Result<Void> updateDisplayRule(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        String operatorId = UserContextHolder.get().getUserId();
        String content = body.get("content");
        activityService.updateDisplayRule(id, content, operatorId);
        operationLogService.record(operatorId, "UPDATE_DISPLAY_RULE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.delete(id, operatorId);
        operationLogService.record(operatorId, "DELETE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.publish(id, operatorId);
        operationLogService.record(operatorId, "PUBLISH", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.offline(id, operatorId);
        operationLogService.record(operatorId, "OFFLINE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @GetMapping("/{id}/sub-modules")
    public Result<ActivitySubModulesVO> getSubModules(@PathVariable Long id) {
        return Result.ok(activityService.getSubModules(id));
    }
}
