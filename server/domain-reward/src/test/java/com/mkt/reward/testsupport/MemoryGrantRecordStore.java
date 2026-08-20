package com.mkt.reward.testsupport;

import com.mkt.reward.application.GrantRecordStore;
import com.mkt.reward.domain.GrantRecordStatuses;
import com.mkt.reward.entity.GrantRecordEntity;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryGrantRecordStore implements GrantRecordStore {

    private final ConcurrentHashMap<Long, GrantRecordEntity> rows = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public GrantRecordEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public int insert(GrantRecordEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.getAndIncrement());
        }
        rows.put(entity.getId(), entity);
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
}
