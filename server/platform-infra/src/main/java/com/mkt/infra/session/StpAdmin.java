package com.mkt.infra.session;

import cn.dev33.satoken.stp.StpLogic;

/** Admin StpLogic holder. Not a Spring bean (spike 2 / SaBeanInject). */
public final class StpAdmin {

    public static final String TYPE = "admin";
    public static final String PREFIX = "admin:";
    public static final StpLogic LOGIC = new StpLogic(TYPE);

    private StpAdmin() {
    }
}
