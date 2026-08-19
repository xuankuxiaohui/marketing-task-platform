package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.AuditQueryAppService;
import com.mkt.identity.query.AuditQuery;
import com.mkt.identity.response.AuditView;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/audits")
@Tag(name = "system")
public class AuditAdminController {

    private final AuditQueryAppService appService;

    public AuditAdminController(AuditQueryAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.AUDIT_QUERY)
    @Operation(summary = "审计日志分页", description = "权限 system:audit:query；只读，无删除入口")
    public Result<PageData<AuditView>> page(
            @RequestParam(required = false) Long operatorId,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(
                new AuditQuery(operatorId, module, action, result, from, to, PageQuery.of(page, pageSize))));
    }
}
