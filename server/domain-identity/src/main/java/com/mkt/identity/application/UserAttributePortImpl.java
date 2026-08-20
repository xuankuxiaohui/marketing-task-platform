package com.mkt.identity.application;

import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import com.mkt.identity.convert.UserAttributeConvert;
import com.mkt.identity.entity.PortalUserEntity;
import com.mkt.identity.mapper.PortalUserMapper;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Identity-owned {@link UserAttributePort} (design §2.2.3 / D-12). */
@Service
public class UserAttributePortImpl implements UserAttributePort {

    private final PortalUserMapper users;
    private final PlatformCache cache;

    public UserAttributePortImpl(PortalUserMapper users, PlatformCache cache) {
        this.users = users;
        this.cache = cache;
    }

    @Override
    public UserAttributes attributes(long userId) {
        return cache.get(
                CacheNamespace.IDENTITY_USER_ATTR,
                String.valueOf(userId),
                UserAttributes.class,
                () -> UserAttributeConvert.from(users.selectById(userId)));
    }

    @Override
    @Transactional
    public UserAttributes lockAndGet(long userId) {
        PortalUserEntity entity = users.lockById(userId);
        return UserAttributeConvert.from(entity);
    }

    public void evictAfterCommit(long userId) {
        cache.evictAfterCommit(CacheNamespace.IDENTITY_USER_ATTR, String.valueOf(userId));
    }
}
