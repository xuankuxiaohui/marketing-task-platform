package com.mkt.signin.controller.portal;

import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import com.mkt.signin.application.SigninPortalAppService;
import com.mkt.signin.command.CatchupCommand;
import com.mkt.signin.response.PortalActivityView;
import com.mkt.signin.response.SigninActionResponse;
import com.mkt.signin.response.SigninCalendarResponse;
import com.mkt.signin.support.SigninErrorCodes;
import com.mkt.signin.support.SigninSettings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/signin")
@Tag(name = "signin")
public class SigninPortalController {

    private final SigninPortalAppService portal;
    private final SlidingWindowRateLimiter limiter;
    private final SigninSettings settings;

    public SigninPortalController(
            SigninPortalAppService portal,
            ObjectProvider<SlidingWindowRateLimiter> limiter,
            SigninSettings settings) {
        this.portal = portal;
        this.limiter = limiter.getIfAvailable();
        this.settings = settings;
    }

    @GetMapping("/activities")
    @Operation(summary = "C 端进行中签到活动")
    public Result<List<PortalActivityView>> activities() {
        UserContext.require();
        return Result.ok(portal.listPublished(null));
    }

    @GetMapping("/{activityId}/calendar")
    @Operation(summary = "当月签到日历")
    public Result<SigninCalendarResponse> calendar(
            @PathVariable long activityId, @RequestParam(required = false) String yearMonth) {
        long userId = UserContext.require().userId();
        return Result.ok(portal.calendar(activityId, userId, yearMonth));
    }

    @PostMapping("/{activityId}/checkin")
    @Operation(summary = "今日签到")
    public Result<SigninActionResponse> checkin(
            @PathVariable long activityId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        long userId = UserContext.require().userId();
        rateLimit(userId);
        return Result.ok(portal.checkin(activityId, userId, remoteIp(request), deviceId));
    }

    @PostMapping("/{activityId}/catchup")
    @Operation(summary = "补签")
    public Result<SigninActionResponse> catchup(
            @PathVariable long activityId,
            @Valid @RequestBody CatchupCommand command,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        long userId = UserContext.require().userId();
        rateLimit(userId);
        return Result.ok(portal.catchup(activityId, userId, command.signDate(), remoteIp(request), deviceId));
    }

    private static String remoteIp(HttpServletRequest request) {
        if (request == null || request.getRemoteAddr() == null || request.getRemoteAddr().isBlank()) {
            return null;
        }
        String ip = request.getRemoteAddr();
        return "0.0.0.0".equals(ip) ? null : ip;
    }

    private void rateLimit(long userId) {
        if (limiter != null
                && !limiter.tryAcquire(
                        RateLimitDim.USER, "signin:" + userId, 1, settings.portalWritePerSecond())) {
            throw new BusinessException(SigninErrorCodes.RATE_LIMITED);
        }
    }
}
