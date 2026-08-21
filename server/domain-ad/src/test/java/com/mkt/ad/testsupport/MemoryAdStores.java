package com.mkt.ad.testsupport;

import com.mkt.ad.application.AdMaterialStore;
import com.mkt.ad.application.AdPlacementStore;
import com.mkt.ad.application.AdPositionStore;
import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.dao.DuplicateKeyException;

public final class MemoryAdStores {

    public final MemoryPositions positions = new MemoryPositions();
    public final MemoryMaterials materials = new MemoryMaterials();
    public final MemoryPlacements placements = new MemoryPlacements();

    public static final class MemoryPositions implements AdPositionStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<Long, AdPositionEntity> byId = new ConcurrentHashMap<>();

        @Override
        public AdPositionEntity getById(long id) {
            return copy(byId.get(id));
        }

        @Override
        public AdPositionEntity getByCode(String code) {
            return byId.values().stream()
                    .filter(row -> code.equals(row.getCode()) && (row.getDeleted() == null || row.getDeleted() == 0))
                    .findFirst()
                    .map(MemoryPositions::copy)
                    .orElse(null);
        }

        @Override
        public int insert(AdPositionEntity entity) {
            if (getByCode(entity.getCode()) != null) {
                throw new DuplicateKeyException("uk_code");
            }
            entity.setId(seq.getAndIncrement());
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public int update(AdPositionEntity entity) {
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public long countByQuery(String code, String form, String status) {
            return listByQuery(code, form, status, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public List<AdPositionEntity> listByQuery(String code, String form, String status, long offset, int limit) {
            return byId.values().stream()
                    .filter(row -> row.getDeleted() == null || row.getDeleted() == 0)
                    .filter(row -> code == null || code.isBlank() || code.equals(row.getCode()))
                    .filter(row -> form == null || form.isBlank() || form.equals(row.getForm()))
                    .filter(row -> status == null || status.isBlank() || status.equals(row.getStatus()))
                    .sorted(Comparator.comparing(AdPositionEntity::getId).reversed())
                    .skip(offset)
                    .limit(limit)
                    .map(MemoryPositions::copy)
                    .toList();
        }

        private static AdPositionEntity copy(AdPositionEntity src) {
            if (src == null) {
                return null;
            }
            AdPositionEntity out = new AdPositionEntity();
            out.setId(src.getId());
            out.setCode(src.getCode());
            out.setName(src.getName());
            out.setForm(src.getForm());
            out.setPlatforms(src.getPlatforms());
            out.setStatus(src.getStatus());
            out.setDeleted(src.getDeleted());
            out.setCreatedBy(src.getCreatedBy());
            out.setCreatedAt(src.getCreatedAt());
            out.setUpdatedAt(src.getUpdatedAt());
            return out;
        }
    }

    public static final class MemoryMaterials implements AdMaterialStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<Long, AdMaterialEntity> byId = new ConcurrentHashMap<>();

        @Override
        public AdMaterialEntity getById(long id) {
            return copy(byId.get(id));
        }

        @Override
        public int insert(AdMaterialEntity entity) {
            entity.setId(seq.getAndIncrement());
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public int update(AdMaterialEntity entity) {
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public long countByQuery(String title, String status) {
            return listByQuery(title, status, 0, Integer.MAX_VALUE).size();
        }

        @Override
        public List<AdMaterialEntity> listByQuery(String title, String status, long offset, int limit) {
            return byId.values().stream()
                    .filter(row -> row.getDeleted() == null || row.getDeleted() == 0)
                    .filter(row -> title == null
                            || title.isBlank()
                            || (row.getTitle() != null && row.getTitle().contains(title)))
                    .filter(row -> status == null || status.isBlank() || status.equals(row.getStatus()))
                    .sorted(Comparator.comparing(AdMaterialEntity::getId).reversed())
                    .skip(offset)
                    .limit(limit)
                    .map(MemoryMaterials::copy)
                    .toList();
        }

        @Override
        public List<AdMaterialEntity> listByIds(List<Long> ids) {
            if (ids == null || ids.isEmpty()) {
                return List.of();
            }
            return ids.stream().map(this::getById).filter(row -> row != null && !row.deletedFlag()).toList();
        }

        private static AdMaterialEntity copy(AdMaterialEntity src) {
            if (src == null) {
                return null;
            }
            AdMaterialEntity out = new AdMaterialEntity();
            out.setId(src.getId());
            out.setTitle(src.getTitle());
            out.setSubtitle(src.getSubtitle());
            out.setImageUrl(src.getImageUrl());
            out.setJumpType(src.getJumpType());
            out.setJumpParams(src.getJumpParams());
            out.setWeight(src.getWeight());
            out.setStartTime(src.getStartTime());
            out.setEndTime(src.getEndTime());
            out.setStatus(src.getStatus());
            out.setDeleted(src.getDeleted());
            out.setCreatedBy(src.getCreatedBy());
            out.setCreatedAt(src.getCreatedAt());
            out.setUpdatedAt(src.getUpdatedAt());
            return out;
        }
    }

    public static final class MemoryPlacements implements AdPlacementStore {
        private final AtomicLong seq = new AtomicLong(1);
        private final ConcurrentHashMap<Long, AdPositionMaterialEntity> byId = new ConcurrentHashMap<>();

        @Override
        public List<AdPositionMaterialEntity> listByPositionId(long positionId) {
            return byId.values().stream()
                    .filter(row -> row.getPositionId() != null && row.getPositionId() == positionId)
                    .sorted(Comparator.comparingInt(AdPositionMaterialEntity::weightOrZero)
                            .reversed()
                            .thenComparingLong(AdPositionMaterialEntity::getMaterialId))
                    .map(MemoryPlacements::copy)
                    .toList();
        }

        @Override
        public AdPositionMaterialEntity getByPositionAndMaterial(long positionId, long materialId) {
            return byId.values().stream()
                    .filter(row -> row.getPositionId() != null
                            && row.getPositionId() == positionId
                            && row.getMaterialId() != null
                            && row.getMaterialId() == materialId)
                    .findFirst()
                    .map(MemoryPlacements::copy)
                    .orElse(null);
        }

        @Override
        public int insert(AdPositionMaterialEntity entity) {
            if (getByPositionAndMaterial(entity.getPositionId(), entity.getMaterialId()) != null) {
                throw new DuplicateKeyException("uk_position_material");
            }
            entity.setId(seq.getAndIncrement());
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public int update(AdPositionMaterialEntity entity) {
            byId.put(entity.getId(), copy(entity));
            return 1;
        }

        @Override
        public int delete(long positionId, long materialId) {
            Long found = null;
            for (AdPositionMaterialEntity row : byId.values()) {
                if (row.getPositionId() != null
                        && row.getPositionId() == positionId
                        && row.getMaterialId() != null
                        && row.getMaterialId() == materialId) {
                    found = row.getId();
                    break;
                }
            }
            if (found == null) {
                return 0;
            }
            byId.remove(found);
            return 1;
        }

        @Override
        public int countByMaterialId(long materialId) {
            return (int) byId.values().stream()
                    .filter(row -> row.getMaterialId() != null && row.getMaterialId() == materialId)
                    .count();
        }

        private static AdPositionMaterialEntity copy(AdPositionMaterialEntity src) {
            if (src == null) {
                return null;
            }
            AdPositionMaterialEntity out = new AdPositionMaterialEntity();
            out.setId(src.getId());
            out.setPositionId(src.getPositionId());
            out.setMaterialId(src.getMaterialId());
            out.setWeight(src.getWeight());
            out.setStartTime(src.getStartTime());
            out.setEndTime(src.getEndTime());
            out.setPlatforms(src.getPlatforms());
            out.setGrayType(src.getGrayType());
            out.setGrayRatio(src.getGrayRatio());
            out.setCrowdId(src.getCrowdId());
            out.setStatus(src.getStatus());
            out.setCreatedAt(src.getCreatedAt());
            out.setUpdatedAt(src.getUpdatedAt());
            return out;
        }
    }
}
