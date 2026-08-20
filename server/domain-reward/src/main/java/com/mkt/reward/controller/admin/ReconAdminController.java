package com.mkt.reward.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.reward.application.ReconAppService;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.command.ReconReviewCommand;
import com.mkt.reward.response.OkResponse;
import com.mkt.reward.response.ReconActionResponse;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.response.ReconItemView;
import com.mkt.reward.response.ReconMatchResponse;
import com.mkt.reward.response.ReconReviewResponse;
import com.mkt.reward.support.RewardPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reward/recon")
@Tag(name = "reward")
public class ReconAdminController {

    private final ReconAppService recon;

    public ReconAdminController(ReconAppService recon) {
        this.recon = recon;
    }

    @GetMapping("/batches")
    @SaCheckPermission(RewardPermissions.RECON_QUERY)
    @Operation(summary = "对账批次列表", description = "权限 reward:recon:query")
    public Result<PageData<ReconBatchResponse>> batches(
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) LocalDate billDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(recon.page(categoryCode, billDate, status, PageQuery.of(page, pageSize)));
    }

    @PostMapping("/batches")
    @SaCheckPermission(RewardPermissions.RECON_IMPORT)
    @Audited(module = "reward", action = "recon-batch-create")
    @Operation(summary = "创建对账批次", description = "权限 reward:recon:import")
    public Result<ReconBatchResponse> create(@Valid @RequestBody ReconBatchCreateCommand command) {
        return Result.ok(recon.create(command));
    }

    @PostMapping("/batches/{id}/import")
    @SaCheckPermission(RewardPermissions.RECON_IMPORT)
    @Audited(module = "reward", action = "recon-batch-import")
    @Operation(summary = "导入渠道账单", description = "权限 reward:recon:import")
    public Result<OkResponse> importLines(@PathVariable long id, @Valid @RequestBody ReconImportCommand command) {
        recon.importLines(id, command);
        return Result.ok(OkResponse.yes());
    }

    @PostMapping("/batches/{id}/match")
    @SaCheckPermission(RewardPermissions.RECON_MATCH)
    @Audited(module = "reward", action = "recon-batch-match")
    @Operation(summary = "匹配对账批次", description = "权限 reward:recon:match；平台集含 FULFILL_FAILED")
    public Result<ReconMatchResponse> match(@PathVariable long id) {
        return Result.ok(recon.match(id));
    }

    @GetMapping("/batches/{id}/items")
    @SaCheckPermission(RewardPermissions.RECON_QUERY)
    @Operation(summary = "对账明细", description = "权限 reward:recon:query")
    public Result<PageData<ReconItemView>> items(
            @PathVariable long id,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(recon.items(id, result, reviewStatus, PageQuery.of(page, pageSize)));
    }

    @PostMapping("/items/{id}/review")
    @SaCheckPermission(RewardPermissions.RECON_ACTION)
    @Audited(module = "reward", action = "recon-item-review")
    @Operation(summary = "对账核渠", description = "权限 reward:recon:action")
    public Result<ReconReviewResponse> review(@PathVariable long id, @Valid @RequestBody ReconReviewCommand command) {
        return Result.ok(recon.review(id, command));
    }

    @PostMapping("/items/{id}/action")
    @SaCheckPermission(RewardPermissions.RECON_ACTION)
    @Audited(module = "reward", action = "recon-item-action")
    @Operation(summary = "对账差异动作", description = "权限 reward:recon:action；门禁见 §5.11")
    public Result<ReconActionResponse> action(@PathVariable long id, @Valid @RequestBody ReconActionCommand command) {
        return Result.ok(recon.action(id, command));
    }
}
