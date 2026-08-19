package com.mkt.risk.support;

import com.mkt.contract.RiskListType;
import com.mkt.infra.redis.KeyValueStore;
import com.mkt.risk.application.RiskListItemStore;
import com.mkt.risk.application.RiskListUk;
import com.mkt.risk.convert.RiskTime;
import com.mkt.risk.domain.ListEntry;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskListKeys;
import com.mkt.risk.entity.RiskListItemEntity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB is the source of truth; Redis is a point-lookup projection (design §3.10 / R25).
 */
@Component
public class RiskListProjection {

    private static final Logger log = LoggerFactory.getLogger(RiskListProjection.class);

    private final KeyValueStore store;
    private final RiskListItemStore listItemStore;
    private final Clock clock;

    public RiskListProjection(KeyValueStore store, RiskListItemStore listItemStore, Clock clock) {
        this.store = store;
        this.listItemStore = listItemStore;
        this.clock = clock;
    }

    public void scheduleReconcile(RiskDimension dimension, RiskListType listType, String listValue) {
        Runnable job = () -> reconcile(dimension, listType, listValue);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    job.run();
                }
            });
        } else {
            job.run();
        }
    }

    public void reconcile(RiskDimension dimension, RiskListType listType, String listValue) {
        String canonical = RiskListImportParser.normalizeOrNull(dimension, listValue);
        if (canonical == null) {
            return;
        }
        String key = RiskListKeys.of(dimension, listType, canonical);
        RiskListItemEntity row = listItemStore.getByUk(dimension.name(), listType.name(), canonical);
        Instant now = clock.instant();
        if (row == null || expired(row, now)) {
            redisIgnore(() -> store.unlink(key));
            return;
        }
        Instant expireAt = RiskTime.toInstant(row.getExpireAt());
        if (expireAt == null) {
            redisIgnore(() -> store.set(key, RiskListKeys.PERMANENT));
            return;
        }
        Duration ttl = Duration.between(now, expireAt);
        if (ttl.isZero() || ttl.isNegative()) {
            redisIgnore(() -> store.unlink(key));
            return;
        }
        redisIgnore(() -> store.set(key, String.valueOf(expireAt.toEpochMilli()), ttl));
    }

    public ListEntry lookup(RiskDimension dimension, RiskListType listType, String listValue) {
        String canonical = RiskListImportParser.normalizeOrNull(dimension, listValue);
        if (canonical == null) {
            return null;
        }
        Instant now = clock.instant();
        String key = RiskListKeys.of(dimension, listType, canonical);
        return resolve(
                dimension,
                listType,
                canonical,
                redisGet(key),
                now,
                () -> listItemStore.getByUk(dimension.name(), listType.name(), canonical));
    }

    /**
     * Same ghost / lost-window rules as {@link #lookup}, one Redis MGET + one DB IN.
     */
    public List<ListEntry> lookupMany(List<LookupKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Instant now = clock.instant();
        List<PreparedLookup> prepared = new ArrayList<>(keys.size());
        List<String> redisKeys = new ArrayList<>(keys.size());
        List<RiskListUk> uks = new ArrayList<>(keys.size());
        for (LookupKey key : keys) {
            String canonical = RiskListImportParser.normalizeOrNull(key.dimension(), key.listValue());
            if (canonical == null) {
                continue;
            }
            String redisKey = RiskListKeys.of(key.dimension(), key.listType(), canonical);
            prepared.add(new PreparedLookup(key.dimension(), key.listType(), canonical, redisKey));
            redisKeys.add(redisKey);
            uks.add(new RiskListUk(key.dimension().name(), key.listType().name(), canonical));
        }
        Map<String, String> cached = redisGetMany(redisKeys);
        Map<String, RiskListItemEntity> rows = indexByUk(listItemStore.listByUks(uks));
        List<ListEntry> entries = new ArrayList<>(prepared.size());
        for (PreparedLookup item : prepared) {
            ListEntry found = resolve(
                    item.dimension(),
                    item.listType(),
                    item.canonical(),
                    cached.get(item.redisKey()),
                    now,
                    () -> rows.get(ukIndex(item.dimension().name(), item.listType().name(), item.canonical())));
            if (found != null) {
                entries.add(found);
            }
        }
        return entries;
    }

    public record LookupKey(RiskDimension dimension, RiskListType listType, String listValue) {}

    private record PreparedLookup(
            RiskDimension dimension, RiskListType listType, String canonical, String redisKey) {}

    private ListEntry resolve(
            RiskDimension dimension,
            RiskListType listType,
            String canonical,
            String cached,
            Instant now,
            Supplier<RiskListItemEntity> db) {
        String key = RiskListKeys.of(dimension, listType, canonical);
        if (cached != null && !expiredCache(cached, now)) {
            RiskListItemEntity row = db.get();
            if (row == null || expired(row, now)) {
                redisIgnore(() -> store.unlink(key));
                return null;
            }
            return toEntry(row);
        }
        RiskListItemEntity row = db.get();
        if (row == null || expired(row, now)) {
            if (cached != null) {
                redisIgnore(() -> store.unlink(key));
            }
            return null;
        }
        reconcile(dimension, listType, canonical);
        return toEntry(row);
    }

    private String redisGet(String key) {
        try {
            return store.get(key);
        } catch (RuntimeException ex) {
            log.warn("risk:list redis get failed, falling back to db key={}", key);
            return null;
        }
    }

    private Map<String, String> redisGetMany(List<String> keys) {
        if (keys.isEmpty()) {
            return Map.of();
        }
        try {
            return store.getMany(keys);
        } catch (RuntimeException ex) {
            log.warn("risk:list redis mget failed, falling back to db size={}", keys.size());
            return Map.of();
        }
    }

    private static Map<String, RiskListItemEntity> indexByUk(List<RiskListItemEntity> rows) {
        Map<String, RiskListItemEntity> indexed = new HashMap<>();
        if (rows == null) {
            return indexed;
        }
        for (RiskListItemEntity row : rows) {
            indexed.put(ukIndex(row.getDimension(), row.getListType(), row.getListValue()), row);
        }
        return indexed;
    }

    private static String ukIndex(String dimension, String listType, String listValue) {
        return dimension + '\0' + listType + '\0' + listValue;
    }

    private void redisIgnore(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException ex) {
            log.warn("risk:list redis write skipped: {}", ex.toString());
        }
    }

    private static boolean expired(RiskListItemEntity row, Instant now) {
        Instant expireAt = RiskTime.toInstant(row.getExpireAt());
        return expireAt != null && !expireAt.isAfter(now);
    }

    private static boolean expiredCache(String cached, Instant now) {
        if (RiskListKeys.PERMANENT.equals(cached)) {
            return false;
        }
        try {
            long millis = Long.parseLong(cached);
            return Instant.ofEpochMilli(millis).compareTo(now) <= 0;
        } catch (NumberFormatException ex) {
            return true;
        }
    }

    private static ListEntry toEntry(RiskListItemEntity row) {
        RiskDimension dimension = RiskDimension.valueOf(row.getDimension());
        String value = RiskListImportParser.normalizeOrNull(dimension, row.getListValue());
        return new ListEntry(
                dimension,
                RiskListType.valueOf(row.getListType()),
                value == null ? row.getListValue() : value,
                RiskTime.toInstant(row.getExpireAt()),
                row.denyLoginFlag());
    }
}
