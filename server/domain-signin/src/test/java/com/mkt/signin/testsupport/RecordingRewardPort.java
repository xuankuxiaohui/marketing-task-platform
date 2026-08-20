package com.mkt.signin.testsupport;

import com.mkt.contract.FulfillmentStatus;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantResult;
import com.mkt.contract.GrantSource;
import com.mkt.contract.GrantStatus;
import com.mkt.contract.PrizeSummary;
import com.mkt.contract.RewardPort;
import com.mkt.contract.UserRewardSummary;
import com.mkt.kernel.BusinessException;
import com.mkt.signin.support.SigninErrorCodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class RecordingRewardPort implements RewardPort {

    public record GrantCall(long prizeId, long userId, GrantSource source, String sourceId, GrantContext ctx) {}

    public record ConsumeCall(long userId, int points, String sourceType, String sourceId, String remark) {}

    public final List<GrantCall> grants = new CopyOnWriteArrayList<>();
    public final List<ConsumeCall> consumes = new CopyOnWriteArrayList<>();
    public RuntimeException consumeFail;
    public long balance = 1_000L;
    public Set<Long> enabledPrizes = ConcurrentHashMap.newKeySet();
    private final AtomicLong ids = new AtomicLong(1);
    private final Set<String> grantKeys = ConcurrentHashMap.newKeySet();

    public RecordingRewardPort() {
        enabledPrizes.add(10L);
        enabledPrizes.add(11L);
        enabledPrizes.add(12L);
    }

    @Override
    public GrantResult grant(long prizeId, long userId, GrantSource grantSource, String sourceId, GrantContext ctx) {
        String key = grantSource + ":" + sourceId + ":" + prizeId;
        boolean hit = !grantKeys.add(key);
        grants.add(new GrantCall(prizeId, userId, grantSource, sourceId, ctx));
        return new GrantResult(ids.getAndIncrement(), GrantStatus.GRANTED, FulfillmentStatus.ARRIVED, prizeId, hit);
    }

    @Override
    public UserRewardSummary userSummary(long userId) {
        return new UserRewardSummary(balance, new PrizeSummary(0L, 0L));
    }

    @Override
    public boolean prizeEnabled(long prizeId) {
        return enabledPrizes.contains(prizeId);
    }

    @Override
    public long consume(long userId, int points, String sourceType, String sourceId, String remark) {
        if (consumeFail != null) {
            throw consumeFail;
        }
        if (points > balance) {
            throw new BusinessException(SigninErrorCodes.CATCHUP_NOT_ALLOWED, "积分余额不足");
        }
        balance -= points;
        consumes.add(new ConsumeCall(userId, points, sourceType, sourceId, remark));
        return balance;
    }

    public List<GrantCall> uniqueGrants() {
        List<GrantCall> out = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (GrantCall call : grants) {
            String key = call.source() + ":" + call.sourceId() + ":" + call.prizeId();
            if (seen.add(key)) {
                out.add(call);
            }
        }
        return out;
    }
}
