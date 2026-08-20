package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.command.ConfigCreateCommand;
import com.mkt.identity.command.ConfigUpdateCommand;
import com.mkt.identity.config.ConfigAppService;
import com.mkt.identity.query.ConfigQuery;
import com.mkt.identity.response.ConfigView;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/configs")
@Tag(name = "system")
public class ConfigAdminController {

    private final ConfigAppService appService;

    public ConfigAdminController(ConfigAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.CONFIG_QUERY)
    @Operation(summary = "分页查询配置", description = "权限 system:config:query；掩码项不回显明文")
    public Result<PageData<ConfigView>> page(
            @RequestParam(required = false) String configGroup,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(new ConfigQuery(configGroup, key, PageQuery.of(page, pageSize))));
    }

    @PostMapping
    @SaCheckPermission(IdentityPermissions.CONFIG_CREATE)
    @Audited(module = "system", action = "config-create")
    @Operation(summary = "创建配置", description = "权限 system:config:create")
    public Result<IdResponse> create(@Valid @RequestBody ConfigCreateCommand command) {
        return Result.ok(new IdResponse(appService.create(command).getId()));
    }

    @PutMapping("/{key}")
    @SaCheckPermission(IdentityPermissions.CONFIG_UPDATE)
    @Audited(module = "system", action = "config-update")
    @Operation(summary = "更新配置", description = "权限 system:config:update；未带 value 保持原值")
    public Result<OkResponse> update(@PathVariable String key, @Valid @RequestBody ConfigUpdateCommand command) {
        appService.update(key, command);
        return Result.ok(OkResponse.yes());
    }
}
