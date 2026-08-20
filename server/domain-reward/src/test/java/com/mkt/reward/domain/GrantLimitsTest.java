package com.mkt.reward.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.BypassRule;
import com.mkt.contract.GrantContext;
import com.mkt.contract.UserAttributes;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.entity.PrizeEntity;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class GrantLimitsTest {

    @Test
    void emptyLimitsPass() {
        PrizeEntity prize = new PrizeEntity();
        UserAttributes attrs = UserAttributes.notFound();
        GrantContext ctx = GrantContext.defaults();
        assertThat(GrantLimits.regionOk(prize, attrs, ctx)).isTrue();
        assertThat(GrantLimits.levelOk(prize, attrs, ctx)).isTrue();
        assertThat(GrantLimits.tagOk(prize, attrs, ctx)).isTrue();
    }

    @Test
    void regionLevelTagMatchAny() {
        PrizeEntity prize = new PrizeEntity();
        prize.setRegionLimit(RewardJson.strings(List.of("GD", "BJ")));
        prize.setLevelLimit(RewardJson.strings(List.of("1", "2")));
        prize.setTagLimit(RewardJson.strings(List.of("vip", "gold")));
        UserAttributes attrs = new UserAttributes(
                "gd", "user", "1", 1, List.of("vip"), Instant.EPOCH, AccountStatus.ACTIVE);
        GrantContext ctx = GrantContext.defaults();
        assertThat(GrantLimits.regionOk(prize, attrs, ctx)).isTrue();
        assertThat(GrantLimits.levelOk(prize, attrs, ctx)).isTrue();
        assertThat(GrantLimits.tagOk(prize, attrs, ctx)).isTrue();
    }

    @Test
    void missFailsUnlessBypassed() {
        PrizeEntity prize = new PrizeEntity();
        prize.setRegionLimit(RewardJson.strings(List.of("BJ")));
        UserAttributes attrs = new UserAttributes(
                "GD", "user", "1", 1, List.of(), Instant.EPOCH, AccountStatus.ACTIVE);
        assertThat(GrantLimits.regionOk(prize, attrs, GrantContext.defaults())).isFalse();
        GrantContext bypass = new GrantContext(null, List.of(BypassRule.REGION), 1L, false, null);
        assertThat(GrantLimits.regionOk(prize, attrs, bypass)).isTrue();
    }
}
