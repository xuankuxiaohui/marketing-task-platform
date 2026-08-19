package com.mkt.risk.support;

import com.mkt.infra.redis.KeyValueStore;
import com.mkt.risk.domain.RiskCntKeys;
import com.mkt.risk.domain.RiskRuleCode;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** ZSET window: trim = ZREMRANGEBYSCORE, count = ZCOUNT (design §5.9). */
@Component
public class RiskCntWindow {

    /** Longest appendix A window (R-b / R-d): 7 days. Async writers trim to this. */
    static final long RETAIN_MILLIS = 604_800_000L;

    private final KeyValueStore store;

    public RiskCntWindow(KeyValueStore store) {
        this.store = store;
    }

    public void add(RiskRuleCode rule, String dimension, String value, String member, long scoreMillis) {
        String key = RiskCntKeys.of(rule, dimension, value);
        store.zremrangeByScore(key, Double.NEGATIVE_INFINITY, scoreMillis - RETAIN_MILLIS - 1);
        store.zadd(key, scoreMillis, member);
    }

    public long count(RiskRuleCode rule, String dimension, String value, long windowSeconds, long nowMillis) {
        String key = RiskCntKeys.of(rule, dimension, value);
        double min = nowMillis - windowSeconds * 1000.0;
        store.zremrangeByScore(key, Double.NEGATIVE_INFINITY, min - 1);
        return store.zcount(key, min, nowMillis);
    }

    public long addAndCount(
            RiskRuleCode rule, String dimension, String value, String member, long windowSeconds, long nowMillis) {
        add(rule, dimension, value, member, nowMillis);
        return count(rule, dimension, value, windowSeconds, nowMillis);
    }

    /** {@code {ts}:{uuid}} so two JVMs cannot collide in the same millisecond (design §5.9). */
    public String uniqueMember(long nowMillis) {
        return nowMillis + ":" + UUID.randomUUID();
    }
}
