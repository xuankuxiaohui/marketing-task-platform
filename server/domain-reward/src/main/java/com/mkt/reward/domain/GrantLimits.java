package com.mkt.reward.domain;

import com.mkt.contract.BypassRule;
import com.mkt.contract.GrantContext;
import com.mkt.contract.GrantSource;
import com.mkt.contract.UserAttributes;
import com.mkt.reward.convert.RewardJson;
import com.mkt.reward.entity.PrizeEntity;
import java.util.List;

/** Region / level / tag match (design §5.6.2 / §5.7). Empty limit = pass. */
public final class GrantLimits {

    private GrantLimits() {}

    public static boolean regionOk(PrizeEntity prize, UserAttributes attrs, GrantContext ctx) {
        if (bypassed(ctx, BypassRule.REGION)) {
            return true;
        }
        List<String> limit = RewardJson.strings(prize.getRegionLimit());
        if (limit.isEmpty()) {
            return true;
        }
        String province = attrs == null ? null : attrs.province();
        return province != null && containsIgnoreCase(limit, province);
    }

    public static boolean levelOk(PrizeEntity prize, UserAttributes attrs, GrantContext ctx) {
        if (bypassed(ctx, BypassRule.LEVEL)) {
            return true;
        }
        List<String> limit = RewardJson.strings(prize.getLevelLimit());
        if (limit.isEmpty()) {
            return true;
        }
        if (attrs == null || attrs.userLevel() == null) {
            return false;
        }
        return containsIgnoreCase(limit, String.valueOf(attrs.userLevel()));
    }

    public static boolean tagOk(PrizeEntity prize, UserAttributes attrs, GrantContext ctx) {
        if (bypassed(ctx, BypassRule.TAG)) {
            return true;
        }
        List<String> limit = RewardJson.strings(prize.getTagLimit());
        if (limit.isEmpty()) {
            return true;
        }
        List<String> tags = attrs == null || attrs.tags() == null ? List.of() : attrs.tags();
        for (String tag : tags) {
            if (tag != null && containsIgnoreCase(limit, tag)) {
                return true;
            }
        }
        return false;
    }

    private static boolean bypassed(GrantContext ctx, BypassRule rule) {
        if (ctx == null || ctx.bypassRules() == null || ctx.bypassRules().isEmpty()) {
            return false;
        }
        return ctx.bypassRules().contains(rule);
    }

    public static boolean allowBypass(GrantSource source) {
        return source == GrantSource.MANUAL_GRANT;
    }

    private static boolean containsIgnoreCase(List<String> values, String want) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(want)) {
                return true;
            }
        }
        return false;
    }
}
