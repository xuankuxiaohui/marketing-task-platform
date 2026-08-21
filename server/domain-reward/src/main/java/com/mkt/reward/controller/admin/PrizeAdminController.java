package com.mkt.reward.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.reward.application.PrizeAppService;
import com.mkt.reward.command.PrizeConfirmCommand;
import com.mkt.reward.command.PrizeSaveCommand;
import com.mkt.reward.command.StockReplenishCommand;
import com.mkt.reward.query.PrizeQuery;
import com.mkt.reward.response.IdResponse;
import com.mkt.reward.response.OkResponse;
import com.mkt.reward.response.PrizeImpactResponse;
import com.mkt.reward.response.PrizeResponse;
import com.mkt.reward.response.StockLogView;
import com.mkt.reward.response.StockReplenishResponse;
import com.mkt.reward.support.RewardPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reward/prizes")
@Tag(name = "reward")
public class PrizeAdminController {

    private final PrizeAppService appService;

    public PrizeAdminController(PrizeAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(RewardPermissions.PRIZE_QUERY)
    @Operation(summary = "分页查询奖品", description = "权限 reward:prize:query")
    public Result<PageData<PrizeResponse>> page(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(
                new PrizeQuery(code, name, categoryCode, status, groupId, PageQuery.of(page, pageSize))));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(RewardPermissions.PRIZE_QUERY)
    @Operation(summary = "奖品详情", description = "权限 reward:prize:query")
    public Result<PrizeResponse> get(@PathVariable long id) {
        return Result.ok(appService.get(id));
    }

    @PostMapping
    @SaCheckPermission(RewardPermissions.PRIZE_CREATE)
    @Audited(module = "reward", action = "prize-create")
    @Operation(summary = "新建奖品", description = "权限 reward:prize:create")
    public Result<IdResponse> create(@Valid @RequestBody PrizeSaveCommand command) {
        return Result.ok(new IdResponse(appService.create(command).id()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(RewardPermissions.PRIZE_UPDATE)
    @Audited(module = "reward", action = "prize-update")
    @Operation(summary = "更新奖品", description = "权限 reward:prize:update；reconActionPolicy 启用后可改")
    public Result<OkResponse> update(@PathVariable long id, @Valid @RequestBody PrizeSaveCommand command) {
        appService.update(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(RewardPermissions.PRIZE_DELETE)
    @Audited(module = "reward", action = "prize-delete")
    @Operation(summary = "删除奖品", description = "权限 reward:prize:delete；仅草稿；被快照引用 reward.prize.referenced-by-snapshot")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/{id}/disable")
    @SaCheckPermission(RewardPermissions.PRIZE_DISABLE)
    @Audited(module = "reward", action = "prize-disable")
    @Operation(summary = "停用奖品", description = "权限 reward:prize:disable；confirm=false 返回影响面")
    public Result<PrizeImpactResponse> disable(
            @PathVariable long id, @Valid @RequestBody PrizeConfirmCommand command) {
        return Result.ok(appService.disable(id, command));
    }

    @PostMapping("/{id}/enable")
    @SaCheckPermission(RewardPermissions.PRIZE_ENABLE)
    @Audited(module = "reward", action = "prize-enable")
    @Operation(summary = "启用奖品", description = "权限 reward:prize:enable；停用→启用需二次确认")
    public Result<PrizeImpactResponse> enable(
            @PathVariable long id, @Valid @RequestBody PrizeConfirmCommand command) {
        return Result.ok(appService.enable(id, command));
    }

    @GetMapping("/{id}/stock-logs")
    @SaCheckPermission(RewardPermissions.PRIZE_QUERY)
    @Operation(summary = "库存留痕", description = "权限 reward:prize:query")
    public Result<PageData<StockLogView>> stockLogs(
            @PathVariable long id,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.stockLogs(id, PageQuery.of(page, pageSize)));
    }

    @PostMapping("/{id}/stock-replenish")
    @SaCheckPermission(RewardPermissions.PRIZE_STOCK_REPLENISH)
    @Audited(module = "reward", action = "prize-stock-replenish")
    @Operation(summary = "库存回补", description = "权限 reward:prize:stock-replenish")
    public Result<StockReplenishResponse> replenish(
            @PathVariable long id, @Valid @RequestBody StockReplenishCommand command) {
        return Result.ok(appService.replenish(id, command));
    }
}
