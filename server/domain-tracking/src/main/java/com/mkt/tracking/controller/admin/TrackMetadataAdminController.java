package com.mkt.tracking.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.tracking.application.TrackMetadataAppService;
import com.mkt.tracking.command.TrackMetadataSaveCommand;
import com.mkt.tracking.query.TrackMetadataQuery;
import com.mkt.tracking.response.IdResponse;
import com.mkt.tracking.response.OkResponse;
import com.mkt.tracking.response.TrackMetadataResponse;
import com.mkt.tracking.support.TrackPermissions;
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
@RequestMapping("/admin/track/metadata")
@Tag(name = "track")
public class TrackMetadataAdminController {

    private final TrackMetadataAppService appService;

    public TrackMetadataAdminController(TrackMetadataAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(TrackPermissions.METADATA_QUERY)
    @Operation(summary = "分页查询事件元数据", description = "权限 track:metadata:query；与 V4 种子联动")
    public Result<PageData<TrackMetadataResponse>> page(
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        TrackMetadataQuery query = new TrackMetadataQuery(eventCode, status, PageQuery.of(page, pageSize));
        return Result.ok(appService.page(query));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(TrackPermissions.METADATA_QUERY)
    @Operation(summary = "事件元数据详情", description = "权限 track:metadata:query")
    public Result<TrackMetadataResponse> get(@PathVariable long id) {
        return Result.ok(appService.get(id));
    }

    @PostMapping
    @SaCheckPermission(TrackPermissions.METADATA_CREATE)
    @Audited(module = "track", action = "metadata-create")
    @Operation(
            summary = "登记事件元数据",
            description = "权限 track:metadata:create；重复 400 track.metadata.duplicate-code")
    public Result<IdResponse> create(@Valid @RequestBody TrackMetadataSaveCommand command) {
        return Result.ok(new IdResponse(appService.create(command).id()));
    }

    @PutMapping("/{id}")
    @SaCheckPermission(TrackPermissions.METADATA_UPDATE)
    @Audited(module = "track", action = "metadata-update")
    @Operation(summary = "更新事件元数据", description = "权限 track:metadata:update；eventCode 不可改")
    public Result<OkResponse> update(
            @PathVariable long id, @Valid @RequestBody TrackMetadataSaveCommand command) {
        appService.update(id, command);
        return Result.ok(OkResponse.yes());
    }

    @DeleteMapping("/{id}")
    @SaCheckPermission(TrackPermissions.METADATA_DELETE)
    @Audited(module = "track", action = "metadata-delete")
    @Operation(summary = "删除事件元数据", description = "权限 track:metadata:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        appService.delete(id);
        return Result.ok(OkResponse.yes());
    }
}
