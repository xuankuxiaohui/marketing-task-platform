package com.marketing.task.activity.controller;

import com.marketing.task.activity.domain.dto.ActivityDetailVO;
import com.marketing.task.activity.domain.dto.ActivityListVO;
import com.marketing.task.activity.domain.dto.ParticipationContext;
import com.marketing.task.activity.domain.dto.RuleCheckResult;
import com.marketing.task.activity.domain.entity.Activity;
import com.marketing.task.activity.domain.entity.ActivityDisplayRule;
import com.marketing.task.activity.service.ActivityCacheService;
import com.marketing.task.activity.service.ActivityRuleService;
import com.marketing.task.activity.service.ActivityService;
import com.marketing.task.common.BusinessException;
import com.marketing.task.common.ErrorCode;
import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/api/client/activities")
@RequiredArgsConstructor
public class ClientActivityController {

    private final ActivityService activityService;
    private final ActivityCacheService cacheService;
    private final ActivityRuleService ruleService;

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
            vo.setHasDisplayRule(cacheService.getDisplayRule(a.getId()) != null);
            return vo;
        }).toList();
        return Result.ok(vos);
    }

    @GetMapping("/{id}")
    public Result<ActivityDetailVO> getDetail(@PathVariable Long id) {
        ActivityDetailVO detail = activityService.getDetail(id);
        String userId = UserContextHolder.get().getUserId();
        Activity activity = cacheService.getActivity(id);
        if (!activityService.isUserInGray(activity, Long.parseLong(userId))) {
            throw new BusinessException(ErrorCode.ACTIVITY_GRAY_NOT_VISIBLE);
        }
        return Result.ok(detail);
    }

    @GetMapping("/{id}/display-rule")
    public ResponseEntity<?> getDisplayRule(@PathVariable Long id, @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        ActivityDisplayRule rule = cacheService.getDisplayRule(id);
        if (rule == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (rule.getContentHash() != null && rule.getContentHash().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(rule.getContentHash())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(rule.getContentHash())
                .cacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofMinutes(30)))
                .body(rule);
    }

    @PostMapping("/{id}/participate")
    public Result<RuleCheckResult> participate(@PathVariable Long id, HttpServletRequest request) {
        String userId = UserContextHolder.get().getUserId();
        Activity activity = cacheService.getActivity(id);
        if (activity == null) {
            throw new BusinessException(ErrorCode.ACTIVITY_NOT_FOUND);
        }

        ParticipationContext context = new ParticipationContext();
        context.setUserId(Long.parseLong(userId));
        context.setClientIp(getClientIp(request));

        RuleCheckResult result = ruleService.check(activity, context);
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
