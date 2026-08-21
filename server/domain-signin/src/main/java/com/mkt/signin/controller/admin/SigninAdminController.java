package com.mkt.signin.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.signin.application.SigninAdminAppService;
import com.mkt.signin.command.SigninActivitySaveCommand;
import com.mkt.signin.command.SigninPublishCommand;
import com.mkt.signin.command.SigninScheduleCommand;
import com.mkt.signin.query.SigninActivityQuery;
import com.mkt.signin.query.SigninRecordQuery;
import com.mkt.signin.response.OkResponse;
import com.mkt.signin.response.SigninActivitySaveResponse;
import com.mkt.signin.response.SigninActivityView;
import com.mkt.signin.response.SigninPublishResponse;
import com.mkt.signin.response.SigninRecordView;
import com.mkt.signin.support.SigninPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/signin")
@Tag(name = "signin")
public class SigninAdminController {

    private final SigninAdminAppService appService;

    public SigninAdminController(SigninAdminAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/activities")
    @SaCheckPermission(SigninPermissions.CONFIG_QUERY)
    @Operation(summary = "分页查询签到活动", description = "权限 signin:config:query")
    public Result<PageData<SigninActivityView>> page(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(
                new SigninActivityQuery(code, name, status, PageQuery.of(page, pageSize))));
    }

    @GetMapping("/activities/{id}")
    @SaCheckPermission(SigninPermissions.CONFIG_QUERY)
    @Operation(summary = "签到活动详情", description = "权限 signin:config:query")
    public Result<SigninActivityView> get(@PathVariable long id) {
        return Result.ok(appService.get(id));
    }

    @PostMapping("/activities")
    @SaCheckPermission(
            value = {SigninPermissions.CONFIG_CREATE, SigninPermissions.CONFIG_UPDATE},
            mode = SaMode.OR)
    @Audited(module = "signin", action = "activity-save")
    @Operation(summary = "保存签到活动", description = "新建 signin:config:create / 更新 signin:config:update")
    public Result<SigninActivitySaveResponse> save(@Valid @RequestBody SigninActivitySaveCommand command) {
        if (command.id() == null) {
            StpUtil.checkPermission(SigninPermissions.CONFIG_CREATE);
        } else {
            StpUtil.checkPermission(SigninPermissions.CONFIG_UPDATE);
        }
        return Result.ok(appService.save(command));
    }

    @DeleteMapping("/activities/{id}")
    @SaCheckPermission(SigninPermissions.CONFIG_DELETE)
    @Audited(module = "signin", action = "activity-delete")
    @Operation(summary = "删除签到活动", description = "权限 signin:config:delete")
    public Result<OkResponse> delete(@PathVariable long id) {
        return Result.ok(appService.delete(id));
    }

    @PostMapping("/activities/{id}/publish")
    @SaCheckPermission(SigninPermissions.CONFIG_PUBLISH)
    @Audited(module = "signin", action = "activity-publish")
    @Operation(summary = "发布签到活动", description = "权限 signin:config:publish")
    public Result<SigninPublishResponse> publish(
            @PathVariable long id, @RequestBody(required = false) SigninPublishCommand command) {
        return Result.ok(appService.publish(id, command));
    }

    @PostMapping("/activities/{id}/schedule")
    @SaCheckPermission(SigninPermissions.CONFIG_SCHEDULE)
    @Audited(module = "signin", action = "activity-schedule")
    @Operation(summary = "定时发布签到活动", description = "权限 signin:config:schedule")
    public Result<SigninPublishResponse> schedule(
            @PathVariable long id, @Valid @RequestBody SigninScheduleCommand command) {
        return Result.ok(appService.schedule(id, command));
    }

    @PostMapping("/activities/{id}/offline")
    @SaCheckPermission(SigninPermissions.CONFIG_OFFLINE)
    @Audited(module = "signin", action = "activity-offline")
    @Operation(summary = "下线签到活动", description = "权限 signin:config:offline")
    public Result<SigninPublishResponse> offline(@PathVariable long id) {
        return Result.ok(appService.offline(id));
    }

    @GetMapping("/records")
    @SaCheckPermission(SigninPermissions.RECORD_QUERY)
    @Operation(summary = "分页查询签到记录", description = "权限 signin:record:query")
    public Result<PageData<SigninRecordView>> records(
            @RequestParam(required = false) Long activityId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) LocalDate from,
            @RequestParam(required = false) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.pageRecords(
                new SigninRecordQuery(activityId, userId, from, to, PageQuery.of(page, pageSize))));
    }
}
