package com.mkt.infra.session;

import cn.dev33.satoken.stp.StpLogic;

/** Client StpLogic holder. Not a Spring bean (spike 2 / SaBeanInject). */
public final class StpClient {

    public static final String TYPE = "client";
    public static final String PREFIX = "client:";
    public static final StpLogic LOGIC = new StpLogic(TYPE);

    private StpClient() {
    }
}
