package com.mkt.risk.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.mkt.contract.RiskListType;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.kernel.trace.TraceIds;
import com.mkt.risk.application.RiskListAddResult;
import com.mkt.risk.application.RiskListAppService;
import com.mkt.risk.command.RiskListItemCreateCommand;
import com.mkt.risk.command.RiskListItemImportCommand;
import com.mkt.risk.command.RiskListItemRemoveCommand;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.query.RiskListItemQuery;
import com.mkt.risk.response.IdResponse;
import com.mkt.risk.response.OkResponse;
import com.mkt.risk.response.RiskListImportResponse;
import com.mkt.risk.response.RiskListItemResponse;
import com.mkt.risk.support.RiskErrorCodes;
import com.mkt.risk.support.RiskListPermissions;
import com.mkt.risk.support.RiskPermissionGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/risk/list-items")
@Tag(name = "risk")
public class RiskListAdminController {

    private final RiskListAppService appService;
    private final RiskPermissionGuard permissions;

    public RiskListAdminController(RiskListAppService appService, RiskPermissionGuard permissions) {
        this.appService = appService;
        this.permissions = permissions;
    }

    @GetMapping
    @Operation(summary = "分页查询风控名单",
            description = "权限 risk:blacklist:query / risk:whitelist:query（按 listType；缺省两者都要）")
    public Result<PageData<RiskListItemResponse>> page(
            @RequestParam(required = false) RiskDimension dimension,
            @RequestParam(required = false) RiskListType listType,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        permissions.requireQuery(listType);
        RiskListItemQuery query =
                new RiskListItemQuery(dimension, listType, value, from, to, PageQuery.of(page, pageSize));
        return Result.ok(appService.page(query));
    }

    @PostMapping
    @SaCheckPermission(
            value = {RiskListPermissions.BLACK_ADD, RiskListPermissions.WHITE_ADD},
            mode = SaMode.OR)
    @Audited(module = "risk", action = "list-add")
    @Operation(
            summary = "新增名单条目",
            description = "权限 risk:blacklist:add / risk:whitelist:add；重复 400 risk.list.duplicate-returned")
    public ResponseEntity<Result<?>> add(@Valid @RequestBody RiskListItemCreateCommand command) {
        permissions.requireAdd(command.listType());
        RiskListAddResult result = appService.add(command);
        if (result.duplicate()) {
            RiskErrorCodes code = RiskErrorCodes.LIST_DUPLICATE_RETURNED;
            return ResponseEntity.status(code.httpStatus())
                    .body(new Result<>(code.code(), code.message(), result.item(), TraceIds.current()));
        }
        return ResponseEntity.ok(Result.ok(new IdResponse(result.item().id())));
    }

    @PostMapping("/import")
    @SaCheckPermission(RiskListPermissions.BLACK_IMPORT)
    @Audited(module = "risk", action = "list-import")
    @Operation(summary = "批量导入名单", description = "权限 risk:blacklist:import；白名单 400 common.param-invalid")
    public Result<RiskListImportResponse> importItems(@Valid @RequestBody RiskListItemImportCommand command) {
        permissions.requireImport(command.listType());
        return Result.ok(appService.importItems(command));
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(
            value = {RiskListPermissions.BLACK_REMOVE, RiskListPermissions.WHITE_REMOVE},
            mode = SaMode.OR)
    @Audited(module = "risk", action = "list-remove")
    @Operation(summary = "移除名单条目", description = "权限 risk:blacklist:remove / risk:whitelist:remove；body.reason 必填")
    public Result<OkResponse> remove(
            @PathVariable long id, @Valid @RequestBody RiskListItemRemoveCommand command) {
        RiskListItemResponse existing = appService.get(id);
        permissions.requireRemove(RiskListType.valueOf(existing.listType()));
        appService.remove(id, command.reason());
        return Result.ok(OkResponse.yes());
    }
}
