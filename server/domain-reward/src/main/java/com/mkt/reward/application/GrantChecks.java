package com.mkt.reward.application;

import com.mkt.contract.BypassRule;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.contract.RiskAction;
import com.mkt.contract.RiskCheckPort;
import com.mkt.contract.RiskScene;
import com.mkt.contract.RiskSubject;
import com.mkt.contract.RiskVerdict;
import com.mkt.kernel.BusinessException;
import com.mkt.reward.domain.ClaimWindows;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import com.mkt.reward.entity.PrizeEntity;
import com.mkt.reward.support.RewardErrorCodes;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Claim-limit, risk, and bypass helpers extracted from {@link GrantAppService} (R2-1).
 * Move-only: assertClaimLimits / occupyingSlot / checkRisk / clientIp / clientDevice /
 * stripBypass / parseBypass semantics unchanged.
 */
@Component
public class GrantChecks {

    private final GrantRecordStore grants;
    private final RiskCheckPort risk;
    private final Clock clock;

    @Autowired
    public GrantChecks(GrantRecordStore grants, ObjectProvider<RiskCheckPort> risk, Clock clock) {
        this(grants, risk.getIfAvailable(), clock);
    }

    GrantChecks(GrantRecordStore grants, RiskCheckPort risk, Clock clock) {
        this.grants = grants;
        this.risk = risk;
        this.clock = clock;
    }

    void assertClaimLimits(PrizeEntity prize, long userId, GrantRecordEntity existing) {
        int daily = prize.getDailyClaimLimit() == null ? 0 : prize.getDailyClaimLimit();
        int total = prize.getTotalClaimLimit() == null ? 0 : prize.getTotalClaimLimit();
        long self = occupyingSlot(existing) ? 1 : 0;
        if (daily > 0) {
            long used = grants.countActive(userId, prize.getId(), ClaimWindows.dailyStartUtc(clock)) - self;
            if (used >= daily) {
                throw new BusinessException(RewardErrorCodes.CLAIM_LIMIT_EXCEEDED, "当日限领已达上限");
            }
        }
        if (total > 0) {
            long used = grants.countActive(userId, prize.getId(), null) - self;
            if (used >= total) {
                throw new BusinessException(RewardErrorCodes.CLAIM_LIMIT_EXCEEDED, "累计限领已达上限");
            }
        }
    }

    private static boolean occupyingSlot(GrantRecordEntity existing) {
        return existing != null && GrantRecordStatuses.LIMIT_COUNT.contains(existing.getStatus());
    }

    void checkRisk(long userId, GrantSource grantSource, GrantContext ctx) {
        if (risk == null) {
            return;
        }
        Long elapsed = ctx.simulated() || grantSource != GrantSource.TASK_STEP ? null : ctx.elapsedSeconds();
        RiskVerdict verdict = risk.check(
                RiskScene.GRANT,
                new RiskSubject(userId, clientIp(ctx), clientDevice(ctx), elapsed, ctx.simulated()));
        if (verdict.action() == RiskAction.REJECT || verdict.action() == RiskAction.SILENT_REJECT) {
            if (grantSource == GrantSource.MANUAL_GRANT) {
                throw new BusinessException(RewardErrorCodes.RISK_BLOCKED_ACCOUNT);
            }
            throw new BusinessException(RewardErrorCodes.RISK_BLOCKED_GENERIC);
        }
    }

    private static String clientIp(GrantContext ctx) {
        String ip = ctx.ip();
        if (ip == null || ip.isBlank() || "0.0.0.0".equals(ip)) {
            return null;
        }
        return ip;
    }

    private static String clientDevice(GrantContext ctx) {
        String device = ctx.deviceId();
        return device == null || device.isBlank() ? null : device;
    }

    GrantContext stripBypass(GrantContext ctx) {
        return new GrantContext(
                ctx.reason(),
                List.of(),
                ctx.operatorId(),
                ctx.simulated(),
                ctx.elapsedSeconds(),
                ctx.ip(),
                ctx.deviceId());
    }

    List<BypassRule> parseBypass(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<BypassRule> rules = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            try {
                rules.add(BypassRule.valueOf(item.trim()));
            } catch (IllegalArgumentException ignored) {
                // only REGION/LEVEL/TAG are defined
            }
        }
        return List.copyOf(rules);
    }
}
