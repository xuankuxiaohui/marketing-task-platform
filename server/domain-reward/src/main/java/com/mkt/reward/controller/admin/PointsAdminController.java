package com.mkt.reward.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.command.PointsAdjustCommand;
import com.mkt.reward.query.PointsAccountQuery;
import com.mkt.reward.query.PointsTransactionQuery;
import com.mkt.reward.response.PointsAccountView;
import com.mkt.reward.response.PointsBalanceResponse;
import com.mkt.reward.response.PointsTransactionView;
import com.mkt.reward.support.PointsPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/points")
@Tag(name = "points")
public class PointsAdminController {

    private final PointsAppService points;

    public PointsAdminController(PointsAppService points) {
        this.points = points;
    }

    @GetMapping("/accounts")
    @SaCheckPermission(PointsPermissions.ACCOUNT_QUERY)
    @Operation(summary = "分页查询积分账户", description = "权限 points:account:query")
    public Result<PageData<PointsAccountView>> accounts(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(points.pageAccounts(new PointsAccountQuery(userId, PageQuery.of(page, pageSize))));
    }

    @PostMapping("/accounts/adjust")
    @SaCheckPermission(PointsPermissions.ACCOUNT_ADJUST)
    @Audited(module = "points", action = "account-adjust")
    @Operation(summary = "调整积分", description = "权限 points:account:adjust")
    public Result<PointsBalanceResponse> adjust(@Valid @RequestBody PointsAdjustCommand command) {
        return Result.ok(points.adjust(command));
    }

    @GetMapping("/transactions")
    @SaCheckPermission(PointsPermissions.TRANSACTION_QUERY)
    @Operation(summary = "分页查询积分流水", description = "权限 points:transaction:query")
    public Result<PageData<PointsTransactionView>> transactions(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(points.pageTransactions(
                new PointsTransactionQuery(userId, type, from, to, PageQuery.of(page, pageSize))));
    }
}
