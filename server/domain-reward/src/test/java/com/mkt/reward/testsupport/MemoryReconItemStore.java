package com.mkt.reward.testsupport;

import com.mkt.reward.application.ReconItemStore;
import com.mkt.reward.domain.ReconActions;
import com.mkt.reward.domain.ReconReviewStatuses;
import com.mkt.reward.entity.ReconItemEntity;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryReconItemStore implements ReconItemStore {

    private final ConcurrentHashMap<Long, ReconItemEntity> rows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> refs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> persistedAction = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> persistedReview = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public ReconItemEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public int insert(ReconItemEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.getAndIncrement());
        }
        if (entity.getFulfillmentRef() != null) {
            Long previous = refs.putIfAbsent(entity.getBatchId() + ":" + entity.getFulfillmentRef(), entity.getId());
            if (previous != null) {
                throw new DuplicateKeyException("uk_batch_ref");
            }
        }
        rows.put(entity.getId(), entity);
        persistedAction.put(entity.getId(), entity.getAction() == null ? ReconActions.NONE : entity.getAction());
        persistedReview.put(
                entity.getId(),
                entity.getReviewStatus() == null ? ReconReviewStatuses.NONE : entity.getReviewStatus());
        return 1;
    }

    @Override
    public int update(ReconItemEntity entity) {
        if (entity.getId() == null || !rows.containsKey(entity.getId())) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        persistedAction.put(entity.getId(), entity.getAction());
        persistedReview.put(entity.getId(), entity.getReviewStatus());
        return 1;
    }

    @Override
    public List<ReconItemEntity> list(long batchId, String result, String reviewStatus, long offset, int limit) {
        List<ReconItemEntity> filtered = rows.values().stream()
                .filter(row -> batchId == row.getBatchId())
                .filter(row -> result == null || result.isBlank() || result.equals(row.getResult()))
                .filter(row -> reviewStatus == null
                        || reviewStatus.isBlank()
                        || reviewStatus.equals(row.getReviewStatus()))
                .sorted(Comparator.comparing(ReconItemEntity::getId))
                .toList();
        int from = (int) Math.min(offset, filtered.size());
        int to = (int) Math.min(offset + limit, filtered.size());
        return filtered.subList(from, to);
    }

    @Override
    public long count(long batchId, String result, String reviewStatus) {
        return rows.values().stream()
                .filter(row -> batchId == row.getBatchId())
                .filter(row -> result == null || result.isBlank() || result.equals(row.getResult()))
                .filter(row -> reviewStatus == null
                        || reviewStatus.isBlank()
                        || reviewStatus.equals(row.getReviewStatus()))
                .count();
    }

    @Override
    public List<ReconItemEntity> listAll(long batchId) {
        return rows.values().stream()
                .filter(row -> batchId == row.getBatchId())
                .sorted(Comparator.comparing(ReconItemEntity::getId))
                .toList();
    }

    @Override
    public int casAction(ReconItemEntity entity) {
        boolean replaced = persistedAction.replace(entity.getId(), ReconActions.NONE, entity.getAction());
        if (!replaced) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int casReview(ReconItemEntity entity) {
        boolean replaced =
                persistedReview.replace(entity.getId(), ReconReviewStatuses.PENDING_REVIEW, entity.getReviewStatus());
        if (!replaced) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        return 1;
    }
}
