package com.mkt.task.controller.portal;

import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.task.application.TaskClaimAppService;
import com.mkt.task.application.TaskInstanceAppService;
import com.mkt.task.application.TaskPortalAppService;
import com.mkt.task.application.TaskStepAppService;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.response.MineTaskView;
import com.mkt.task.response.TaskCardView;
import com.mkt.task.response.TaskClickResponse;
import com.mkt.task.response.TaskDetailResponse;
import com.mkt.task.response.TaskStartResponse;
import com.mkt.task.support.ClientPlatformResolver;
import com.mkt.task.support.TaskErrorCodes;
import com.mkt.task.support.TaskSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/task")
@Tag(name = "task")
public class TaskPortalController {

    private final TaskPortalAppService portal;
    private final TaskClaimAppService claims;
    private final TaskStepAppService steps;
    private final TaskInstanceAppService instances;
    private final SlidingWindowRateLimiter limiter;
    private final TaskSettings settings;
    private final ClientPlatformResolver platforms;

    public TaskPortalController(
            TaskPortalAppService portal,
            TaskClaimAppService claims,
            TaskStepAppService steps,
            TaskInstanceAppService instances,
            ObjectProvider<SlidingWindowRateLimiter> limiter,
            TaskSettings settings,
            ClientPlatformResolver platforms) {
        this.portal = portal;
        this.claims = claims;
        this.steps = steps;
        this.instances = instances;
        this.limiter = limiter.getIfAvailable();
        this.settings = settings;
        this.platforms = platforms;
    }

    @GetMapping("/list")
    @Operation(summary = "C 端任务投放列表（匿名可访问公开卡，R32.1）")
    public Result<PageData<TaskCardView>> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        Long userId = UserContext.current().map(UserPrincipal::userId).orElse(null);
        return Result.ok(portal.list(userId, category, page, pageSize));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的任务")
    public Result<PageData<MineTaskView>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        long userId = UserContext.require().userId();
        return Result.ok(portal.mine(userId, status, category, page, pageSize));
    }

    @GetMapping("/{taskId}/detail")
    @Operation(summary = "C 端任务详情三分渲染（匿名可访问公开卡，R32.1）")
    public Result<TaskDetailResponse> detail(
            @PathVariable long taskId,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        Long userId = UserContext.current().map(UserPrincipal::userId).orElse(null);
        return Result.ok(portal.detail(taskId, userId, platforms.resolve(platform)));
    }

    @PostMapping("/{taskId}/start")
    @Operation(summary = "领取任务")
    public Result<TaskStartResponse> start(
            @PathVariable long taskId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform,
            HttpServletRequest request) {
        long userId = UserContext.require().userId();
        if (limiter != null
                && !limiter.tryAcquire(
                        RateLimitDim.USER, "claim:" + userId, 1, settings.portalWritePerSecond())) {
            throw new BusinessException(TaskErrorCodes.CLAIM_RATE_LIMITED);
        }
        String ip = remoteIp(request);
        String device = deviceId == null || deviceId.isBlank() ? null : deviceId.trim();
        return Result.ok(claims.start(taskId, userId, ip, device, platforms.resolve(platform)));
    }

    @PostMapping("/instances/{instanceId}/steps/{stepCode}/click")
    @Operation(summary = "完成点击步骤")
    public Result<TaskClickResponse> click(
            @PathVariable long instanceId,
            @PathVariable String stepCode,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform,
            HttpServletRequest request) {
        long userId = UserContext.require().userId();
        String ip = remoteIp(request);
        String device = deviceId == null || deviceId.isBlank() ? null : deviceId.trim();
        return Result.ok(steps.click(instanceId, stepCode, userId, ip, device, platforms.resolve(platform)));
    }

    @PostMapping("/instances/{instanceId}/abandon")
    @Operation(summary = "用户放弃进行中任务")
    public Result<InstanceAbandonResponse> abandon(
            @PathVariable long instanceId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        long userId = UserContext.require().userId();
        String ip = remoteIp(request);
        String device = deviceId == null || deviceId.isBlank() ? null : deviceId.trim();
        return Result.ok(instances.abandonUser(instanceId, userId, ip, device));
    }

    private static String remoteIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return null;
        }
        String ip = request.getRemoteAddr();
        return "0.0.0.0".equals(ip) ? null : ip;
    }
}
