package com.mkt.reward.controller.portal;

import com.mkt.infra.ratelimit.RateLimitDim;
import com.mkt.infra.ratelimit.SlidingWindowRateLimiter;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.RateLimitedException;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import com.mkt.reward.application.ClaimAppService;
import com.mkt.reward.application.PrizePortalAppService;
import com.mkt.reward.response.ClaimResponse;
import com.mkt.reward.response.PrizeCardView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/prize")
@Tag(name = "prize")
public class PrizePortalController {

    private static final int DEFAULT_PORTAL_WRITE_PER_SECOND = 10;

    private final PrizePortalAppService portal;
    private final ClaimAppService claims;
    private final SlidingWindowRateLimiter limiter;

    public PrizePortalController(
            PrizePortalAppService portal,
            ClaimAppService claims,
            ObjectProvider<SlidingWindowRateLimiter> limiter) {
        this.portal = portal;
        this.claims = claims;
        this.limiter = limiter.getIfAvailable();
    }

    @GetMapping("/list")
    @Operation(summary = "我的奖品")
    public Result<PageData<PrizeCardView>> list(
            @RequestParam(required = false) String tab,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        long userId = UserContext.require().userId();
        return Result.ok(portal.list(userId, tab, page, pageSize));
    }

    @PostMapping("/records/{id}/claim")
    @Operation(summary = "领取奖品")
    public Result<ClaimResponse> claim(@PathVariable long id) {
        long userId = UserContext.require().userId();
        if (limiter != null
                && !limiter.tryAcquire(RateLimitDim.USER, "prize-claim:" + userId, 1, DEFAULT_PORTAL_WRITE_PER_SECOND)) {
            throw new RateLimitedException(CommonErrorCodes.RATE_LIMITED, 1);
        }
        return Result.ok(claims.claim(id, userId));
    }
}
