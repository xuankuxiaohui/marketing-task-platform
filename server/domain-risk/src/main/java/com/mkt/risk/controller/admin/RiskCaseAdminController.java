package com.mkt.risk.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.risk.application.RiskCaseAppService;
import com.mkt.risk.command.RiskCaseHandleCommand;
import com.mkt.risk.query.RiskHitQuery;
import com.mkt.risk.response.OkResponse;
import com.mkt.risk.response.RiskHitLogResponse;
import com.mkt.risk.support.RiskListPermissions;
import com.mkt.risk.support.RiskPermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "risk")
public class RiskCaseAdminController {

    private final RiskCaseAppService appService;
    private final RiskPermissionGuard permissions;

    public RiskCaseAdminController(RiskCaseAppService appService, RiskPermissionGuard permissions) {
        this.appService = appService;
        this.permissions = permissions;
    }

    @GetMapping("/admin/risk/hits")
    @SaCheckPermission(RiskListPermissions.CASE_QUERY)
    @Operation(summary = "查询风控命中记录", description = "权限 risk:case:query")
    public Result<PageData<RiskHitLogResponse>> hits(
            @RequestParam(required = false) String ruleCode,
            @RequestParam(required = false) String hitType,
            @RequestParam(required = false) String dimensionValue,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String actionResult,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        permissions.requireCaseQuery();
        RiskHitQuery query = new RiskHitQuery(
                ruleCode,
                hitType,
                dimensionValue,
                userId,
                actionResult,
                from,
                to,
                PageQuery.of(page, pageSize));
        return Result.ok(appService.pageHits(query));
    }

    @PostMapping("/admin/risk/cases/handle")
    @SaCheckPermission(RiskListPermissions.CASE_HANDLE)
    @Audited(module = "risk", action = "case-handle")
    @Operation(summary = "人工处置风控命中", description = "权限 risk:case:handle；reason 必填")
    public Result<OkResponse> handle(@Valid @RequestBody RiskCaseHandleCommand command) {
        permissions.requireCaseHandle();
        appService.handle(command);
        return Result.ok(OkResponse.yes());
    }
}
