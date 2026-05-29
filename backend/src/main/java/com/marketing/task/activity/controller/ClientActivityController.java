package com.marketing.task.activity.controller;

import com.marketing.task.activity.domain.dto.*;
import com.marketing.task.activity.domain.entity.Activity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.service.ActivityCacheService;
import com.marketing.task.activity.service.ActivityRuleService;
import com.marketing.task.activity.service.ActivityService;
import com.marketing.task.activity.service.ParticipationLogService;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@Slf4j
@Tag(name = "Client - Activities", description = "C端活动")
@RestController
@RequestMapping("/api/client/activities")
@RequiredArgsConstructor
public class ClientActivityController {

    private final ActivityService activityService;
    private final ActivityCacheService cacheService;
    private final ActivityRuleService ruleService;
    private final ParticipationLogService participationLogService;

    @Operation(summary = "获取活动列表")
    @GetMapping
    public Result<List<ActivityListVO>> list() {
        List<Activity> activities = activityService.listOnlineActivities();
        List<ActivityListVO> vos = activities.stream().map(a -> {
            ActivityListVO vo = new ActivityListVO();
            vo.setId(a.getId());
            vo.setCode(a.getCode());
            vo.setName(a.getName());
            vo.setStatus(a.getStatus());
            vo.setStartTime(a.getStartTime());
            vo.setEndTime(a.getEndTime());
            vo.setHasDisplayRule(cacheService.getDisplayRule(a.getCode()) != null);
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    @Operation(summary = "获取活动详情")
    @GetMapping("/{id}")
    public Result<ActivityDetailVO> getDetail(@PathVariable Long id) {
        ActivityDetailVO detail = activityService.getDetail(id);
        String userId = UserContextHolder.get().getUserId();
        Activity activity = cacheService.getActivity(id);
        long userIdLong;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的用户ID");
        }
        if (!activityService.isUserInGray(activity, userIdLong)) {
            throw new BusinessException(ErrorCode.ACTIVITY_GRAY_NOT_VISIBLE);
        }
        return Result.ok(detail);
    }

    @Operation(summary = "获取活动展示规则")
    @GetMapping("/{id}/display-rule")
    public ResponseEntity<?> getDisplayRule(@PathVariable Long id, @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        Activity activity = cacheService.getActivity(id);
        if (activity == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        ActivityDisplayRule rule = cacheService.getDisplayRule(activity.getCode());
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (rule.getContentHash() != null && rule.getContentHash().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(rule.getContentHash())
                    .build();
        }
        DisplayRuleVO vo = new DisplayRuleVO();
        vo.setId(rule.getId());
        vo.setActivityCode(rule.getActivityCode());
        vo.setContent(rule.getContent());
        vo.setContentHash(rule.getContentHash());
        vo.setUpdatedAt(rule.getUpdatedAt());
        vo.setUpdatedBy(rule.getUpdatedBy());
        return ResponseEntity.ok()
                .eTag(rule.getContentHash())
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(30)))
                .body(vo);
    }

    @Operation(summary = "参与活动")
    @PostMapping("/{id}/participate")
    public Result<RuleCheckResult> participate(@PathVariable Long id, HttpServletRequest request) {
        String userId = UserContextHolder.get().getUserId();
        Activity activity = cacheService.getActivity(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        ParticipationContext context = new ParticipationContext();
        long userIdLong;
        try {
            userIdLong = Long.parseLong(userId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的用户ID");
        }
        context.setUserId(userIdLong);
        context.setClientIp(getClientIp(request));

        RuleCheckResult result = ruleService.check(activity, context);

        try {
            participationLogService.recordParticipation(activity.getCode(), userIdLong, context.getClientIp(), result);
        } catch (Exception e) {
            log.warn("记录活动参与日志异常，不影响主流程: activityCode={}", activity.getCode(), e);
        }

        if (!result.isPassed()) {
            return Result.fail(ErrorCode.ACTIVITY_RULE_CHECK_FAILED, result.getFailMessage());
        }
        return Result.ok(result);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty()) {
            ip = ip.split(",")[0].trim();
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
