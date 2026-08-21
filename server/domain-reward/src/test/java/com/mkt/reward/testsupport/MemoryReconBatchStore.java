package com.mkt.reward.testsupport;

import com.mkt.reward.application.ReconBatchStore;
import com.mkt.reward.entity.ReconBatchEntity;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryReconBatchStore implements ReconBatchStore {

    private final ConcurrentHashMap<Long, ReconBatchEntity> rows = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> keys = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public ReconBatchEntity getById(long id) {
        return rows.get(id);
    }

    @Override
    public ReconBatchEntity getByCategoryAndDay(String categoryCode, LocalDate billDate) {
        Long id = keys.get(key(categoryCode, billDate));
        return id == null ? null : rows.get(id);
    }

    @Override
    public int insert(ReconBatchEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.getAndIncrement());
        }
        String uk = key(entity.getCategoryCode(), entity.getBillDate());
        Long previous = keys.putIfAbsent(uk, entity.getId());
        if (previous != null) {
            throw new DuplicateKeyException("uk_cat_day");
        }
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int update(ReconBatchEntity entity) {
        if (entity.getId() == null || !rows.containsKey(entity.getId())) {
            return 0;
        }
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public List<ReconBatchEntity> list(
            String categoryCode, LocalDate billDate, String status, long offset, int limit) {
        List<ReconBatchEntity> filtered = rows.values().stream()
                .filter(row -> categoryCode == null
                        || categoryCode.isBlank()
                        || categoryCode.equals(row.getCategoryCode()))
                .filter(row -> billDate == null || billDate.equals(row.getBillDate()))
                .filter(row -> status == null || status.isBlank() || status.equals(row.getStatus()))
                .sorted(Comparator.comparing(ReconBatchEntity::getId).reversed())
                .toList();
        int from = (int) Math.min(offset, filtered.size());
        int to = (int) Math.min(offset + limit, filtered.size());
        return filtered.subList(from, to);
    }

    @Override
    public long count(String categoryCode, LocalDate billDate, String status) {
        return rows.values().stream()
                .filter(row -> categoryCode == null
                        || categoryCode.isBlank()
                        || categoryCode.equals(row.getCategoryCode()))
                .filter(row -> billDate == null || billDate.equals(row.getBillDate()))
                .filter(row -> status == null || status.isBlank() || status.equals(row.getStatus()))
                .count();
    }

    private static String key(String categoryCode, LocalDate billDate) {
        return categoryCode + '\0' + billDate;
    }
}
