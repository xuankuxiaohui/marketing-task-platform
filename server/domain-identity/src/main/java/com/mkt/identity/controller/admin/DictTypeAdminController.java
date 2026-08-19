package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.DictAppService;
import com.mkt.identity.command.DictTypeCreateCommand;
import com.mkt.identity.command.DictTypeUpdateCommand;
import com.mkt.identity.response.DictEntryOption;
import com.mkt.identity.response.DictTypeView;
import com.mkt.identity.response.IdResponse;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/admin/system/dict-types")
@Tag(name = "system")
public class DictTypeAdminController {

    private final DictAppService appService;

    public DictTypeAdminController(DictAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.DICT_TYPE_QUERY)
    @Operation(summary = "分页查询字典类型", description = "权限 system:dict-type:query")
    public Result<PageData<DictTypeView>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.pageTypes(PageQuery.of(page, pageSize)));
    }

    @PostMapping
    @SaCheckPermission(IdentityPermissions.DICT_TYPE_CREATE)
    @Audited(module = "system", action = "dict-type-create")
    @Operation(summary = "创建字典类型", description = "权限 system:dict-type:create")
    public Result<IdResponse> create(@Valid @RequestBody DictTypeCreateCommand command) {
        return Result.ok(new IdResponse(appService.createType(command).getId()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.DICT_TYPE_UPDATE)
    @Audited(module = "system", action = "dict-type-update")
    @Operation(summary = "更新字典类型", description = "权限 system:dict-type:update")
    public Result<OkResponse> update(@PathVariable long id, @Valid @RequestBody DictTypeUpdateCommand command) {
        appService.updateType(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.DICT_TYPE_DELETE)
    @Audited(module = "system", action = "dict-type-delete")
    @Operation(summary = "删除字典类型", description = "权限 system:dict-type:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.deleteType(id);
        return Result.ok(OkResponse.yes());
    }

    @GetMapping("/{code}/entries")
    @Operation(summary = "按类型查询启用字典项", description = "登录即可读；类型停用返回空列表")
    public Result<List<DictEntryOption>> entries(@PathVariable String code) {
        return Result.ok(appService.listEnabledEntries(code));
    }
}
