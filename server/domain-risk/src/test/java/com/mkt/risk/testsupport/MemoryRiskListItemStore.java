package com.mkt.risk.testsupport;

import com.mkt.risk.application.RiskListItemStore;
import com.mkt.risk.application.RiskListUk;
import com.mkt.risk.entity.RiskListItemEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryRiskListItemStore implements RiskListItemStore {

    private final AtomicLong seq = new AtomicLong();
    private final ConcurrentHashMap<Long, RiskListItemEntity> rows = new ConcurrentHashMap<>();

    @Override
    public RiskListItemEntity getByUk(String dimension, String listType, String listValue) {
        return rows.values().stream()
                .filter(e -> e.getDimension().equals(dimension)
                        && e.getListType().equals(listType)
                        && e.getListValue().equals(listValue))
                .findFirst()
                .map(this::copy)
                .orElse(null);
    }

    @Override
    public List<RiskListItemEntity> listByUks(List<RiskListUk> uks) {
        if (uks == null || uks.isEmpty()) {
            return List.of();
        }
        List<RiskListItemEntity> found = new ArrayList<>();
        for (RiskListUk uk : uks) {
            RiskListItemEntity row = getByUk(uk.dimension(), uk.listType(), uk.listValue());
            if (row != null) {
                found.add(row);
            }
        }
        return found;
    }

    @Override
    public int insert(RiskListItemEntity entity) {
        if (getByUk(entity.getDimension(), entity.getListType(), entity.getListValue()) != null) {
            throw new DuplicateKeyException("uk_dim_type_value");
        }
        if (entity.getId() == null) {
            entity.setId(seq.incrementAndGet());
        }
        rows.put(entity.getId(), copy(entity));
        return 1;
    }

    @Override
    public RiskListItemEntity getById(long id) {
        RiskListItemEntity found = rows.get(id);
        return found == null ? null : copy(found);
    }

    @Override
    public int deleteById(long id) {
        return rows.remove(id) == null ? 0 : 1;
    }

    @Override
    public long countByQuery(
            String dimension, String listType, String listValue, LocalDateTime from, LocalDateTime to) {
        return filter(dimension, listType, listValue, from, to).size();
    }

    @Override
    public List<RiskListItemEntity> listByQuery(
            String dimension,
            String listType,
            String listValue,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit) {
        return filter(dimension, listType, listValue, from, to).stream()
                .sorted(Comparator.comparing(RiskListItemEntity::getId).reversed())
                .skip(offset)
                .limit(limit)
                .toList();
    }

    public int size() {
        return rows.size();
    }

    private List<RiskListItemEntity> filter(
            String dimension, String listType, String listValue, LocalDateTime from, LocalDateTime to) {
        List<RiskListItemEntity> result = new ArrayList<>();
        for (RiskListItemEntity e : rows.values()) {
            if (dimension != null && !dimension.isEmpty() && !dimension.equals(e.getDimension())) {
                continue;
            }
            if (listType != null && !listType.isEmpty() && !listType.equals(e.getListType())) {
                continue;
            }
            if (listValue != null && !listValue.isEmpty() && !listValue.equals(e.getListValue())) {
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

    private RiskListItemEntity copy(RiskListItemEntity src) {
        RiskListItemEntity e = new RiskListItemEntity();
        e.setId(src.getId());
        e.setDimension(src.getDimension());
        e.setListType(src.getListType());
        e.setListValue(src.getListValue());
        e.setReason(src.getReason());
        e.setDenyLogin(src.getDenyLogin());
        e.setEffectiveAt(src.getEffectiveAt());
        e.setExpireAt(src.getExpireAt());
        e.setOperatorId(src.getOperatorId());
        e.setRemark(src.getRemark());
        e.setCreatedAt(src.getCreatedAt());
        return e;
    }
}
