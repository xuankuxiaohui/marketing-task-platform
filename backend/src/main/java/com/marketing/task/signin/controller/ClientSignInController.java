package com.marketing.task.signin.controller;

import com.marketing.task.common.Result;
import com.marketing.task.context.UserContextHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.marketing.task.signin.domain.dto.SignInCalendarVO;
import com.marketing.task.signin.domain.dto.SignInResult;
import com.marketing.task.signin.domain.dto.SignInStatusVO;
import com.marketing.task.signin.domain.entity.PointAccount;
import com.marketing.task.signin.domain.entity.PointTransaction;
import com.marketing.task.signin.domain.entity.SignInConfig;
import com.marketing.task.signin.service.PointService;
import com.marketing.task.signin.service.SignInService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Tag(name = "Client - Sign-in", description = "C端签到")
@RestController
@RequestMapping("/api/client/signin")
@RequiredArgsConstructor
public class ClientSignInController {
    private final SignInService signInService;
    private final PointService pointService;

    @Operation(summary = "获取签到配置列表")
    @GetMapping("/configs")
    public Result<List<SignInConfig>> listConfigs() {
        return Result.ok(signInService.listActiveConfigs());
    }

    @Operation(summary = "签到")
    @PostMapping("/{configId}/sign")
    public Result<SignInResult> signIn(@PathVariable Long configId) {
        String userId = UserContextHolder.get().getUserId();
        SignInResult result = signInService.signIn(configId, userId);
        return Result.ok(result);
    }

    @Operation(summary = "补签")
    @PostMapping("/{configId}/catch-up")
    public Result<SignInResult> catchUp(@PathVariable Long configId, @RequestBody Map<String, String> body) {
        String userId = UserContextHolder.get().getUserId();
        LocalDate targetDate = LocalDate.parse(body.get("targetDate"));
        SignInResult result = signInService.catchUp(configId, userId, targetDate);
        return Result.ok(result);
    }

    @Operation(summary = "获取签到日历")
    @GetMapping("/{configId}/calendar")
    public Result<SignInCalendarVO> getCalendar(
            @PathVariable Long configId,
            @RequestParam String periodKey) {
        String userId = UserContextHolder.get().getUserId();
        return Result.ok(signInService.getCalendar(configId, userId, periodKey));
    }

    @Operation(summary = "获取签到状态")
    @GetMapping("/{configId}/status")
    public Result<SignInStatusVO> getStatus(@PathVariable Long configId) {
        String userId = UserContextHolder.get().getUserId();
        return Result.ok(signInService.getStatus(configId, userId));
    }

    @Operation(summary = "获取积分余额")
    @GetMapping("/points/balance")
    public Result<PointAccount> getBalance() {
        String userId = UserContextHolder.get().getUserId();
        return Result.ok(pointService.getBalance(userId));
    }

    @Operation(summary = "获取积分交易记录")
    @GetMapping("/points/transactions")
    public Result<?> getTransactions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String type) {
        String userId = UserContextHolder.get().getUserId();
        return Result.ok(pointService.getTransactions(userId, type, page, size));
    }
}
