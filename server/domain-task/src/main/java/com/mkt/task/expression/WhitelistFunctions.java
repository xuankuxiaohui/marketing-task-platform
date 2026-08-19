package com.mkt.task.expression;

import java.util.Map;
import java.util.Set;

/** Closed 7-function whitelist (R11.9 / design §5.10). */
public final class WhitelistFunctions {

    public static final String PROVINCE = "province";
    public static final String USER_ROLE = "userRole";
    public static final String ORG_ID = "orgId";
    public static final String USER_LEVEL = "userLevel";
    public static final String HAS_TAG = "hasTag";
    public static final String REGISTER_WITHIN_DAYS = "registerWithinDays";
    public static final String IN_CROWD = "inCrowd";

    public static final Set<String> NAMES = Set.of(
            PROVINCE, USER_ROLE, ORG_ID, USER_LEVEL, HAS_TAG, REGISTER_WITHIN_DAYS, IN_CROWD);

    private static final Map<String, Integer> ARITY = Map.of(
            PROVINCE, 0,
            USER_ROLE, 0,
            ORG_ID, 0,
            USER_LEVEL, 0,
            HAS_TAG, 1,
            REGISTER_WITHIN_DAYS, 1,
            IN_CROWD, 1);

    private WhitelistFunctions() {}

    public static boolean known(String name) {
        return NAMES.contains(name);
    }

    public static int arity(String name) {
        Integer value = ARITY.get(name);
        return value == null ? -1 : value;
    }

    public static boolean stringResult(String name) {
        return PROVINCE.equals(name) || USER_ROLE.equals(name) || ORG_ID.equals(name);
    }

    public static boolean intResult(String name) {
        return USER_LEVEL.equals(name);
    }

    public static boolean booleanResult(String name) {
        return HAS_TAG.equals(name) || REGISTER_WITHIN_DAYS.equals(name) || IN_CROWD.equals(name);
    }
}
