package com.mkt.task.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.task.entity.TaskCrowdEntity;
import com.mkt.task.testsupport.MemoryTaskCrowdStore;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class StoreCrowdResolverTest {

    @Test
    void missingDisabledAndMember() {
        MemoryTaskCrowdStore store = new MemoryTaskCrowdStore();
        TaskCrowdEntity enabled = new TaskCrowdEntity();
        enabled.setCode("vip");
        enabled.setName("vip");
        enabled.setStatus("ENABLED");
        enabled.setItemCount(0);
        store.insert(enabled);
        store.insertMemberIgnore(enabled.getId(), 9L);
        TaskCrowdEntity disabled = new TaskCrowdEntity();
        disabled.setCode("old");
        disabled.setName("old");
        disabled.setStatus("DISABLED");
        store.insert(disabled);
        UserAttributePort users = mock(UserAttributePort.class);
        when(users.attributes(9L))
                .thenReturn(new UserAttributes(
                        "GD", "u", "1", 1, List.of(), Instant.parse("2026-01-01T00:00:00Z"), AccountStatus.ACTIVE));
        StoreCrowdResolver resolver = new StoreCrowdResolver(store, users, 9L);
        assertThat(resolver.contains("missing")).isFalse();
        assertThat(resolver.contains("old")).isFalse();
        assertThat(resolver.contains("vip")).isTrue();
        assertThat(new StoreCrowdResolver(store, users, null).contains("vip")).isFalse();
    }
}
