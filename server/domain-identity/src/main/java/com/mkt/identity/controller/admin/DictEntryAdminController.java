package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.DictAppService;
import com.mkt.identity.command.DictEntryCreateCommand;
import com.mkt.identity.command.DictEntryUpdateCommand;
import com.mkt.identity.response.IdResponse;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/dict-entries")
@Tag(name = "system")
public class DictEntryAdminController {

    private final DictAppService appService;

    public DictEntryAdminController(DictAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    @SaCheckPermission(IdentityPermissions.DICT_ENTRY_CREATE)
    @Audited(module = "system", action = "dict-entry-create")
    @Operation(summary = "创建字典项", description = "权限 system:dict-entry:create")
    public Result<IdResponse> create(@Valid @RequestBody DictEntryCreateCommand command) {
        return Result.ok(new IdResponse(appService.createEntry(command).getId()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.DICT_ENTRY_UPDATE)
    @Audited(module = "system", action = "dict-entry-update")
    @Operation(summary = "更新字典项", description = "权限 system:dict-entry:update")
    public Result<OkResponse> update(@PathVariable long id, @Valid @RequestBody DictEntryUpdateCommand command) {
        appService.updateEntry(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(IdentityPermissions.DICT_ENTRY_DELETE)
    @Audited(module = "system", action = "dict-entry-delete")
    @Operation(summary = "删除字典项", description = "权限 system:dict-entry:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.deleteEntry(id);
        return Result.ok(OkResponse.yes());
    }
}
