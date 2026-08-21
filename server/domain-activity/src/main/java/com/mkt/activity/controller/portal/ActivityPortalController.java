package com.mkt.activity.controller.portal;

import com.mkt.activity.application.ActivityPortalAppService;
import com.mkt.activity.response.ParticipateResponse;
import com.mkt.activity.response.PortalActivityDetailView;
import com.mkt.activity.response.PortalActivityView;
import com.mkt.activity.support.ActivityErrorCodes;
import com.mkt.activity.support.ActivitySettings;
import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/activity")
@Tag(name = "activity")
public class ActivityPortalController {

    private final ActivityPortalAppService portal;
    private final SlidingWindowRateLimiter limiter;
    private final ActivitySettings settings;

    public ActivityPortalController(
            ActivityPortalAppService portal,
            ObjectProvider<SlidingWindowRateLimiter> limiter,
            ActivitySettings settings) {
        this.portal = portal;
        this.limiter = limiter.getIfAvailable();
        this.settings = settings;
    }

    @GetMapping("/activities")
    @Operation(summary = "C 端进行中活动")
    public Result<List<PortalActivityView>> activities() {
        long userId = UserContext.require().userId();
        return Result.ok(portal.listPublished(userId));
    }

    @GetMapping("/{activityId}")
    @Operation(summary = "C 端活动详情（ETag）")
    public ResponseEntity<Result<PortalActivityDetailView>> detail(
            @PathVariable long activityId,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {
        long userId = UserContext.require().userId();
        PortalActivityDetailView view = portal.detail(activityId, userId);
        String etag = view.contentHash() == null ? "" : view.contentHash();
        if (etagMatches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(Result.ok(view));
    }

    @PostMapping("/{activityId}/participate")
    @Operation(summary = "参与活动")
    public Result<ParticipateResponse> participate(
            @PathVariable long activityId,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        long userId = UserContext.require().userId();
        rateLimit(userId);
        return Result.ok(portal.participate(activityId, userId, remoteIp(request), deviceId));
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
                        RateLimitDim.USER, "activity:" + userId, 1, settings.portalWritePerSecond())) {
            throw new BusinessException(ActivityErrorCodes.RATE_LIMITED);
        }
    }

    private static boolean etagMatches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank() || etag == null || etag.isBlank()) {
            return false;
        }
        String incoming = ifNoneMatch.trim();
        if (incoming.startsWith("W/")) {
            incoming = incoming.substring(2).trim();
        }
        if (incoming.length() >= 2 && incoming.startsWith("\"") && incoming.endsWith("\"")) {
            incoming = incoming.substring(1, incoming.length() - 1);
        }
        return etag.equals(incoming);
    }
}
