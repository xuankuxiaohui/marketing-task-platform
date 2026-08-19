package com.mkt.tracking.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.tracking.application.TrackDebugQueryService;
import com.mkt.tracking.query.TrackDebugQuery;
import com.mkt.tracking.response.TrackDebugEventResponse;
import com.mkt.tracking.support.TrackPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/track/events")
@Tag(name = "track")
public class TrackDebugAdminController {

    private final TrackDebugQueryService debugQueryService;

    public TrackDebugAdminController(TrackDebugQueryService debugQueryService) {
        this.debugQueryService = debugQueryService;
    }

    @GetMapping("/debug")
    @SaCheckPermission(TrackPermissions.EVENT_QUERY)
    @Operation(
            summary = "调试查询原始事件",
            description = "权限 track:event:query；抽样 track.query.sample-ratio-percent + 独立限流；无副作用")
    public Result<PageData<TrackDebugEventResponse>> debug(
            @RequestParam(required = false) String eventCode,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String deviceId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        TrackDebugQuery query = new TrackDebugQuery(
                eventCode, userId, source, deviceId, from, to, PageQuery.of(page, pageSize));
        return Result.ok(debugQueryService.query(query));
    }
}
