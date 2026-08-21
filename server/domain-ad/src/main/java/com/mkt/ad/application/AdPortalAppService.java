package com.mkt.ad.application;

import com.mkt.ad.convert.AdFieldCodec;
import com.mkt.ad.domain.AdCandidate;
import com.mkt.ad.domain.AdDates;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.domain.AdFreqKeys;
import com.mkt.ad.domain.AdGrayBuckets;
import com.mkt.ad.domain.AdGrayTypes;
import com.mkt.ad.domain.AdPlatforms;
import com.mkt.ad.domain.AdSelector;
import com.mkt.ad.domain.AdStatuses;
import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import com.mkt.ad.response.CachedAdSnapshot;
import com.mkt.ad.response.CachedAdSnapshot.CachedMaterial;
import com.mkt.ad.response.CachedAdSnapshot.CachedPlacement;
import com.mkt.ad.response.CachedAdSnapshot.CachedPosition;
import com.mkt.ad.response.OkResponse;
import com.mkt.ad.response.PortalAdMaterialView;
import com.mkt.ad.response.PortalAdPositionView;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.ad.support.AdSettings;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.kernel.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public AdPortalAppService(
            AdPositionStore positions,
            AdMaterialStore materials,
            AdPlacementStore placements,
            PlatformCache cache,
            AdFreqStore freq,
            AdSettings settings,
            Clock clock) {
        this.positions = positions;
        this.materials = materials;
        this.placements = placements;
        this.cache = cache;
        this.freq = freq;
        this.settings = settings;
        this.clock = clock;
    }

    public PortalAdPositionView pull(String code, Long userId, String deviceId, String platformHeader) {
        CachedAdSnapshot snapshot = catalog(code);
        if (snapshot == null || snapshot.position() == null) {
            throw new BusinessException(AdErrorCodes.POSITION_NOT_FOUND);
        }
        CachedPosition position = snapshot.position();
        if (!AdStatuses.ENABLED.equals(position.status())) {
            return empty(position);
        }
        String platform = AdPlatforms.resolve(platformHeader);
        Instant now = clock.instant();
        List<AdCandidate> eligible = new ArrayList<>();
        for (CachedPlacement placement : snapshot.placements()) {
            if (visible(position, placement, userId, platform, now)) {
                eligible.add(toCandidate(placement));
            }
        }
        if (AdForms.POPUP.equals(position.form())
                && AdFreqKeys.hasSubject(userId, deviceId)
                && freq.hasCooldown(AdFreqKeys.popup(userId, deviceId))) {
            return empty(position);
        }
        List<AdCandidate> ranked = AdSelector.rank(eligible);
        List<AdCandidate> served = new ArrayList<>();
        int want = AdForms.CAROUSEL.equals(position.form()) ? settings.carouselMaxItems() : 1;
        Duration dayTtl = AdDates.ttlToEndOfDay(clock);
        String day = AdDates.dayKey(clock);
        for (AdCandidate candidate : ranked) {
            if (served.size() >= want) {
                break;
            }
            if (!tryConsume(userId, deviceId, candidate.materialId(), day, dayTtl)) {
                continue;
            }
            served.add(candidate);
        }
        if (AdForms.POPUP.equals(position.form()) && !served.isEmpty() && AdFreqKeys.hasSubject(userId, deviceId)) {
            freq.trySetCooldown(
                    AdFreqKeys.popup(userId, deviceId), Duration.ofSeconds(settings.popupCooldownSeconds()));
        }
        List<PortalAdMaterialView> materialsOut = new ArrayList<>();
        for (AdCandidate candidate : served) {
            materialsOut.add(toPortal(position, candidate, day));
        }
        Integer splash = AdForms.SPLASH.equals(position.form()) ? settings.splashDurationSeconds() : null;
        Integer interval = AdForms.CAROUSEL.equals(position.form()) ? settings.carouselIntervalSeconds() : null;
        return new PortalAdPositionView(position.code(), position.form(), materialsOut, splash, interval);
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

    private boolean visible(
            CachedPosition position, CachedPlacement placement, Long userId, String platform, Instant now) {
        if (placement == null || placement.material() == null) {
            return false;
        }
        if (!AdStatuses.ENABLED.equals(placement.status())
                || !AdStatuses.ENABLED.equals(placement.material().status())) {
            return false;
        }
        CachedMaterial material = placement.material();
        if (!AdDates.inWindow(material.startTime(), material.endTime(), now)
                || !AdDates.inWindow(placement.startTime(), placement.endTime(), now)) {
            return false;
        }
        List<String> platforms =
                placement.platforms() == null || placement.platforms().isEmpty()
                        ? position.platforms()
                        : placement.platforms();
        if (!AdPlatforms.allows(platforms, platform)) {
            return false;
        }
        if (userId == null) {
            return true;
        }
        if (!AdGrayBuckets.hit(placement.grayType(), placement.grayRatio(), userId, position.id())) {
            return false;
        }
        // crowd_id is stored; anonymous already returned. Logged-in crowd is R11 reuse without
        // visiting task_ tables (no CrowdPort). Unset crowd = no filter.
        return true;
    }

    private boolean tryConsume(Long userId, String deviceId, long materialId, String day, Duration ttl) {
        if (!AdFreqKeys.hasSubject(userId, deviceId)) {
            return true;
        }
        String key = AdFreqKeys.daily(userId, deviceId, materialId, day);
        long n = freq.increment(key, ttl);
        return n <= settings.dailyImpressionLimit();
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

    private static AdCandidate toCandidate(CachedPlacement placement) {
        CachedMaterial material = placement.material();
        return new AdCandidate(
                placement.materialId(),
                placement.weight(),
                material.title(),
                material.subtitle(),
                material.imageUrl(),
                material.jumpType(),
                material.jumpParams(),
                material.startTime(),
                material.endTime(),
                placement.startTime(),
                placement.endTime(),
                placement.platforms(),
                placement.grayType(),
                placement.grayRatio(),
                placement.crowdId());
    }

    private static PortalAdMaterialView toPortal(CachedPosition position, AdCandidate candidate, String day) {
        String trackId = position.id() + ":" + candidate.materialId() + ":" + day;
        return new PortalAdMaterialView(
                candidate.materialId(),
                trackId,
                candidate.title(),
                candidate.subtitle(),
                candidate.imageUrl(),
                candidate.jumpType(),
                candidate.jumpParams() == null ? Map.of() : candidate.jumpParams(),
                candidate.weight());
    }

    private PortalAdPositionView empty(CachedPosition position) {
        Integer splash = AdForms.SPLASH.equals(position.form()) ? settings.splashDurationSeconds() : null;
        Integer interval = AdForms.CAROUSEL.equals(position.form()) ? settings.carouselIntervalSeconds() : null;
        return new PortalAdPositionView(position.code(), position.form(), List.of(), splash, interval);
    }
}
