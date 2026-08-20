package com.mkt.reward.points;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.reward.application.PointsAppService;
import com.mkt.reward.testsupport.MemoryPointsStore;
import java.time.Clock;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class PointsPortImplTest {

    @Test
    void earnDelegatesToLedger() {
        MemoryPointsStore store = new MemoryPointsStore();
        PointsAppService service = new PointsAppService(store, (PlatformTransactionManager) null, Clock.systemUTC());
        PointsPort port = new PointsPortImpl(service);
        long txId = port.earn(9L, 12, Instant.EPOCH, "TASK_STEP", "s-1");
        assertThat(txId).isPositive();
        assertThat(service.balanceOrZero(9L)).isEqualTo(12L);
        assertThat(port.consume(9L, 4, "SIGNIN", "c-1", "catchup")).isEqualTo(8L);
        assertThat(service.balanceOrZero(9L)).isEqualTo(8L);
    }
}
