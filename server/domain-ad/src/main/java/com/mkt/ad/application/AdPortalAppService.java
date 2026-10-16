package com.mkt.ad.application;

import com.mkt.ad.response.OkResponse;
import com.mkt.ad.response.PortalAdPositionView;
import com.mkt.ad.support.AdSettings;
import com.mkt.contract.CrowdPort;
import com.mkt.infra.cache.PlatformCache;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AdPortalAppService {

    private final AdPortalPull portalPull;
    private final AdPortalDismiss portalDismiss;

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
            AdPortalPull portalPull,
            AdPortalDismiss portalDismiss) {
        // Manual/IT chain passes null. Production passes the injected collaborator.
        this.portalPull = portalPull != null
                ? portalPull
                : new AdPortalPull(positions, materials, placements, cache, freq, settings, crowds, clock);
        this.portalDismiss = portalDismiss != null
                ? portalDismiss
                : new AdPortalDismiss(positions, materials, placements, cache, freq, settings, clock);
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
        this(positions, materials, placements, cache, freq, settings, crowds, clock, null, null);
    }

    public PortalAdPositionView pull(String code, Long userId, String deviceId, String platformHeader) {
        return portalPull.pull(code, userId, deviceId, platformHeader);
    }

    public OkResponse dismiss(String positionCode, long materialId, Long userId, String deviceId) {
        return portalDismiss.dismiss(positionCode, materialId, userId, deviceId);
    }
}
