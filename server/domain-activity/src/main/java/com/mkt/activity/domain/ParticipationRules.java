package com.mkt.activity.domain;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

/**
 * Activity participation chain (R22.3). Not task claim §5.5.
 * Order: allowlist → new-user → user daily → user total → global daily → region.
 */
public final class ParticipationRules {

    private ParticipationRules() {}

    public static String firstReject(
            Collection<Long> allowUserIds,
            Collection<String> allowCrowdCodes,
            boolean newUserOnly,
            int newUserDays,
            Integer userDailyLimit,
            Integer userTotalLimit,
            Integer globalDailyLimit,
            int userDailyPass,
            int userTotalPass,
            int globalDailyPass,
            Collection<String> regions,
            long userId,
            UserAttributes attrs,
            Instant now) {
        if (allowlistConfigured(allowUserIds, allowCrowdCodes) && !containsUser(allowUserIds, userId)) {
            return HitRules.ALLOWLIST;
        }
        if (newUserOnly && !isNewUser(attrs, now, newUserDays)) {
            return HitRules.NEW_USER;
        }
        if (exceeded(userDailyLimit, userDailyPass)) {
            return HitRules.USER_DAILY;
        }
        if (exceeded(userTotalLimit, userTotalPass)) {
            return HitRules.USER_TOTAL;
        }
        if (exceeded(globalDailyLimit, globalDailyPass)) {
            return HitRules.GLOBAL_DAILY;
        }
        if (regionConfigured(regions) && !regionHit(regions, attrs)) {
            return HitRules.REGION;
        }
        return null;
    }

    public static boolean allowlistConfigured(Collection<Long> allowUserIds, Collection<String> allowCrowdCodes) {
        return notEmpty(allowUserIds) || notEmpty(allowCrowdCodes);
    }

    public static boolean isNewUser(UserAttributes attrs, Instant now, int newUserDays) {
        if (attrs == null || attrs.registeredAt() == null || now == null || newUserDays < 1) {
            return false;
        }
        if (attrs.accountStatus() != AccountStatus.ACTIVE) {
            return false;
        }
        long days = ChronoUnit.DAYS.between(attrs.registeredAt(), now);
        return days >= 0 && days <= newUserDays;
    }

    private static boolean containsUser(Collection<Long> allowUserIds, long userId) {
        return allowUserIds != null && allowUserIds.contains(userId);
    }

    private static boolean exceeded(Integer limit, int used) {
        return limit != null && used >= limit;
    }

    private static boolean regionConfigured(Collection<String> regions) {
        return notEmpty(regions);
    }

    private static boolean regionHit(Collection<String> regions, UserAttributes attrs) {
        if (attrs == null || attrs.province() == null || attrs.province().isBlank()) {
            return false;
        }
        return regions.contains(attrs.province());
    }

    private static boolean notEmpty(Collection<?> values) {
        return values != null && !values.isEmpty();
    }

    public static List<Long> copyLongs(Collection<Long> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }

    public static List<String> copyStrings(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return List.copyOf(values);
    }
}
