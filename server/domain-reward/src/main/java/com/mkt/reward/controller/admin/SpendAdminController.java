package com.mkt.reward.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.Result;
import com.mkt.reward.application.SpendAppService;
import com.mkt.reward.response.SpendResponse;
import com.mkt.reward.support.RewardPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reward/spend")
@Tag(name = "reward")
public class SpendAdminController {

    private final SpendAppService spend;

    public SpendAdminController(SpendAppService spend) {
        this.spend = spend;
    }

    @GetMapping
    @SaCheckPermission(RewardPermissions.RECORD_QUERY)
    @Operation(summary = "花销汇总", description = "权限 reward:record:query；simulated=0")
    public Result<SpendResponse> spend(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) Long prizeId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return Result.ok(spend.spend(categoryCode, prizeId, from, to));
    }
}
