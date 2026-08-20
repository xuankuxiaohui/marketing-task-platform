package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.CacheAdminAppService;
import com.mkt.identity.command.CacheEvictCommand;
import com.mkt.identity.response.CacheEvictResponse;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.infra.cache.CacheStatsView;
import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/system/cache")
@Tag(name = "system")
public class CacheAdminController {

    private final CacheAdminAppService appService;

    public CacheAdminController(CacheAdminAppService appService) {
        this.appService = appService;
    }

    @GetMapping("/stats")
    @SaCheckPermission(IdentityPermissions.CACHE_STATS)
    @Operation(summary = "缓存概况", description = "权限 system:cache:stats")
    public Result<PageData<CacheStatsView>> stats() {
        return Result.ok(appService.stats());
    }

    @PostMapping("/evict")
    @SaCheckPermission(IdentityPermissions.CACHE_EVICT)
    @Audited(module = "system", action = "cache-evict")
    @Operation(summary = "清理缓存", description = "权限 system:cache:evict；禁止 identity:session")
    public Result<CacheEvictResponse> evict(@RequestBody CacheEvictCommand command) {
        return Result.ok(appService.evict(command));
    }
}
