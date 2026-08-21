package com.mkt.risk.support;

import com.mkt.contract.RiskListType;

/** Appendix B / design §4.6 permission codes by list type. */
public final class RiskListPermissions {

    public static final String BLACK_QUERY = "risk:blacklist:query";
    public static final String WHITE_QUERY = "risk:whitelist:query";
    public static final String BLACK_ADD = "risk:blacklist:add";
    public static final String WHITE_ADD = "risk:whitelist:add";
    public static final String BLACK_REMOVE = "risk:blacklist:remove";
    public static final String WHITE_REMOVE = "risk:whitelist:remove";
    public static final String BLACK_IMPORT = "risk:blacklist:import";
    public static final String CASE_QUERY = "risk:case:query";
    public static final String CASE_HANDLE = "risk:case:handle";
    public static final String RULE_QUERY = "risk:rule:query";
    public static final String RULE_CONFIG = "risk:rule:config";

    private RiskListPermissions() {
    }

    public static String query(RiskListType listType) {
        return listType == RiskListType.WHITE ? WHITE_QUERY : BLACK_QUERY;
    }

    public static String add(RiskListType listType) {
        return listType == RiskListType.WHITE ? WHITE_ADD : BLACK_ADD;
    }

    public static String remove(RiskListType listType) {
        return listType == RiskListType.WHITE ? WHITE_REMOVE : BLACK_REMOVE;
    }

    public static String importCode(RiskListType listType) {
        if (listType != RiskListType.BLACK) {
            throw new IllegalArgumentException("appendix B has no whitelist import");
        }
        return BLACK_IMPORT;
    }
}
