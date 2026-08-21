package com.mkt.reward.domain;

import java.util.Set;

/** pnt_transaction.type closed set (design §3.5). */
public final class PointTypes {

    public static final String EARN = "EARN";
    public static final String CONSUME = "CONSUME";
    public static final String EXPIRE = "EXPIRE";
    public static final String ADJUST = "ADJUST";
    public static final String REVERSAL = "REVERSAL";

    public static final Set<String> ALL = Set.of(EARN, CONSUME, EXPIRE, ADJUST, REVERSAL);

    private PointTypes() {}

    public static boolean closed(String type) {
        return type != null && ALL.contains(type);
    }
}
