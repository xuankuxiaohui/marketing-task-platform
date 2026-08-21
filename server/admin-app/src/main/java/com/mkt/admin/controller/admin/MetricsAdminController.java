package com.mkt.admin.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.admin.metrics.AdMetricsResponse;
import com.mkt.admin.metrics.FunnelResponse;
import com.mkt.admin.metrics.MetricsGrain;
import com.mkt.admin.metrics.MetricsPermissions;
import com.mkt.admin.metrics.MetricsQuery;
import com.mkt.admin.metrics.MetricsQueryService;
import com.mkt.admin.metrics.RiskMetricsResponse;
import com.mkt.admin.metrics.SpendMetricsResponse;
import com.mkt.kernel.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/metrics")
@Tag(name = "metrics")
public class MetricsAdminController {

    private final MetricsQueryService queries;

    public MetricsAdminController(MetricsQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/funnel")
    @SaCheckPermission(MetricsPermissions.DASHBOARD_VIEW)
    @Operation(summary = "任务漏斗", description = "权限 metrics:dashboard:view；读 mtr_task_funnel_d")
    public Result<FunnelResponse> funnel(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String grain,
            @RequestParam(required = false) String dimKey) {
        return Result.ok(queries.funnel(new MetricsQuery(from, to, MetricsGrain.parse(grain), dimKey)));
    }

    @GetMapping("/spend")
    @SaCheckPermission(MetricsPermissions.DASHBOARD_VIEW)
    @Operation(summary = "奖励成本", description = "权限 metrics:dashboard:view；读 mtr_reward_spend_d")
    public Result<SpendMetricsResponse> spend(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String grain,
            @RequestParam(required = false) String dimKey) {
        return Result.ok(queries.spend(new MetricsQuery(from, to, MetricsGrain.parse(grain), dimKey)));
    }

    @GetMapping("/risk")
    @SaCheckPermission(MetricsPermissions.DASHBOARD_VIEW)
    @Operation(summary = "风控命中", description = "权限 metrics:dashboard:view；读 mtr_risk_hit_d")
    public Result<RiskMetricsResponse> risk(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String grain,
            @RequestParam(required = false) String dimKey) {
        return Result.ok(queries.risk(new MetricsQuery(from, to, MetricsGrain.parse(grain), dimKey)));
    }

    @GetMapping("/ad")
    @SaCheckPermission(MetricsPermissions.DASHBOARD_VIEW)
    @Operation(summary = "广告素材", description = "权限 metrics:dashboard:view；读 mtr_ad_material_d")
    public Result<AdMetricsResponse> ad(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String grain,
            @RequestParam(required = false) String dimKey) {
        return Result.ok(queries.ad(new MetricsQuery(from, to, MetricsGrain.parse(grain), dimKey)));
    }
}
