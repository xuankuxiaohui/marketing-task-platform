package com.mkt.reward.testsupport;

import com.mkt.reward.application.StockLogStore;
import com.mkt.reward.entity.StockLogEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryStockLogStore implements StockLogStore {

    private final CopyOnWriteArrayList<StockLogEntity> rows = new CopyOnWriteArrayList<>();
    private final AtomicLong seq = new AtomicLong(1);

    @Override
    public int insert(StockLogEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.getAndIncrement());
        }
        rows.add(entity);
        return 1;
    }

    @Override
    public long countByPrize(long prizeId) {
        return rows.stream().filter(row -> prizeId == row.getPrizeId()).count();
    }

    @Override
    public List<StockLogEntity> listByPrize(long prizeId, long offset, int limit) {
        List<StockLogEntity> all = new ArrayList<>();
        for (StockLogEntity row : rows) {
            if (prizeId == row.getPrizeId()) {
                all.add(row);
            }
        }
        all.sort(Comparator.comparing(StockLogEntity::getId).reversed());
        int from = (int) Math.min(offset, all.size());
        int to = Math.min(from + limit, all.size());
        return all.subList(from, to);
    }

    public List<StockLogEntity> all() {
        return List.copyOf(rows);
    }
}
