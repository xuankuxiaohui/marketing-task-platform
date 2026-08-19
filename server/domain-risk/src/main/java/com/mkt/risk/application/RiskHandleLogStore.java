package com.mkt.risk.application;

import com.mkt.risk.entity.RiskHandleLogEntity;
import java.util.List;

public interface RiskHandleLogStore {

    int insert(RiskHandleLogEntity entity);

    List<RiskHandleLogEntity> listAll();
}
