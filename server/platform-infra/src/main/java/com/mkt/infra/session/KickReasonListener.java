package com.mkt.infra.session;

import cn.dev33.satoken.listener.SaTokenListenerForSimple;

/** Writes D-02 kick-reason on replace (concurrent) and kickout (admin). */
public final class KickReasonListener extends SaTokenListenerForSimple {

    private final KickReasonStore store;

    public KickReasonListener(KickReasonStore store) {
        this.store = store;
    }

    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        if (tokenValue != null && !tokenValue.isBlank()) {
            store.write(loginType, tokenValue, KickReason.CONCURRENT);
        }
    }

    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        if (tokenValue != null && !tokenValue.isBlank()) {
            store.write(loginType, tokenValue, KickReason.ADMIN);
        }
    }
}
