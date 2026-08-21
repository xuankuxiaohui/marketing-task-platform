package com.mkt.activity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.activity.application.ActivityAdminAppService;
import com.mkt.activity.command.ActivityPublishCommand;
import com.mkt.activity.command.ActivitySaveCommand;
import com.mkt.activity.command.ActivityScheduleCommand;
import com.mkt.activity.query.ActivityQuery;
import com.mkt.activity.query.ParticipationQuery;
import com.mkt.activity.response.ActivityPublishResponse;
import com.mkt.activity.response.ActivitySaveResponse;
import com.mkt.activity.response.ActivityView;
import com.mkt.activity.response.OkResponse;
import com.mkt.activity.response.ParticipationStatsView;
import com.mkt.activity.response.ParticipationView;
import com.mkt.activity.support.ActivityPermissions;
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
@RequestMapping("/admin/activity")
@Tag(name = "activity")
public class ActivityAdminController {

    private final ActivityAdminAppService appService;

    public ActivityAdminController(ActivityAdminAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/activities")
    @SaCheckPermission(ActivityPermissions.QUERY)
    @Operation(summary = "分页查询活动", description = "权限 activity:query")
    public Result<PageData<ActivityView>> page(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(new ActivityQuery(code, name, status, PageQuery.of(page, pageSize))));
    }

    @GetMapping("/activities/{id}")
    @SaCheckPermission(ActivityPermissions.QUERY)
    @Operation(summary = "活动详情", description = "权限 activity:query")
    public Result<ActivityView> get(@PathVariable long id) {
        return Result.ok(appService.get(id));
    }

    @PostMapping("/activities")
    @SaCheckPermission(
            value = {ActivityPermissions.CREATE, ActivityPermissions.UPDATE},
            mode = SaMode.OR)
    @Audited(module = "activity", action = "activity-save")
    @Operation(summary = "保存活动", description = "新建 activity:create / 更新 activity:update")
    public Result<ActivitySaveResponse> save(@Valid @RequestBody ActivitySaveCommand command) {
        if (command.id() == null) {
            StpUtil.checkPermission(ActivityPermissions.CREATE);
        } else {
            StpUtil.checkPermission(ActivityPermissions.UPDATE);
        }
        return Result.ok(appService.save(command));
    }

    @DeleteMapping("/activities/{id}")
    @SaCheckPermission(ActivityPermissions.DELETE)
    @Audited(module = "activity", action = "activity-delete")
    @Operation(summary = "删除活动", description = "权限 activity:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        return Result.ok(appService.delete(id));
    }

    @PostMapping("/activities/{id}/publish")
    @SaCheckPermission(ActivityPermissions.PUBLISH)
    @Audited(module = "activity", action = "activity-publish")
    @Operation(summary = "发布活动", description = "权限 activity:publish")
    public Result<ActivityPublishResponse> publish(
            @PathVariable long id, @RequestBody(required = false) ActivityPublishCommand command) {
        return Result.ok(appService.publish(id, command));
    }

    @PostMapping("/activities/{id}/schedule")
    @SaCheckPermission(ActivityPermissions.PUBLISH)
    @Audited(module = "activity", action = "activity-schedule")
    @Operation(summary = "定时发布/下线", description = "权限 activity:publish")
    public Result<ActivityPublishResponse> schedule(
            @PathVariable long id, @RequestBody ActivityScheduleCommand command) {
        return Result.ok(appService.schedule(id, command));
    }

    @PostMapping("/activities/{id}/offline")
    @SaCheckPermission(ActivityPermissions.OFFLINE)
    @Audited(module = "activity", action = "activity-offline")
    @Operation(summary = "下线活动", description = "权限 activity:offline")
    public Result<ActivityPublishResponse> offline(@PathVariable long id) {
        return Result.ok(appService.offline(id));
    }

    @GetMapping("/participations")
    @SaCheckPermission(ActivityPermissions.PARTICIPATION_QUERY)
    @Operation(summary = "分页查询参与记录", description = "权限 activity:participation:query")
    public Result<PageData<ParticipationView>> participations(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String periodKey,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.pageParticipations(
                new ParticipationQuery(activityId, userId, result, periodKey, PageQuery.of(page, pageSize))));
    }

    @GetMapping("/activities/{id}/stats")
    @SaCheckPermission(ActivityPermissions.PARTICIPATION_QUERY)
    @Operation(summary = "参与统计", description = "权限 activity:participation:query")
    public Result<ParticipationStatsView> stats(@PathVariable long id) {
        return Result.ok(appService.stats(id));
    }
}
