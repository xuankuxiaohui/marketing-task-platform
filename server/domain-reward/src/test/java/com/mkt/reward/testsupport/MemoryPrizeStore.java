package com.mkt.reward.testsupport;

import com.mkt.reward.application.PrizeStore;
import com.mkt.reward.domain.PrizeStatuses;
import com.mkt.reward.entity.PrizeEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryPrizeStore implements PrizeStore {

    private final ConcurrentHashMap<Long, PrizeEntity> rows = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public PrizeEntity getById(long id) {
        PrizeEntity entity = rows.get(id);
        if (entity == null || entity.deletedFlag()) {
            return null;
        }
        return entity;
    }

    @Override
    public PrizeEntity getByIdIncludingDeleted(long id) {
        return rows.get(id);
    }

    @Override
    public PrizeEntity getByCode(String code) {
        return rows.values().stream()
                .filter(row -> !row.deletedFlag() && code.equals(row.getCode()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public int insert(PrizeEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.getAndIncrement());
        }
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public int update(PrizeEntity entity) {
        rows.put(entity.getId(), entity);
        return 1;
    }

    @Override
    public long count(String code, String name, String categoryCode, String status, Long groupId) {
        return list(code, name, categoryCode, status, groupId, 0, Integer.MAX_VALUE).size();
    }

    @Override
    public List<PrizeEntity> list(
            String code,
            String name,
            String categoryCode,
            String status,
            Long groupId,
            long offset,
            int limit) {
        List<PrizeEntity> all = new ArrayList<>();
        for (PrizeEntity row : rows.values()) {
            if (row.deletedFlag()) {
                continue;
            }
            if (code != null && !code.equals(row.getCode())) {
                continue;
            }
            if (name != null && (row.getName() == null || !row.getName().contains(name))) {
                continue;
            }
            if (categoryCode != null && !categoryCode.equals(row.getCategoryCode())) {
                continue;
            }
            if (status != null && !status.equals(row.getStatus())) {
                continue;
            }
            if (groupId != null && !groupId.equals(row.getGroupId())) {
                continue;
            }
            all.add(row);
        }
        all.sort(Comparator.comparing(PrizeEntity::getId).reversed());
        int from = (int) Math.min(offset, all.size());
        int to = Math.min(from + limit, all.size());
        return all.subList(from, to);
    }

    @Override
    public long countByCategory(String categoryCode) {
        return rows.values().stream()
                .filter(row -> !row.deletedFlag() && categoryCode.equals(row.getCategoryCode()))
                .count();
    }

    @Override
    public synchronized int deductOne(long id) {
        PrizeEntity entity = getById(id);
        if (entity == null
                || !PrizeStatuses.ENABLED.equals(entity.getStatus())
                || entity.getRemainingStock() == null
                || entity.getRemainingStock() < 1) {
            return 0;
        }
        entity.setRemainingStock(entity.getRemainingStock() - 1);
        return 1;
    }

    @Override
    public synchronized int restoreOne(long id) {
        PrizeEntity entity = getById(id);
        if (entity == null
                || entity.getRemainingStock() == null
                || entity.getTotalStock() == null
                || entity.getRemainingStock() + 1 > entity.getTotalStock()) {
            return 0;
        }
        entity.setRemainingStock(entity.getRemainingStock() + 1);
        return 1;
    }

    @Override
    public synchronized int replenish(long id, int amount) {
        PrizeEntity entity = getById(id);
        if (entity == null) {
            return 0;
        }
        entity.setRemainingStock(entity.getRemainingStock() + amount);
        entity.setTotalStock(entity.getTotalStock() + amount);
        return 1;
    }
}
