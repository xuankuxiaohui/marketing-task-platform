package com.mkt.infra.degrade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import org.junit.jupiter.api.Test;

class DegradeMatrixTest {

    @Test
    void sessionAndNonceRejectRateLimitAllows() {
        assertThat(DegradeMatrix.action(DegradeComponent.SESSION)).isEqualTo(DegradeAction.REJECT);
        assertThat(DegradeMatrix.action(DegradeComponent.RATE_LIMIT)).isEqualTo(DegradeAction.ALLOW);
        assertThat(DegradeMatrix.action(DegradeComponent.NONCE)).isEqualTo(DegradeAction.REJECT);
        assertThat(DegradeMatrix.action(DegradeComponent.CLAIM_LOCK)).isEqualTo(DegradeAction.FALLBACK_CAS);
        assertThat(DegradeMatrix.rejectOnFailure(DegradeComponent.SESSION)).isTrue();
        assertThat(DegradeMatrix.allowOnFailure(DegradeComponent.RATE_LIMIT)).isTrue();
        assertThat(DegradeMatrix.rejectOnFailure(DegradeComponent.NONCE)).isTrue();
        for (DegradeComponent component : DegradeComponent.values()) {
            assertThat(DegradeMatrix.action(component)).isNotNull();
        }
    }

    @Test
    void sessionAvailabilityRejectsWhenRedisDown() {
        MemoryKeyValueStore store = new MemoryKeyValueStore();
        SessionAvailability session = new SessionAvailability(store);
        assertThat(session.available()).isTrue();
        store.setAvailable(false);
        assertThat(session.available()).isFalse();
        assertThatThrownBy(session::requireAvailable)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(CommonErrorCodes.SERVER_ERROR);
    }
}
