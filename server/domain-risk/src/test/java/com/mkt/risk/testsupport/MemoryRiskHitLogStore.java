package com.mkt.risk.testsupport;

import com.mkt.risk.application.RiskHitLogStore;
import com.mkt.risk.entity.RiskHitLogEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class MemoryRiskHitLogStore implements RiskHitLogStore {

    private final AtomicLong seq = new AtomicLong();
    private final ConcurrentHashMap<Long, RiskHitLogEntity> rows = new ConcurrentHashMap<>();

    @Override
    public RiskHitLogEntity getById(long id) {
        RiskHitLogEntity found = rows.get(id);
        return found == null ? null : copy(found);
    }

    @Override
    public int insert(RiskHitLogEntity entity) {
        if (entity.getId() == null) {
            entity.setId(seq.incrementAndGet());
        }
        rows.put(entity.getId(), copy(entity));
        return 1;
    }

    @Override
    public long countByQuery(
            String ruleCode,
            String hitType,
            String dimensionValue,
            Long userId,
            String actionResult,
            LocalDateTime from,
            LocalDateTime to) {
        return filter(ruleCode, hitType, dimensionValue, userId, actionResult, from, to).size();
    }

    @Override
    public List<RiskHitLogEntity> listByQuery(
            String ruleCode,
            String hitType,
            String dimensionValue,
            Long userId,
            String actionResult,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit) {
        return filter(ruleCode, hitType, dimensionValue, userId, actionResult, from, to).stream()
                .sorted(Comparator.comparing(RiskHitLogEntity::getId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    private List<RiskHitLogEntity> filter(
            String ruleCode,
            String hitType,
            String dimensionValue,
            Long userId,
            String actionResult,
            LocalDateTime from,
            LocalDateTime to) {
        List<RiskHitLogEntity> result = new ArrayList<>();
        for (RiskHitLogEntity e : rows.values()) {
            if (ruleCode != null && !ruleCode.isEmpty() && !ruleCode.equals(e.getRuleCode())) {
                continue;
            }
            if (hitType != null && !hitType.isEmpty() && !hitType.equals(e.getHitType())) {
                continue;
            }
            if (dimensionValue != null
                    && !dimensionValue.isEmpty()
                    && !dimensionValue.equals(e.getDimensionValue())) {
                continue;
            }
            if (userId != null && !userId.equals(e.getUserId())) {
                continue;
            }
            if (actionResult != null && !actionResult.isEmpty() && !actionResult.equals(e.getActionResult())) {
                continue;
            }
            if (from != null && e.getCreatedAt() != null && e.getCreatedAt().isBefore(from)) {
                continue;
            }
            if (to != null && e.getCreatedAt() != null && e.getCreatedAt().isAfter(to)) {
                continue;
            }
            result.add(copy(e));
        }
        return result;
    }

    private RiskHitLogEntity copy(RiskHitLogEntity src) {
        RiskHitLogEntity e = new RiskHitLogEntity();
        e.setId(src.getId());
        e.setHitType(src.getHitType());
        e.setRuleCode(src.getRuleCode());
        e.setUserId(src.getUserId());
        e.setDimensionValue(src.getDimensionValue());
        e.setContext(src.getContext());
        e.setHitValue(src.getHitValue());
        e.setThreshold(src.getThreshold());
        e.setActionResult(src.getActionResult());
        e.setSimulated(src.getSimulated());
        e.setOccurredAt(src.getOccurredAt());
        e.setCreatedAt(src.getCreatedAt());
        return e;
    }
}
