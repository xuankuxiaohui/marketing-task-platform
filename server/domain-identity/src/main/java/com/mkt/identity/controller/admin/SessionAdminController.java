package com.mkt.identity.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.mkt.identity.application.SessionAdminAppService;
import com.mkt.identity.command.SessionKickCommand;
import com.mkt.identity.query.SessionQuery;
import com.mkt.identity.response.OkResponse;
import com.mkt.identity.response.SessionView;
import com.mkt.identity.support.IdentityPermissions;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.Result;
import com.mkt.kernel.audit.Audited;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/identity/sessions")
@Tag(name = "identity")
public class SessionAdminController {

    private final SessionAdminAppService appService;

    public SessionAdminController(SessionAdminAppService appService) {
        this.appService = appService;
    }

    @GetMapping
    @SaCheckPermission(IdentityPermissions.SESSION_QUERY)
    @Operation(summary = "在线会话列表", description = "权限 identity:session:query；按 accountType + account 筛选")
    public Result<PageData<SessionView>> page(
            @RequestParam(required = false) String accountType,
            @RequestParam(required = false) String account,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return Result.ok(appService.page(new SessionQuery(accountType, account, PageQuery.of(page, pageSize))));
    }

    @PostMapping("/kick")
    @SaCheckPermission(IdentityPermissions.SESSION_KICK)
    @Audited(module = "identity", action = "session-kick")
    @Operation(
            summary = "强制下线",
            description = "权限 identity:session:kick；按账号下线全部会话，tokenLast4 仅展示不作为定位键")
    public Result<OkResponse> kick(@Valid @RequestBody SessionKickCommand command) {
        appService.kick(command);
        return Result.ok(OkResponse.yes());
    }
}
