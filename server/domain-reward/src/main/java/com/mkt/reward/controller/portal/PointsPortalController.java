package com.mkt.reward.controller.portal;

import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.response.PointsBalanceResponse;
import com.mkt.reward.response.PointsPortalTxView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/points")
@Tag(name = "points")
public class PointsPortalController {

    private final PointsAppService points;

    public PointsPortalController(PointsAppService points) {
        this.points = points;
    }

    @GetMapping("/balance")
    @Operation(summary = "积分余额")
    public Result<PointsBalanceResponse> balance() {
        long userId = UserContext.require().userId();
        return Result.ok(points.balance(userId));
    }

    @GetMapping("/transactions")
    @Operation(summary = "积分流水")
    public Result<PageData<PointsPortalTxView>> transactions(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        long userId = UserContext.require().userId();
        return Result.ok(points.pagePortal(userId, type, page, pageSize));
    }
}
