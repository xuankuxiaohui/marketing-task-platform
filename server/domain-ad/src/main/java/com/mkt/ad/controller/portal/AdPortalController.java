package com.mkt.ad.controller.portal;

import com.mkt.ad.application.AdPortalAppService;
import com.mkt.ad.command.AdDismissCommand;
import com.mkt.ad.response.OkResponse;
import com.mkt.ad.response.PortalAdPositionView;
import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/ad")
@Tag(name = "ad")
public class AdPortalController {

    private final AdPortalAppService portal;

    public AdPortalController(AdPortalAppService portal) {
        this.portal = portal;
    }

    @GetMapping("/positions/{code}")
    @Operation(summary = "拉取广告位素材（匿名可访问，R30.6）")
    public Result<PortalAdPositionView> pull(
            @PathVariable String code,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            @RequestHeader(value = "X-Client-Platform", required = false) String platform) {
        Long userId = UserContext.current().map(p -> p.userId()).orElse(null);
        return Result.ok(portal.pull(code, userId, deviceId, platform));
    }

    @PostMapping("/materials/{id}/dismiss")
    @Operation(summary = "关闭悬浮素材，消耗当日剩余频控（R30.11）")
    public Result<OkResponse> dismiss(
            @PathVariable long id,
            @Valid @RequestBody AdDismissCommand command,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId) {
        Long userId = UserContext.current().map(p -> p.userId()).orElse(null);
        return Result.ok(portal.dismiss(command.positionCode(), id, userId, deviceId));
    }
}
