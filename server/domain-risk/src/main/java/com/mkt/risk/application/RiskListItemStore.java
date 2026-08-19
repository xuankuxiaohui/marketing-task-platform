package com.mkt.risk.application;

import com.mkt.risk.entity.RiskListItemEntity;
import java.time.LocalDateTime;
import java.util.List;

public interface RiskListItemStore {

    RiskListItemEntity getByUk(String dimension, String listType, String listValue);

    List<RiskListItemEntity> listByUks(List<RiskListUk> uks);

    int insert(RiskListItemEntity entity);

    RiskListItemEntity getById(long id);

    int deleteById(long id);

    long countByQuery(String dimension, String listType, String listValue, LocalDateTime from, LocalDateTime to);

    List<RiskListItemEntity> listByQuery(
            String dimension,
            String listType,
            String listValue,
            LocalDateTime from,
            LocalDateTime to,
            long offset,
            int limit);
}
