package com.marketing.task.activity.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.marketing.task.activity.domain.dto.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.domain.entity.ActivityStats;
import com.marketing.task.activity.mapper.ActivityDisplayRuleMapper;
import com.marketing.task.activity.mapper.ActivityMapper;
import com.marketing.task.activity.mapper.ActivityStatsMapper;
import com.marketing.task.activity.service.ActivityService;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import com.marketing.task.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Admin - Activities", description = "活动管理")
@RestController
@RequestMapping("/api/admin/activities")
@RequiredArgsConstructor
public class AdminActivityController {

    private final ActivityService activityService;
    private final ActivityMapper activityMapper;
    private final ActivityDisplayRuleMapper displayRuleMapper;
    private final ActivityStatsMapper activityStatsMapper;
    private final OperationLogService operationLogService;

    @Operation(summary = "分页查询活动列表")
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

    @Operation(summary = "查询活动详情")
    @GetMapping("/{id}")
    public Result<ActivityDetailVO> getDetail(@PathVariable Long id) {
        return Result.ok(activityService.getDetail(id));
    }

    @Operation(summary = "查询活动展示规则")
    @GetMapping("/{id}/display-rule")
    public Result<DisplayRuleVO> getDisplayRule(@PathVariable Long id) {
        ActivityDisplayRule rule = activityService.getDisplayRule(id);
        return Result.ok(toDisplayRuleVO(rule));
    }

    @Operation(summary = "创建活动")
    @PostMapping
    public Result<ActivityDetailVO> create(@RequestBody @Valid ActivityCreateRequest request) {
        String operatorId = UserContextHolder.get().getUserId();
        ActivityDetailVO detail = activityService.create(request, operatorId);
        operationLogService.record(operatorId, "CREATE", "ACTIVITY", detail.getId(), detail.getName(), null);
        return Result.ok(detail);
    }

    @Operation(summary = "更新活动")
    @PutMapping("/{id}")
    public Result<ActivityDetailVO> update(@PathVariable Long id, @RequestBody @Valid ActivityUpdateRequest request) {
        String operatorId = UserContextHolder.get().getUserId();
        ActivityDetailVO detail = activityService.update(id, request, operatorId);
        operationLogService.record(operatorId, "UPDATE", "ACTIVITY", id, detail.getName(), null);
        return Result.ok(detail);
    }

    @Operation(summary = "更新活动展示规则")
    @PutMapping("/{id}/display-rule")
    public Result<Void> updateDisplayRule(@PathVariable Long id, @RequestBody @Valid DisplayRuleUpdateRequest request) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.updateDisplayRule(id, request.getContent(), operatorId);
        operationLogService.record(operatorId, "UPDATE_DISPLAY_RULE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @Operation(summary = "删除活动")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.delete(id, operatorId);
        operationLogService.record(operatorId, "DELETE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @Operation(summary = "发布活动")
    @PostMapping("/{id}/publish")
    public Result<Void> publish(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.publish(id, operatorId);
        operationLogService.record(operatorId, "PUBLISH", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @Operation(summary = "下线活动")
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.offline(id, operatorId);
        operationLogService.record(operatorId, "OFFLINE", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @Operation(summary = "活动退回草稿")
    @PostMapping("/{id}/back-to-draft")
    public Result<Void> backToDraft(@PathVariable Long id) {
        String operatorId = UserContextHolder.get().getUserId();
        activityService.backToDraft(id, operatorId);
        operationLogService.record(operatorId, "BACK_TO_DRAFT", "ACTIVITY", id, null, null);
        return Result.ok(null);
    }

    @Operation(summary = "查询活动子模块")
    @GetMapping("/{id}/sub-modules")
    public Result<ActivitySubModulesVO> getSubModules(@PathVariable Long id) {
        return Result.ok(activityService.getSubModules(id));
    }

    @Operation(summary = "查询活动参与统计")
    @GetMapping("/{id}/participation-stats")
    public Result<List<ActivityDailyVO>> participationStats(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Activity activity = activityMapper.selectById(id);
        if (activity == null) {
            return Result.ok(List.of());
        }
        LambdaQueryWrapper<ActivityStats> wrapper = new LambdaQueryWrapper<ActivityStats>()
                .eq(ActivityStats::getActivityCode, activity.getCode());
        if (startDate != null) {
            wrapper.ge(ActivityStats::getStatDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(ActivityStats::getStatDate, endDate);
        }
        wrapper.orderByAsc(ActivityStats::getStatDate);
        List<ActivityStats> stats = activityStatsMapper.selectList(wrapper);
        List<ActivityDailyVO> vos = stats.stream().map(s -> {
            ActivityDailyVO vo = new ActivityDailyVO();
            vo.setActivityCode(s.getActivityCode());
            vo.setStatDate(s.getStatDate());
            vo.setParticipantCount(s.getParticipantCount());
            vo.setCompletionCount(s.getCompletionCount());
            vo.setRewardCount(s.getRewardCount());
            return vo;
        }).toList();
        return Result.ok(vos);
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
