package com.mkt.reward.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.reward.application.PrizeCategoryAppService;
import com.mkt.reward.command.PrizeCategorySaveCommand;
import com.mkt.reward.response.OkResponse;
import com.mkt.reward.response.PrizeCategoryResponse;
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
@RequestMapping("/admin/reward/prize-categories")
@Tag(name = "reward")
public class PrizeCategoryAdminController {

    private final PrizeCategoryAppService appService;

    public PrizeCategoryAdminController(PrizeCategoryAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(RewardPermissions.CATEGORY_QUERY)
    @Operation(summary = "分页查询奖品分类", description = "权限 reward:category:query")
    public Result<PageData<PrizeCategoryResponse>> page(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(PageQuery.of(page, pageSize)));
    }

    @GetMapping("/{code}")
    @SaCheckPermission(RewardPermissions.CATEGORY_QUERY)
    @Operation(summary = "奖品分类详情", description = "权限 reward:category:query")
    public Result<PrizeCategoryResponse> get(@PathVariable String code) {
        return Result.ok(appService.get(code));
    }

    @PostMapping
    @SaCheckPermission(RewardPermissions.CATEGORY_CREATE)
    @Audited(module = "reward", action = "category-create")
    @Operation(summary = "新建奖品分类", description = "权限 reward:category:create")
    public Result<PrizeCategoryResponse> create(@Valid @RequestBody PrizeCategorySaveCommand command) {
        return Result.ok(appService.create(command));
    }

    @PutMapping("/{code}")
    @SaCheckPermission(RewardPermissions.CATEGORY_UPDATE)
    @Audited(module = "reward", action = "category-update")
    @Operation(summary = "更新奖品分类", description = "权限 reward:category:update；reconActionPolicy 启用后可改")
    public Result<OkResponse> update(
            @PathVariable String code, @Valid @RequestBody PrizeCategorySaveCommand command) {
        appService.update(code, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{code}")
    @SaCheckPermission(RewardPermissions.CATEGORY_DELETE)
    @Audited(module = "reward", action = "category-delete")
    @Operation(summary = "删除奖品分类", description = "权限 reward:category:delete；内置不可删 reward.category.builtin-protected")
    public Result<OkResponse> delete(@PathVariable String code) {
        appService.delete(code);
        return Result.ok(OkResponse.yes());
    }

    @PutMapping("/{code}/disable")
    @SaCheckPermission(RewardPermissions.CATEGORY_DISABLE)
    @Audited(module = "reward", action = "category-disable")
    @Operation(summary = "停用奖品分类", description = "权限 reward:category:disable")
    public Result<OkResponse> disable(@PathVariable String code) {
        appService.disable(code);
        return Result.ok(OkResponse.yes());
    }

    @PutMapping("/{code}/enable")
    @SaCheckPermission(RewardPermissions.CATEGORY_ENABLE)
    @Audited(module = "reward", action = "category-enable")
    @Operation(summary = "启用奖品分类", description = "权限 reward:category:enable")
    public Result<OkResponse> enable(@PathVariable String code) {
        appService.enable(code);
        return Result.ok(OkResponse.yes());
    }
}
