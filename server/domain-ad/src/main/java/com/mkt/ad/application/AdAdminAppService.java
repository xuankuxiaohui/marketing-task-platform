package com.mkt.ad.application;

import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.convert.AdFieldCodec;
import com.mkt.ad.domain.AdCodes;
import com.mkt.ad.domain.AdDates;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.domain.AdGrayTypes;
import com.mkt.ad.domain.AdJumpSchemas;
import com.mkt.ad.domain.AdPlatforms;
import com.mkt.ad.domain.AdStatuses;
import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import com.mkt.ad.query.AdMaterialQuery;
import com.mkt.ad.query.AdPositionQuery;
import com.mkt.ad.response.AdMaterialView;
import com.mkt.ad.response.AdPlacementView;
import com.mkt.ad.response.AdPositionView;
import com.mkt.ad.response.AdSaveResponse;
import com.mkt.ad.response.OkResponse;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.ad.support.AdOperator;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.PageQuery;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdAdminAppService {

    private final AdPositionStore positions;
    private final AdMaterialStore materials;
    private final AdPlacementStore placements;
    private final PlatformCache cache;

    public AdAdminAppService(
            AdPositionStore positions,
            AdMaterialStore materials,
            AdPlacementStore placements,
            PlatformCache cache) {
        this.positions = positions;
        this.materials = materials;
        this.placements = placements;
        this.cache = cache;
    }

    public PageData<AdPositionView> pagePositions(AdPositionQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = positions.countByQuery(query.code(), query.form(), query.status());
        List<AdPositionView> rows = positions
                .listByQuery(query.code(), query.form(), query.status(), page.offset(), page.pageSize())
                .stream()
                .map(this::toPositionView)
                .toList();
        return new PageData<>(total, rows);
    }

    public AdPositionView getPosition(long id) {
        return toPositionView(requirePosition(id));
    }

    @Transactional
    public AdSaveResponse savePosition(AdPositionSaveCommand command) {
        String form = AdForms.require(command.form());
        if (!AdForms.valid(form)) {
            throw new BusinessException(AdErrorCodes.POSITION_FORM_INVALID);
        }
        if (!AdCodes.valid(command.code())) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "广告位编码格式不正确");
        }
        String status = AdStatuses.normalize(command.status());
        if (!AdStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "状态无效");
        }
        List<String> platforms = AdPlatforms.normalize(command.platforms());
        if (command.id() == null) {
            AdPositionEntity entity = new AdPositionEntity();
            entity.setCode(command.code());
            entity.setName(command.name());
            entity.setForm(form);
            entity.setPlatforms(AdFieldCodec.json(platforms));
            entity.setStatus(status);
            entity.setDeleted(0);
            entity.setCreatedBy(AdOperator.requireUserId());
            try {
                positions.insert(entity);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(AdErrorCodes.POSITION_CODE_DUPLICATE);
            }
            evict(entity.getCode());
            return new AdSaveResponse(entity.getId());
        }
        AdPositionEntity live = requirePosition(command.id());
        if (!command.code().equals(live.getCode()) && positions.getByCode(command.code()) != null) {
            throw new BusinessException(AdErrorCodes.POSITION_CODE_DUPLICATE);
        }
        String previous = live.getCode();
        live.setCode(command.code());
        live.setName(command.name());
        live.setForm(form);
        live.setPlatforms(AdFieldCodec.json(platforms));
        live.setStatus(status);
        try {
            positions.update(live);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(AdErrorCodes.POSITION_CODE_DUPLICATE);
        }
        evict(previous);
        evict(live.getCode());
        return new AdSaveResponse(live.getId());
    }

    @Transactional
    public OkResponse deletePosition(long id) {
        AdPositionEntity live = requirePosition(id);
        live.setDeleted(1);
        positions.update(live);
        evict(live.getCode());
        return OkResponse.yes();
    }

    public PageData<AdMaterialView> pageMaterials(AdMaterialQuery query) {
        PageQuery page = query.page() == null ? PageQuery.of(null, null) : query.page();
        long total = materials.countByQuery(query.title(), query.status());
        List<AdMaterialView> rows = materials
                .listByQuery(query.title(), query.status(), page.offset(), page.pageSize())
                .stream()
                .map(this::toMaterialView)
                .toList();
        return new PageData<>(total, rows);
    }

    public AdMaterialView getMaterial(long id) {
        return toMaterialView(requireMaterial(id));
    }

    @Transactional
    public AdSaveResponse saveMaterial(AdMaterialSaveCommand command) {
        validateMaterial(command);
        if (command.id() == null) {
            AdMaterialEntity entity = new AdMaterialEntity();
            applyMaterial(entity, command);
            entity.setDeleted(0);
            entity.setCreatedBy(AdOperator.requireUserId());
            materials.insert(entity);
            return new AdSaveResponse(entity.getId());
        }
        AdMaterialEntity live = requireMaterial(command.id());
        applyMaterial(live, command);
        materials.update(live);
        evictPlacementsOf(live.getId());
        return new AdSaveResponse(live.getId());
    }

    @Transactional
    public OkResponse deleteMaterial(long id) {
        AdMaterialEntity live = requireMaterial(id);
        live.setDeleted(1);
        materials.update(live);
        evictPlacementsOf(id);
        return OkResponse.yes();
    }

    @Transactional
    public AdSaveResponse bind(long positionId, AdPlacementSaveCommand command) {
        AdPositionEntity position = requirePosition(positionId);
        AdMaterialEntity material = requireMaterial(command.materialId());
        validatePlacement(command);
        AdPositionMaterialEntity existing =
                placements.getByPositionAndMaterial(positionId, command.materialId());
        if (existing == null) {
            AdPositionMaterialEntity entity = new AdPositionMaterialEntity();
            entity.setPositionId(positionId);
            entity.setMaterialId(command.materialId());
            applyPlacement(entity, command, material.weightOrZero());
            try {
                placements.insert(entity);
            } catch (DuplicateKeyException ex) {
                throw new BusinessException(AdErrorCodes.PLACEMENT_DUPLICATE);
            }
            evict(position.getCode());
            return new AdSaveResponse(entity.getId());
        }
        applyPlacement(existing, command, material.weightOrZero());
        placements.update(existing);
        evict(position.getCode());
        return new AdSaveResponse(existing.getId());
    }

    @Transactional
    public OkResponse unbind(long positionId, long materialId) {
        AdPositionEntity position = requirePosition(positionId);
        int deleted = placements.delete(positionId, materialId);
        if (deleted == 0) {
            throw new BusinessException(AdErrorCodes.PLACEMENT_NOT_FOUND);
        }
        evict(position.getCode());
        return OkResponse.yes();
    }

    private void validateMaterial(AdMaterialSaveCommand command) {
        if (command.weight() == null || command.weight() < 1 || command.weight() > 999) {
            throw new BusinessException(AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        }
        if (command.startTime() == null
                || command.endTime() == null
                || !command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(AdErrorCodes.TIME_WINDOW_INVALID);
        }
        String jump = AdJumpSchemas.normalize(command.jumpType());
        if (!AdJumpSchemas.validType(jump) || !AdJumpSchemas.validParams(jump, command.jumpParams())) {
            throw new BusinessException(AdErrorCodes.MATERIAL_JUMP_INVALID);
        }
        String image = command.imageUrl() == null ? "" : command.imageUrl().trim();
        if (!image.toLowerCase(Locale.ROOT).startsWith("https://") || image.length() > 512) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "图片须为 https 外链");
        }
        String status = AdStatuses.normalize(command.status());
        if (!AdStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "状态无效");
        }
    }

    private void applyMaterial(AdMaterialEntity entity, AdMaterialSaveCommand command) {
        entity.setTitle(command.title());
        entity.setSubtitle(command.subtitle());
        entity.setImageUrl(command.imageUrl().trim());
        entity.setJumpType(AdJumpSchemas.normalize(command.jumpType()));
        entity.setJumpParams(AdFieldCodec.json(command.jumpParams()));
        entity.setWeight(command.weight());
        entity.setStartTime(AdDates.toUtc(command.startTime()));
        entity.setEndTime(AdDates.toUtc(command.endTime()));
        entity.setStatus(AdStatuses.normalize(command.status()));
    }

    private void validatePlacement(AdPlacementSaveCommand command) {
        if (command.weight() == null || command.weight() < 1 || command.weight() > 999) {
            throw new BusinessException(AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        }
        if (command.startTime() == null
                || command.endTime() == null
                || !command.startTime().isBefore(command.endTime())) {
            throw new BusinessException(AdErrorCodes.TIME_WINDOW_INVALID);
        }
        String gray = AdGrayTypes.normalize(command.grayType());
        if (!AdGrayTypes.valid(gray)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "灰度配置无效");
        }
        if (AdGrayTypes.RATIO.equals(gray)
                && (command.grayRatio() == null || command.grayRatio() < 0 || command.grayRatio() > 100)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "灰度比例无效");
        }
        String status = AdStatuses.normalize(command.status());
        if (!AdStatuses.valid(status)) {
            throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "状态无效");
        }
        if (command.platforms() != null) {
            for (String platform : command.platforms()) {
                if (platform != null
                        && !platform.isBlank()
                        && !AdPlatforms.valid(platform.trim().toUpperCase(Locale.ROOT))) {
                    throw new BusinessException(CommonErrorCodes.PARAM_INVALID, "端定向无效");
                }
            }
        }
    }

    private void applyPlacement(AdPositionMaterialEntity entity, AdPlacementSaveCommand command, int fallbackWeight) {
        entity.setWeight(command.weight() == null ? fallbackWeight : command.weight());
        entity.setStartTime(AdDates.toUtc(command.startTime()));
        entity.setEndTime(AdDates.toUtc(command.endTime()));
        List<String> platforms =
                command.platforms() == null || command.platforms().isEmpty()
                        ? List.of()
                        : AdPlatforms.normalize(command.platforms());
        entity.setPlatforms(platforms.isEmpty() ? null : AdFieldCodec.json(platforms));
        String gray = AdGrayTypes.normalize(command.grayType());
        entity.setGrayType(gray);
        entity.setGrayRatio(AdGrayTypes.RATIO.equals(gray) ? command.grayRatio() : null);
        entity.setCrowdId(command.crowdId());
        entity.setStatus(AdStatuses.normalize(command.status()));
    }

    private AdPositionEntity requirePosition(long id) {
        AdPositionEntity entity = positions.getById(id);
        if (entity == null || entity.deletedFlag()) {
            throw new BusinessException(AdErrorCodes.POSITION_NOT_FOUND);
        }
        return entity;
    }

    private AdMaterialEntity requireMaterial(long id) {
        AdMaterialEntity entity = materials.getById(id);
        if (entity == null || entity.deletedFlag()) {
            throw new BusinessException(AdErrorCodes.MATERIAL_NOT_FOUND);
        }
        return entity;
    }

    private AdPositionView toPositionView(AdPositionEntity entity) {
        List<AdPositionMaterialEntity> rels = placements.listByPositionId(entity.getId());
        List<Long> ids = rels.stream().map(AdPositionMaterialEntity::getMaterialId).toList();
        Map<Long, AdMaterialEntity> byId = new HashMap<>();
        for (AdMaterialEntity material : materials.listByIds(ids)) {
            byId.put(material.getId(), material);
        }
        List<AdPlacementView> views = new ArrayList<>();
        for (AdPositionMaterialEntity rel : rels) {
            AdMaterialEntity material = byId.get(rel.getMaterialId());
            views.add(toPlacementView(rel, material));
        }
        return new AdPositionView(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getForm(),
                AdFieldCodec.strings(entity.getPlatforms()),
                entity.getStatus(),
                views,
                overlapCount(views));
    }

    private AdPlacementView toPlacementView(AdPositionMaterialEntity rel, AdMaterialEntity material) {
        return new AdPlacementView(
                rel.getId(),
                rel.getPositionId(),
                rel.getMaterialId(),
                rel.weightOrZero(),
                AdDates.toInstant(rel.getStartTime()),
                AdDates.toInstant(rel.getEndTime()),
                AdFieldCodec.strings(rel.getPlatforms()),
                rel.getGrayType(),
                rel.getGrayRatio(),
                rel.getCrowdId(),
                rel.getStatus(),
                material == null ? null : toMaterialView(material));
    }

    private AdMaterialView toMaterialView(AdMaterialEntity entity) {
        return new AdMaterialView(
                entity.getId(),
                entity.getTitle(),
                entity.getSubtitle(),
                entity.getImageUrl(),
                entity.getJumpType(),
                AdFieldCodec.map(entity.getJumpParams()),
                entity.weightOrZero(),
                AdDates.toInstant(entity.getStartTime()),
                AdDates.toInstant(entity.getEndTime()),
                entity.getStatus());
    }

    static int overlapCount(List<AdPlacementView> views) {
        int count = 0;
        for (int i = 0; i < views.size(); i++) {
            for (int j = i + 1; j < views.size(); j++) {
                AdPlacementView a = views.get(i);
                AdPlacementView b = views.get(j);
                if (AdDates.overlap(a.startTime(), a.endTime(), b.startTime(), b.endTime())) {
                    count++;
                }
            }
        }
        return count;
    }

    private void evictPlacementsOf(long materialId) {
        cache.evictNamespace(CacheNamespace.AD_POSITION);
    }

    private void evict(String code) {
        if (code != null) {
            cache.evictAfterCommit(CacheNamespace.AD_POSITION, code);
        }
    }
}
