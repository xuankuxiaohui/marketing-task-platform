package com.mkt.task.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import com.mkt.task.application.TaskInstanceAppService;
import com.mkt.task.command.InstanceAbandonCommand;
import com.mkt.task.query.InstanceQuery;
import com.mkt.task.response.AdminInstanceDetailResponse;
import com.mkt.task.response.AdminInstanceView;
import com.mkt.task.response.InstanceAbandonResponse;
import com.mkt.task.support.TaskPermissions;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/task/instances")
@Tag(name = "task")
public class TaskInstanceAdminController {

    private final TaskInstanceAppService instances;

    public TaskInstanceAdminController(TaskInstanceAppService instances) {
        this.instances = instances;
    }

    @GetMapping
    @SaCheckPermission(TaskPermissions.INSTANCE_QUERY)
    @Operation(summary = "后台实例分页", description = "权限 task:instance:query")
    public Result<PageData<AdminInstanceView>> page(
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer simulated,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        InstanceQuery query =
                new InstanceQuery(taskId, userId, status, simulated, from, to, PageQuery.of(page, pageSize));
        return Result.ok(instances.page(query));
    }

    @GetMapping("/{id}")
    @SaCheckPermission(TaskPermissions.INSTANCE_QUERY)
    @Operation(summary = "实例详情（步骤+事件时间线）", description = "权限 task:instance:query")
    public Result<AdminInstanceDetailResponse> get(@PathVariable long id) {
        return Result.ok(instances.get(id));
    }

    @PostMapping("/{id}/abandon")
    @SaCheckPermission(TaskPermissions.INSTANCE_ABANDON)
    @Audited(module = "task", action = "instance-abandon")
    @Operation(summary = "运营终止实例", description = "权限 task:instance:abandon；abandonSource=ADMIN；已终态幂等")
    public Result<InstanceAbandonResponse> abandon(
            @PathVariable long id, @Valid @RequestBody InstanceAbandonCommand command) {
        return Result.ok(instances.abandonAdmin(id));
    }
}
