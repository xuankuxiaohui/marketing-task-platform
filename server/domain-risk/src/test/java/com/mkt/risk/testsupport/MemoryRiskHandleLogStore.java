package com.mkt.risk.testsupport;

import com.mkt.risk.application.RiskHandleLogStore;
import com.mkt.risk.entity.RiskHandleLogEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryRiskHandleLogStore implements RiskHandleLogStore {

    private final AtomicLong seq = new AtomicLong();
    private final ConcurrentHashMap<Long, RiskHandleLogEntity> rows = new ConcurrentHashMap<>();

    @Override
    public int insert(RiskHandleLogEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.incrementAndGet());
        }
        rows.put(entity.getId(), copy(entity));
        return 1;
    }

    @Override
    public List<RiskHandleLogEntity> listAll() {
        List<RiskHandleLogEntity> result = new ArrayList<>();
        rows.values().stream()
                .sorted(Comparator.comparing(RiskHandleLogEntity::getId))
                .forEach(e -> result.add(copy(e)));
        return result;
    }

    private RiskHandleLogEntity copy(RiskHandleLogEntity src) {
        RiskHandleLogEntity e = new RiskHandleLogEntity();
        e.setId(src.getId());
        e.setHitLogId(src.getHitLogId());
        e.setUserId(src.getUserId());
        e.setAction(src.getAction());
        e.setToWhitelist(src.getToWhitelist());
        e.setOperatorId(src.getOperatorId());
        e.setReason(src.getReason());
        e.setCreatedAt(src.getCreatedAt());
        return e;
    }
}
