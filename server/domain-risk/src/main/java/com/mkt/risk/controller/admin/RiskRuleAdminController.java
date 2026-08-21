package com.mkt.risk.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.risk.application.RiskRuleAppService;
import com.mkt.risk.command.RiskRuleUpdateCommand;
import com.mkt.risk.response.RiskRuleResponse;
import com.mkt.risk.support.RiskListPermissions;
import com.mkt.risk.support.RiskPermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/risk/rules")
@Tag(name = "risk")
public class RiskRuleAdminController {

    private final RiskRuleAppService appService;
    private final RiskPermissionGuard permissions;

    public RiskRuleAdminController(RiskRuleAppService appService, RiskPermissionGuard permissions) {
        this.appService = appService;
        this.permissions = permissions;
    }

    @GetMapping
    @SaCheckPermission(RiskListPermissions.RULE_QUERY)
    @Operation(summary = "查询风控规则", description = "权限 risk:rule:query；全量 R-a–R-f")
    public Result<List<RiskRuleResponse>> list() {
        permissions.requireRuleQuery();
        return Result.ok(appService.list());
    }

    @PutMapping("/{ruleCode}")
    @SaCheckPermission(RiskListPermissions.RULE_CONFIG)
    @Audited(module = "risk", action = "rule-config")
    @Operation(summary = "更新风控规则", description = "权限 risk:rule:config；实时生效+审计；错误 risk.rule.range-violated")
    public Result<RiskRuleResponse> update(
            @PathVariable String ruleCode, @Valid @RequestBody RiskRuleUpdateCommand command) {
        permissions.requireRuleConfig();
        return Result.ok(appService.update(ruleCode, command));
    }
}
