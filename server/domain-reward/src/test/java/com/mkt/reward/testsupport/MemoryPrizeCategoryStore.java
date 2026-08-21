package com.mkt.reward.testsupport;

import com.mkt.reward.application.PrizeCategoryStore;
import com.mkt.reward.entity.PrizeCategoryEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryPrizeCategoryStore implements PrizeCategoryStore {

    private final ConcurrentHashMap<String, PrizeCategoryEntity> rows = new ConcurrentHashMap<>();

    @Override
    public PrizeCategoryEntity getByCode(String code) {
        return rows.get(code);
    }

    @Override
    public int insert(PrizeCategoryEntity entity) {
        rows.put(entity.getCode(), entity);
        return 1;
    }

    @Override
    public int update(PrizeCategoryEntity entity) {
        rows.put(entity.getCode(), entity);
        return 1;
    }

    @Override
    public int deleteByCode(String code) {
        return rows.remove(code) == null ? 0 : 1;
    }

    @Override
    public long countAll() {
        return rows.size();
    }

    @Override
    public List<PrizeCategoryEntity> list(long offset, int limit) {
        List<PrizeCategoryEntity> all = new ArrayList<>(rows.values());
        all.sort(Comparator.comparing(PrizeCategoryEntity::getCode));
        int from = (int) Math.min(offset, all.size());
        int to = Math.min(from + limit, all.size());
        return all.subList(from, to);
    }

    public void seed(PrizeCategoryEntity entity) {
        rows.put(entity.getCode(), entity);
    }
}
