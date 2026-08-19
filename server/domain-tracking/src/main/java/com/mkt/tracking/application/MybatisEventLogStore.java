package com.mkt.tracking.application;

import com.mkt.tracking.entity.EvtEventLogEntity;
import com.mkt.tracking.mapper.EvtEventLogMapper;
import com.mkt.tracking.query.EventLogQuery;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisEventLogStore implements EventLogStore {

    private final EvtEventLogMapper mapper;

    public MybatisEventLogStore(EvtEventLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(EvtEventLogEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public long countByQuery(EventLogQuery query) {
        return mapper.selectCountByQuery(
                query.eventCode(),
                query.userId(),
                query.source(),
                query.deviceId(),
                query.from(),
                query.to(),
                query.sampleRatioPercent());
    }

    @Override
    public List<EvtEventLogEntity> listByQuery(EventLogQuery query) {
        return mapper.selectByQuery(
                query.eventCode(),
                query.userId(),
                query.source(),
                query.deviceId(),
                query.from(),
                query.to(),
                query.sampleRatioPercent(),
                query.offset(),
                query.limit());
    }
}
