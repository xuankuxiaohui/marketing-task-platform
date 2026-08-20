package com.mkt.infra.session;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.auto.SaTokenDaoByObjectFollowString;
import com.mkt.infra.redis.KeyValueStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Sa-Token DAO over {@link KeyValueStore} (Redis DB 2). */
public final class SaTokenDaoKeyValue implements SaTokenDaoByObjectFollowString {

    private final KeyValueStore store;

    public SaTokenDaoKeyValue(KeyValueStore store) {
        this.store = store;
    }

    @Override
    public String get(String key) {
        return store.get(key);
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == 0 || timeout == SaTokenDao.NOT_VALUE_EXPIRE) {
            delete(key);
            return;
        }
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            store.set(key, value);
            return;
        }
        store.set(key, value, Duration.ofSeconds(timeout));
    }

    @Override
    public void update(String key, String value) {
        long timeout = getTimeout(key);
        if (timeout == SaTokenDao.NOT_VALUE_EXPIRE) {
            return;
        }
        set(key, value, timeout);
    }

    @Override
    public void delete(String key) {
        store.unlink(key);
    }

    @Override
    public long getTimeout(String key) {
        return store.ttlSeconds(key);
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (timeout == 0 || timeout == SaTokenDao.NOT_VALUE_EXPIRE) {
            delete(key);
            return;
        }
        if (timeout == SaTokenDao.NEVER_EXPIRE) {
            String value = store.get(key);
            if (value != null) {
                store.set(key, value);
            }
            return;
        }
        store.expire(key, Duration.ofSeconds(timeout));
    }

    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sortType) {
        String p = prefix == null ? "" : prefix;
        String kw = keyword == null ? "" : keyword;
        List<String> matched = new ArrayList<>();
        for (String key : store.keysByPattern(p + "*")) {
            if (key.startsWith(p) && key.contains(kw)) {
                matched.add(key);
            }
        }
        if (!sortType) {
            Collections.reverse(matched);
        }
        int from = Math.max(0, start);
        int to = size < 0 ? matched.size() : Math.min(matched.size(), from + size);
        if (from >= matched.size()) {
            return List.of();
        }
        return new ArrayList<>(matched.subList(from, to));
    }
}
