package com.mkt.activity.testsupport;

import com.mkt.contract.AccountStatus;
import com.mkt.contract.UserAttributePort;
import com.mkt.contract.UserAttributes;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class StubUserAttributePort implements UserAttributePort {

    public final ConcurrentHashMap<Long, UserAttributes> byId = new ConcurrentHashMap<>();

    public StubUserAttributePort() {
        put(9L, "110000", Instant.parse("2026-08-01T00:00:00Z"));
    }

    public void put(long userId, String province, Instant registeredAt) {
        byId.put(
                userId,
                new UserAttributes(province, "user", null, 1, List.of(), registeredAt, AccountStatus.ACTIVE));
    }

    @Override
    public UserAttributes attributes(long userId) {
        return byId.getOrDefault(userId, UserAttributes.notFound());
    }

    @Override
    public UserAttributes lockAndGet(long userId) {
        return attributes(userId);
    }
}
