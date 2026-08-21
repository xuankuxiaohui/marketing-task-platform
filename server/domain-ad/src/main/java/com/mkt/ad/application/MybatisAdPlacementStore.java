package com.mkt.ad.application;

import com.mkt.ad.entity.AdPositionMaterialEntity;
import com.mkt.ad.mapper.AdPositionMaterialMapper;
import com.mkt.ad.support.AdDuplicateKeys;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

@Repository
public class MybatisAdPlacementStore implements AdPlacementStore {

    private final AdPositionMaterialMapper mapper;

    public MybatisAdPlacementStore(AdPositionMaterialMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<AdPositionMaterialEntity> listByPositionId(long positionId) {
        return mapper.selectByPositionId(positionId);
    }

    @Override
    public AdPositionMaterialEntity getByPositionAndMaterial(long positionId, long materialId) {
        return mapper.selectByPositionAndMaterial(positionId, materialId);
    }

    @Override
    public int insert(AdPositionMaterialEntity entity) {
        try {
            return mapper.insert(entity);
        } catch (DuplicateKeyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (AdDuplicateKeys.duplicate(ex)) {
                throw AdDuplicateKeys.wrap("uk_position_material", ex);
            }
            throw ex;
        }
    }

    @Override
    public int update(AdPositionMaterialEntity entity) {
        return mapper.updateById(entity);
    }

    @Override
    public int delete(long positionId, long materialId) {
        return mapper.deleteByPositionAndMaterial(positionId, materialId);
    }

    @Override
    public int countByMaterialId(long materialId) {
        return mapper.countByMaterialId(materialId);
    }
}
