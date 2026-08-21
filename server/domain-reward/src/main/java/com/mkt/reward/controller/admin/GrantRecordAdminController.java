package com.mkt.reward.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.reward.application.FulfillmentService;
import com.mkt.reward.application.GrantAppService;
import com.mkt.reward.command.ManualGrantCommand;
import com.mkt.reward.response.FulfillmentCallbackResponse;
import com.mkt.reward.response.GrantRetryResponse;
import com.mkt.reward.response.ManualGrantResponse;
import com.mkt.reward.support.RewardPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reward/records")
@Tag(name = "reward")
public class GrantRecordAdminController {

    private final GrantAppService grants;
    private final FulfillmentService fulfillment;

    public GrantRecordAdminController(GrantAppService grants, FulfillmentService fulfillment) {
        this.grants = grants;
        this.fulfillment = fulfillment;
    }

    @PostMapping("/{id}/retry")
    @SaCheckPermission(RewardPermissions.RECORD_RETRY)
    @Audited(module = "reward", action = "record-retry")
    @Operation(summary = "发放失败重试", description = "权限 reward:record:retry")
    public Result<GrantRetryResponse> retry(@PathVariable long id) {
        return Result.ok(grants.retry(id));
    }

    @PostMapping("/manual-grant")
    @SaCheckPermission(RewardPermissions.RECORD_MANUAL_GRANT)
    @Audited(module = "reward", action = "record-manual-grant")
    @Operation(summary = "人工补发", description = "权限 reward:record:manual-grant")
    public Result<ManualGrantResponse> manualGrant(@Valid @RequestBody ManualGrantCommand command) {
        return Result.ok(grants.manualGrant(command));
    }

    @PostMapping("/{id}/fulfill-confirm")
    @SaCheckPermission(RewardPermissions.RECORD_FULFILL)
    @Audited(module = "reward", action = "record-fulfill-confirm")
    @Operation(summary = "履约确认到账", description = "权限 reward:record:fulfill")
    public Result<FulfillmentCallbackResponse> fulfillConfirm(@PathVariable long id) {
        return Result.ok(fulfillment.confirm(id));
    }

    @PostMapping("/{id}/fulfill-retry")
    @SaCheckPermission(RewardPermissions.RECORD_FULFILL)
    @Audited(module = "reward", action = "record-fulfill-retry")
    @Operation(summary = "履约重试", description = "权限 reward:record:fulfill")
    public Result<FulfillmentCallbackResponse> fulfillRetry(@PathVariable long id) {
        return Result.ok(fulfillment.retry(id));
    }
}
