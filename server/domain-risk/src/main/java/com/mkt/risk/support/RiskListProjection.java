package com.mkt.risk.support;

import com.mkt.contract.RiskListType;
import com.mkt.infra.redis.KeyValueStore;
import com.mkt.risk.convert.RiskTime;
import com.mkt.risk.domain.ListEntry;
import com.mkt.risk.domain.RiskDimension;
import com.mkt.risk.domain.RiskListKeys;
import com.mkt.risk.application.RiskListItemStore;
import com.mkt.risk.entity.RiskListItemEntity;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB is the source of truth; Redis is a point-lookup projection (design §3.10 / R25).
 */
@Component
public class RiskListProjection {

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
            store.unlink(key);
            return;
        }
        Instant expireAt = RiskTime.toInstant(row.getExpireAt());
        if (expireAt == null) {
            store.set(key, RiskListKeys.PERMANENT);
            return;
        }
        Duration ttl = Duration.between(now, expireAt);
        if (ttl.isZero() || ttl.isNegative()) {
            store.unlink(key);
            return;
        }
        store.set(key, String.valueOf(expireAt.toEpochMilli()), ttl);
    }

    public ListEntry lookup(RiskDimension dimension, RiskListType listType, String listValue) {
        String canonical = RiskListImportParser.normalizeOrNull(dimension, listValue);
        if (canonical == null) {
            return null;
        }
        Instant now = clock.instant();
        String key = RiskListKeys.of(dimension, listType, canonical);
        String cached = store.get(key);
        if (cached != null && !expiredCache(cached, now)) {
            RiskListItemEntity row = listItemStore.getByUk(dimension.name(), listType.name(), canonical);
            if (row == null || expired(row, now)) {
                store.unlink(key);
                return null;
            }
            return toEntry(row);
        }
        RiskListItemEntity row = listItemStore.getByUk(dimension.name(), listType.name(), canonical);
        if (row == null || expired(row, now)) {
            if (cached != null) {
                store.unlink(key);
            }
            return null;
        }
        reconcile(dimension, listType, canonical);
        return toEntry(row);
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
