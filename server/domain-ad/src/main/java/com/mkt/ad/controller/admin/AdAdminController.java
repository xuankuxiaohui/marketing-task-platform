package com.mkt.ad.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.ad.application.AdAdminAppService;
import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.query.AdMaterialQuery;
import com.mkt.ad.query.AdPositionQuery;
import com.mkt.ad.response.AdMaterialView;
import com.mkt.ad.response.AdPositionView;
import com.mkt.ad.response.AdSaveResponse;
import com.mkt.ad.response.OkResponse;
import com.mkt.ad.support.AdPermissions;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/ad")
@Tag(name = "ad")
public class AdAdminController {

    private final AdAdminAppService appService;

    public AdAdminController(AdAdminAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/positions")
    @SaCheckPermission(AdPermissions.POSITION_QUERY)
    @Operation(summary = "分页查询广告位", description = "权限 ad:position:query")
    public Result<PageData<AdPositionView>> pagePositions(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String form,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.pagePositions(
                new AdPositionQuery(code, form, status, PageQuery.of(page, pageSize))));
    }

    @GetMapping("/positions/{id}")
    @SaCheckPermission(AdPermissions.POSITION_QUERY)
    @Operation(summary = "广告位详情（含投放与重叠数）", description = "权限 ad:position:query")
    public Result<AdPositionView> getPosition(@PathVariable long id) {
        return Result.ok(appService.getPosition(id));
    }

    @PostMapping("/positions")
    @SaCheckPermission(
            value = {AdPermissions.POSITION_CREATE, AdPermissions.POSITION_UPDATE},
            mode = SaMode.OR)
    @Audited(module = "ad", action = "position-save")
    @Operation(summary = "保存广告位", description = "新建 ad:position:create / 更新 ad:position:update")
    public Result<AdSaveResponse> savePosition(@Valid @RequestBody AdPositionSaveCommand command) {
        if (command.id() == null) {
            StpUtil.checkPermission(AdPermissions.POSITION_CREATE);
        } else {
            StpUtil.checkPermission(AdPermissions.POSITION_UPDATE);
        }
        return Result.ok(appService.savePosition(command));
    }

    @DeleteMapping("/positions/{id}")
    @SaCheckPermission(AdPermissions.POSITION_DELETE)
    @Audited(module = "ad", action = "position-delete")
    @Operation(summary = "删除广告位", description = "权限 ad:position:delete")
    public Result<OkResponse> deletePosition(@PathVariable long id) {
        return Result.ok(appService.deletePosition(id));
    }

    @PostMapping("/positions/{id}/materials")
    @SaCheckPermission(AdPermissions.POSITION_UPDATE)
    @Audited(module = "ad", action = "placement-bind")
    @Operation(summary = "绑定或更新投放", description = "权限 ad:position:update")
    public Result<AdSaveResponse> bind(
            @PathVariable long id, @Valid @RequestBody AdPlacementSaveCommand command) {
        return Result.ok(appService.bind(id, command));
    }

    @DeleteMapping("/positions/{id}/materials/{materialId}")
    @SaCheckPermission(AdPermissions.POSITION_UPDATE)
    @Audited(module = "ad", action = "placement-unbind")
    @Operation(summary = "解绑投放", description = "权限 ad:position:update")
    public Result<OkResponse> unbind(@PathVariable long id, @PathVariable long materialId) {
        return Result.ok(appService.unbind(id, materialId));
    }

    @GetMapping("/materials")
    @SaCheckPermission(AdPermissions.MATERIAL_QUERY)
    @Operation(summary = "分页查询素材", description = "权限 ad:material:query")
    public Result<PageData<AdMaterialView>> pageMaterials(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.pageMaterials(new AdMaterialQuery(title, status, PageQuery.of(page, pageSize))));
    }

    @GetMapping("/materials/{id}")
    @SaCheckPermission(AdPermissions.MATERIAL_QUERY)
    @Operation(summary = "素材详情", description = "权限 ad:material:query")
    public Result<AdMaterialView> getMaterial(@PathVariable long id) {
        return Result.ok(appService.getMaterial(id));
    }

    @PostMapping("/materials")
    @SaCheckPermission(
            value = {AdPermissions.MATERIAL_CREATE, AdPermissions.MATERIAL_UPDATE},
            mode = SaMode.OR)
    @Audited(module = "ad", action = "material-save")
    @Operation(summary = "保存素材", description = "新建 ad:material:create / 更新 ad:material:update")
    public Result<AdSaveResponse> saveMaterial(@Valid @RequestBody AdMaterialSaveCommand command) {
        if (command.id() == null) {
            StpUtil.checkPermission(AdPermissions.MATERIAL_CREATE);
        } else {
            StpUtil.checkPermission(AdPermissions.MATERIAL_UPDATE);
        }
        return Result.ok(appService.saveMaterial(command));
    }

    @DeleteMapping("/materials/{id}")
    @SaCheckPermission(AdPermissions.MATERIAL_DELETE)
    @Audited(module = "ad", action = "material-delete")
    @Operation(summary = "删除素材", description = "权限 ad:material:delete")
    public Result<OkResponse> deleteMaterial(@PathVariable long id) {
        return Result.ok(appService.deleteMaterial(id));
    }
}
