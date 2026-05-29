package com.marketing.task.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.activity.domain.dto.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.mapper.ActivityDisplayRuleMapper;
import com.marketing.task.activity.service.ActivityService;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;
    private final ActivityDisplayRuleMapper displayRuleMapper;
    private final OperationLogService operationLogService;

    @GetMapping
    public Result<Page<ActivityListVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        Page<Activity> activityPage = activityService.list(page, size, status);
        List<String> codes = activityPage.getRecords().stream().map(Activity::getCode).toList();
        Map<String, ActivityDisplayRule> ruleMap = codes.isEmpty() ? java.util.Collections.emptyMap() :
                displayRuleMapper.selectList(
                        new LambdaQueryWrapper<ActivityDisplayRule>()
                                .in(ActivityDisplayRule::getActivityCode, codes))
                        .stream().collect(java.util.stream.Collectors.toMap(ActivityDisplayRule::getActivityCode, r -> r, (a, b) -> a));
        Page<ActivityListVO> voPage = new Page<>(activityPage.getCurrent(), activityPage.getSize(), activityPage.getTotal());
        voPage.setRecords(activityPage.getRecords().stream().map(a -> toActivityListVO(a, ruleMap)).toList());
        return Result.ok(voPage);
    }

    @GetMapping("/{id}")
    public Result<ActivityDetailVO> getDetail(@PathVariable Long id) {
        return Result.ok(activityService.getDetail(id));
    }

    @GetMapping("/{id}/display-rule")
    public Result<DisplayRuleVO> getDisplayRule(@PathVariable Long id) {
        ActivityDisplayRule rule = activityService.getDisplayRule(id);
        return Result.ok(toDisplayRuleVO(rule));
    }

    @PostMapping
    public Result<ActivityDetailVO> create(@RequestBody @Valid ActivityCreateRequest request) {
        String operatorId = UserContextHolder.get().getUserId();
        ActivityDetailVO detail = activityService.create(request, operatorId);
        operationLogService.record(operatorId, "CREATE", "ACTIVITY", detail.getId(), detail.getName(), null);
        return Result.ok(detail);
    }

    @PutMapping("/{id}")
    public Result<ActivityDetailVO> update(@PathVariable Long id, @RequestBody @Valid ActivityUpdateRequest request) {
        String operatorId = UserContextHolder.get().getUserId();
        ActivityDetailVO detail = activityService.update(id, request, operatorId);
        operationLogService.record(operatorId, "UPDATE", "ACTIVITY", id, detail.getName(), null);
        return Result.ok(detail);
    }

    @PutMapping("/{id}/display-rule")
    public Result<Void> updateDisplayRule(@PathVariable Long id, @RequestBody @Valid DisplayRuleUpdateRequest request) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.updateDisplayRule(id, request.getContent(), operatorId);
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

    @PostMapping("/{id}/back-to-draft")
    public Result<Void> backToDraft(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.backToDraft(id, operatorId);
        operationLogService.record(operatorId, "BACK_TO_DRAFT", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @GetMapping("/{id}/sub-modules")
    public Result<ActivitySubModulesVO> getSubModules(@PathVariable Long id) {
        return Result.ok(activityService.getSubModules(id));
    }

    private ActivityListVO toActivityListVO(Activity a, Map<String, ActivityDisplayRule> ruleMap) {
        ActivityListVO vo = new ActivityListVO();
        vo.setId(a.getId());
        vo.setCode(a.getCode());
        vo.setName(a.getName());
        vo.setStatus(a.getStatus());
        vo.setStartTime(a.getStartTime());
        vo.setEndTime(a.getEndTime());
        vo.setHasDisplayRule(ruleMap.containsKey(a.getCode()));
        return vo;
    }

    private DisplayRuleVO toDisplayRuleVO(ActivityDisplayRule rule) {
        DisplayRuleVO vo = new DisplayRuleVO();
        vo.setId(rule.getId());
        vo.setActivityCode(rule.getActivityCode());
        vo.setContent(rule.getContent());
        vo.setContentHash(rule.getContentHash());
        vo.setUpdatedAt(rule.getUpdatedAt());
        vo.setUpdatedBy(rule.getUpdatedBy());
        return vo;
    }
}
