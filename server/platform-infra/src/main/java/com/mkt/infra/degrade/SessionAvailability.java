package com.mkt.infra.degrade;

import com.mkt.infra.redis.KeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;

/** Session row of §6.8: Redis down → reject, never fail-open. */
public final class SessionAvailability {

    private final KeyValueStore store;

    public SessionAvailability(KeyValueStore store) {
        this.store = store;
    }

    public boolean available() {
        return store.ping();
    }

    public void requireAvailable() {
        if (!available()) {
            throw new BusinessException(CommonErrorCodes.SERVER_ERROR);
        }
    }
}
