package com.mkt.tracking.controller.portal;

import com.mkt.kernel.Result;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.tracking.application.TrackBatchService;
import com.mkt.tracking.command.TrackBatchCommand;
import com.mkt.tracking.command.TrackIdentity;
import com.mkt.tracking.response.TrackBatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/common/track")
@Tag(name = "track")
public class TrackBatchController {

    private final TrackBatchService trackBatchService;

    public TrackBatchController(TrackBatchService trackBatchService) {
        this.trackBatchService = trackBatchService;
    }

    @PostMapping("/batch")
    @Operation(
            summary = "客户端批量上报",
            description = "匿名可访问；部分接受；错误 track.batch.overflow / track.batch.rate-limited / common.param-invalid")
    public Result<TrackBatchResponse> batch(
            @RequestBody TrackBatchCommand command,
            @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
            HttpServletRequest request) {
        Long userId = UserContext.current().map(UserPrincipal::userId).orElse(null);
        TrackIdentity identity = new TrackIdentity(userId, deviceId, clientIp(request));
        return Result.ok(trackBatchService.ingest(command, identity));
    }

    static String clientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
        }
        return request.getRemoteAddr();
    }
}
