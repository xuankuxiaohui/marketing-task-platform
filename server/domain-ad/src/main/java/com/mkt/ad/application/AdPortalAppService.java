package com.mkt.ad.application;

import com.mkt.ad.convert.AdFieldCodec;
import com.mkt.ad.domain.AdDates;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.domain.AdFreqKeys;
import com.mkt.ad.domain.AdGrayTypes;
import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import com.mkt.ad.response.CachedAdSnapshot;
import com.mkt.ad.response.CachedAdSnapshot.CachedMaterial;
import com.mkt.ad.response.CachedAdSnapshot.CachedPlacement;
import com.mkt.ad.response.CachedAdSnapshot.CachedPosition;
import com.mkt.ad.response.OkResponse;
import com.mkt.ad.response.PortalAdPositionView;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.ad.support.AdSettings;
import com.mkt.contract.CrowdPort;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdPortalAppService {

    private final AdPositionStore positions;
    private final AdMaterialStore materials;
    private final AdPlacementStore placements;
    private final PlatformCache cache;
    private final AdFreqStore freq;
    private final AdSettings settings;
    private final Clock clock;
    private final AdPortalPull portalPull;

    @Autowired
    public AdPortalAppService(
            AdPositionStore positions,
            AdMaterialStore materials,
            AdPlacementStore placements,
            PlatformCache cache,
            AdFreqStore freq,
            AdSettings settings,
            CrowdPort crowds,
            Clock clock,
            AdPortalPull portalPull) {
        this.positions = positions;
        this.materials = materials;
        this.placements = placements;
        this.cache = cache;
        this.freq = freq;
        this.settings = settings;
        this.clock = clock;
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.portalPull = portalPull != null
                ? portalPull
                : new AdPortalPull(positions, materials, placements, cache, freq, settings, crowds, clock);
    }

    public AdPortalAppService(
            AdPositionStore positions,
            AdMaterialStore materials,
            AdPlacementStore placements,
            PlatformCache cache,
            AdFreqStore freq,
            AdSettings settings,
            CrowdPort crowds,
            Clock clock) {
        this(positions, materials, placements, cache, freq, settings, crowds, clock, null);
    }

    public PortalAdPositionView pull(String code, Long userId, String deviceId, String platformHeader) {
        return portalPull.pull(code, userId, deviceId, platformHeader);
    }

    public OkResponse dismiss(String positionCode, long materialId, Long userId, String deviceId) {
        CachedAdSnapshot snapshot = catalog(positionCode);
        if (snapshot == null || snapshot.position() == null) {
            throw new BusinessException(AdErrorCodes.POSITION_NOT_FOUND);
        }
        if (!AdForms.FLOAT.equals(snapshot.position().form())) {
            return OkResponse.yes();
        }
        boolean bound = snapshot.placements().stream().anyMatch(row -> row.materialId() == materialId);
        if (!bound) {
            throw new BusinessException(AdErrorCodes.MATERIAL_NOT_FOUND);
        }
        if (!AdFreqKeys.hasSubject(userId, deviceId)) {
            return OkResponse.yes();
        }
        String key = AdFreqKeys.daily(userId, deviceId, materialId, AdDates.dayKey(clock));
        freq.setTo(key, settings.dailyImpressionLimit(), AdDates.ttlToEndOfDay(clock));
        return OkResponse.yes();
    }

    private CachedAdSnapshot catalog(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return cache.get(CacheNamespace.AD_POSITION, code, CachedAdSnapshot.class, () -> load(code));
    }

    private CachedAdSnapshot load(String code) {
        AdPositionEntity position = positions.getByCode(code);
        if (position == null || position.deletedFlag()) {
            return null;
        }
        List<AdPositionMaterialEntity> rels = placements.listByPositionId(position.getId());
        List<Long> ids = rels.stream().map(AdPositionMaterialEntity::getMaterialId).toList();
        Map<Long, AdMaterialEntity> byId = new HashMap<>();
        for (AdMaterialEntity material : materials.listByIds(ids)) {
            byId.put(material.getId(), material);
        }
        List<CachedPlacement> cached = new ArrayList<>();
        for (AdPositionMaterialEntity rel : rels) {
            AdMaterialEntity material = byId.get(rel.getMaterialId());
            if (material == null || material.deletedFlag()) {
                continue;
            }
            cached.add(toCached(rel, material));
        }
        CachedPosition cachedPosition = new CachedPosition(
                position.getId(),
                position.getCode(),
                position.getName(),
                position.getForm(),
                AdFieldCodec.strings(position.getPlatforms()),
                position.getStatus());
        return new CachedAdSnapshot(cachedPosition, cached);
    }

    private static CachedPlacement toCached(AdPositionMaterialEntity rel, AdMaterialEntity material) {
        CachedMaterial cachedMaterial = new CachedMaterial(
                material.getId(),
                material.getTitle(),
                material.getSubtitle(),
                material.getImageUrl(),
                material.getJumpType(),
                AdFieldCodec.map(material.getJumpParams()),
                material.weightOrZero(),
                AdDates.toInstant(material.getStartTime()),
                AdDates.toInstant(material.getEndTime()),
                material.getStatus());
        return new CachedPlacement(
                rel.getId(),
                rel.getMaterialId(),
                rel.weightOrZero(),
                AdDates.toInstant(rel.getStartTime()),
                AdDates.toInstant(rel.getEndTime()),
                AdFieldCodec.strings(rel.getPlatforms()),
                rel.getGrayType() == null ? AdGrayTypes.NONE : rel.getGrayType(),
                rel.getGrayRatio(),
                rel.getCrowdId(),
                rel.getStatus(),
                cachedMaterial);
    }
}
