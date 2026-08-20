package com.mkt.reward.testsupport;

import com.mkt.reward.application.GrantRecordStore;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryGrantRecordStore implements GrantRecordStore {

    private final ConcurrentHashMap<Long, GrantRecordEntity> rows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> keys = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> persistedStatus = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public GrantRecordEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public GrantRecordEntity getByIdempotent(String grantSource, String sourceId, long prizeId) {
        Long id = keys.get(key(grantSource, sourceId, prizeId));
        return id == null ? null : rows.get(id);
    }

    @Override
    public int insert(GrantRecordEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.getAndIncrement());
        }
        String uk = key(entity.getGrantSource(), entity.getSourceId(), entity.getPrizeId());
        Long previous = keys.putIfAbsent(uk, entity.getId());
        if (previous != null) {
            throw new DuplicateKeyException("uk_idempotent");
        }
        rows.put(entity.getId(), entity);
        persistedStatus.put(entity.getId(), entity.getStatus());
        return 1;
    }

    @Override
    public int update(GrantRecordEntity entity) {
        if (entity.getId() == null || !rows.containsKey(entity.getId())) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        persistedStatus.put(entity.getId(), entity.getStatus());
        return 1;
    }

    @Override
    public int updateIfStatus(GrantRecordEntity entity, String expectedStatus) {
        if (entity.getId() == null || !rows.containsKey(entity.getId())) {
            return 0;
        }
        boolean replaced = persistedStatus.replace(entity.getId(), expectedStatus, entity.getStatus());
        if (!replaced) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int updateFulfillmentRef(long id, String fulfillmentRef) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null || entity.getFulfillmentRef() != null) {
            return 0;
        }
        entity.setFulfillmentRef(fulfillmentRef);
        return 1;
    }

    @Override
    public long countActive(long userId, long prizeId, LocalDateTime from) {
        return rows.values().stream()
                .filter(row -> userId == row.getUserId()
                        && prizeId == row.getPrizeId()
                        && GrantRecordStatuses.LIMIT_COUNT.contains(row.getStatus())
                        && (from == null
                                || row.getCreatedAt() == null
                                || !row.getCreatedAt().isBefore(from)))
                .count();
    }

    @Override
    public int markPermanentFailed(long id) {
        GrantRecordEntity entity = rows.get(id);
        if (entity == null) {
            return 0;
        }
        entity.setStatus(GrantRecordStatuses.PERMANENT_FAILED);
        return 1;
    }

    @Override
    public List<GrantRecordEntity> listDueRetry(LocalDateTime now, int limit) {
        List<GrantRecordEntity> due = new ArrayList<>();
        for (GrantRecordEntity row : rows.values()) {
            if (!GrantRecordStatuses.RETRY_PENDING.equals(row.getStatus())) {
                continue;
            }
            if (row.getNextRetryAt() == null || row.getNextRetryAt().isAfter(now)) {
                continue;
            }
            if (row.getExpireAt() != null && !row.getExpireAt().isAfter(now)) {
                continue;
            }
            due.add(row);
        }
        due.sort(Comparator.comparing(GrantRecordEntity::getNextRetryAt).thenComparing(GrantRecordEntity::getId));
        return due.subList(0, Math.min(limit, due.size()));
    }

    public List<GrantRecordEntity> all() {
        return List.copyOf(rows.values());
    }

    private static String key(String grantSource, String sourceId, Long prizeId) {
        return grantSource + '\0' + sourceId + '\0' + prizeId;
    }
}
