package com.mkt.tracking.application;

import com.mkt.tracking.domain.MetadataStatus;
import com.mkt.tracking.domain.MetadataStatuses;
import com.mkt.tracking.entity.EvtEventMetadataEntity;
import com.mkt.tracking.mapper.EvtEventMetadataMapper;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisEventMetadataStore implements EventMetadataStore {

    private final EvtEventMetadataMapper mapper;

    public MybatisEventMetadataStore(EvtEventMetadataMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public MetadataStatus statusOf(String eventCode) {
        EvtEventMetadataEntity row = mapper.selectByEventCode(eventCode);
        if (row == null) {
            return MetadataStatus.MISSING;
        }
        return MetadataStatuses.of(row.getStatus());
    }

    @Override
    public EvtEventMetadataEntity getById(long id) {
        return mapper.selectById(id);
    }

    @Override
    public EvtEventMetadataEntity getByEventCode(String eventCode) {
        return mapper.selectByEventCode(eventCode);
    }

    @Override
    public int insert(EvtEventMetadataEntity entity) {
        return mapper.insert(entity);
    }

    @Override
    public int update(EvtEventMetadataEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int deleteById(long id) {
        return mapper.deleteById(id);
    }

    @Override
    public long countByQuery(String eventCode, String status) {
        return mapper.selectCountByQuery(eventCode, status);
    }

    @Override
    public List<EvtEventMetadataEntity> listByQuery(String eventCode, String status, long offset, int limit) {
        return mapper.selectByQuery(eventCode, status, offset, limit);
    }
}
