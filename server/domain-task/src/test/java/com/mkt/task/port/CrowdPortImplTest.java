package com.mkt.task.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.task.domain.CrowdStatuses;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CrowdPortImplTest {

    @Test
    void memberOfAndCodeRespectEnabledAndMembership() {
        MemoryTaskCrowdStore store = new MemoryTaskCrowdStore();
        TaskCrowdEntity pack = new TaskCrowdEntity();
        pack.setCode("vip");
        pack.setName("VIP");
        pack.setStatus(CrowdStatuses.ENABLED);
        store.insert(pack);
        long crowdId = pack.getId();
        store.insertMemberIgnore(crowdId, 9L);
        CrowdPortImpl port = new CrowdPortImpl(store, user(9L, AccountStatus.ACTIVE));
        assertThat(port.memberOf(crowdId, 9L)).isTrue();
        assertThat(port.memberOf(crowdId, 8L)).isFalse();
        assertThat(port.memberOfCode("vip", 9L)).isTrue();
        assertThat(port.memberOfCode("vip", 8L)).isFalse();
        assertThat(port.memberOfCode("missing", 9L)).isFalse();
    }

    private static UserAttributePort user(long id, AccountStatus status) {
        return new UserAttributePort() {
            @Override
            public UserAttributes attributes(long userId) {
                if (userId != id) {
                    return new UserAttributes(null, null, null, null, List.of(), null, AccountStatus.NOT_FOUND);
                }
                return new UserAttributes(
                        "110000", "u", null, 1, List.of(), Instant.parse("2026-01-01T00:00:00Z"), status);
            }

            @Override
            public UserAttributes lockAndGet(long userId) {
                return attributes(userId);
            }
        };
    }
}
