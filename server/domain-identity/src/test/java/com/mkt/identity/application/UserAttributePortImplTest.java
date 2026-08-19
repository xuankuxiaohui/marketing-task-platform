package com.mkt.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributes;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.mapper.PortalUserMapper;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.json.JsonUtil;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UserAttributePortImplTest {

    private final PortalUserMapper users = Mockito.mock(PortalUserMapper.class);
    private PlatformCache cache;
    private UserAttributePortImpl port;

    @BeforeEach
    void setUp() {
        cache = new TwoLevelPlatformCache(new MemoryKeyValueStore());
        port = new UserAttributePortImpl(users, cache);
    }

    @Test
    void attributesUsesCacheAndLockAndGetDoesNot() {
        PortalUserEntity entity = live(7L, "ENABLED", 0, "GD", "3", "org-north_01", List.of("a"));
        when(users.selectById(7L)).thenReturn(entity);
        UserAttributes first = port.attributes(7L);
        UserAttributes second = port.attributes(7L);
        assertThat(first.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(first.province()).isEqualTo("GD");
        assertThat(first.userLevel()).isEqualTo(3);
        assertThat(first.orgId()).isEqualTo("org-north_01");
        assertThat(first.tags()).containsExactly("a");
        assertThat(second).isEqualTo(first);
        verify(users, Mockito.times(1)).selectById(7L);

        when(users.lockById(7L)).thenReturn(entity);
        UserAttributes locked = port.lockAndGet(7L);
        assertThat(locked.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(users).lockById(7L);
        verify(users, never()).selectById(Mockito.eq(8L));
        AtomicInteger loaderHits = new AtomicInteger();
        cache.get(CacheNamespace.IDENTITY_USER_ATTR, "7", UserAttributes.class, () -> {
            loaderHits.incrementAndGet();
            return UserAttributes.notFound();
        });
        assertThat(loaderHits.get()).isZero();
    }

    @Test
    void accountStatusFourStates() {
        when(users.selectById(1L)).thenReturn(null);
        assertThat(port.attributes(1L).accountStatus()).isEqualTo(AccountStatus.NOT_FOUND);

        when(users.selectById(2L)).thenReturn(live(2L, "DISABLED", 1, null, null, null, List.of()));
        assertThat(port.attributes(2L).accountStatus()).isEqualTo(AccountStatus.DELETED);

        when(users.selectById(3L)).thenReturn(live(3L, "DISABLED", 0, null, "x", "9", List.of()));
        UserAttributes disabled = port.attributes(3L);
        assertThat(disabled.accountStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(disabled.userLevel()).isNull();
        assertThat(disabled.orgId()).isEqualTo("9");

        when(users.selectById(4L)).thenReturn(live(4L, "ENABLED", 0, "GD", "1", "10", List.of()));
        assertThat(port.attributes(4L).accountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private static PortalUserEntity live(
            long id, String status, int deleted, String province, String level, String orgId, List<String> tags) {
        PortalUserEntity entity = new PortalUserEntity();
        entity.setId(id);
        entity.setUsername("u" + id);
        entity.setNickname("n");
        entity.setStatus(status);
        entity.setDeleted(deleted);
        entity.setProvince(province);
        entity.setUserLevel(level);
        entity.setOrgId(orgId);
        entity.setTags(JsonUtil.toJson(tags));
        entity.setRegisteredAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return entity;
    }
}
