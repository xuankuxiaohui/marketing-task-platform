package com.mkt.ad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.response.PortalAdPositionView;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.ad.support.AdSettings;
import com.mkt.ad.testsupport.MemoryAdStores;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.time.MutableClock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdPortalAppServiceTest {

    private MemoryAdStores stores;
    private MemoryKeyValueStore kv;
    private AdAdminAppService admin;
    private AdPortalAppService portal;
    private AdSettings settings;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
        stores = new MemoryAdStores();
        kv = new MemoryKeyValueStore();
        clock = new MutableClock(Instant.parse("2026-08-20T04:00:00Z"));
        settings = new AdSettings();
        TwoLevelPlatformCache cache = new TwoLevelPlatformCache(kv);
        admin = new AdAdminAppService(stores.positions, stores.materials, stores.placements, cache);
        portal = new AdPortalAppService(
                stores.positions,
                stores.materials,
                stores.placements,
                cache,
                new AdFreqStore(kv),
                settings,
                clock);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void carouselOrdersByWeightThenIdAndHonorsDailyCap() {
        long positionId = position("home_banner", AdForms.CAROUSEL);
        long heavy = material("heavy", 10);
        long lightA = material("a", 5);
        long lightB = material("b", 5);
        bind(positionId, heavy, 50);
        bind(positionId, lightA, 10);
        bind(positionId, lightB, 10);
        PortalAdPositionView first = portal.pull("home_banner", 9L, null, "WEB");
        assertThat(first.materials()).extracting(m -> m.materialId()).containsExactly(heavy, lightA, lightB);
        assertThat(first.carouselIntervalSeconds()).isEqualTo(5);

        settings.setDailyImpressionLimit(1);
        settings.setCarouselMaxItems(5);
        for (int i = 0; i < 3; i++) {
            portal.pull("home_banner", 11L, null, "WEB");
        }
        PortalAdPositionView capped = portal.pull("home_banner", 11L, null, "WEB");
        assertThat(capped.materials()).isEmpty();
    }

    @Test
    void scheduleExcludesMaterialOutsideWindowForAnyUser() {
        long positionId = position("home_image", AdForms.IMAGE);
        long materialId = material("out", 20);
        bind(positionId, materialId, 20);
        clock.setInstant(Instant.parse("2026-09-01T00:00:00Z"));
        assertThat(portal.pull("home_image", 9L, "dev-1", "WEB").materials()).isEmpty();
        assertThat(portal.pull("home_image", null, "dev-1", "WEB").materials()).isEmpty();
    }

    @Test
    void popupCooldownBlocksAllPopupSlotsForSubject() {
        long positionId = position("home_popup", AdForms.POPUP);
        bind(positionId, material("p", 9), 9);
        PortalAdPositionView first = portal.pull("home_popup", 9L, null, "WEB");
        assertThat(first.materials()).hasSize(1);
        PortalAdPositionView second = portal.pull("home_popup", 9L, null, "WEB");
        assertThat(second.materials()).isEmpty();
        assertThat(portal.pull("home_popup", 8L, null, "WEB").materials()).hasSize(1);
    }

    @Test
    void anonymousSkipsGrayAndUsesDeviceFreqKey() {
        long positionId = position("home_float", AdForms.FLOAT);
        long materialId = material("f", 3);
        admin.bind(
                positionId,
                new AdPlacementSaveCommand(
                        materialId,
                        3,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        List.of(),
                        "RATIO",
                        0,
                        null,
                        "ENABLED"));
        assertThat(portal.pull("home_float", null, "dev-a", "WEB").materials()).hasSize(1);
        assertThat(portal.pull("home_float", 9L, null, "WEB").materials()).isEmpty();
        portal.dismiss("home_float", materialId, null, "dev-a");
        assertThat(portal.pull("home_float", null, "dev-a", "WEB").materials()).isEmpty();
    }

    @Test
    void platformFilterAndMissingPosition() {
        long positionId = position("app_splash", AdForms.SPLASH);
        bind(positionId, material("s", 1), 1);
        assertThat(portal.pull("app_splash", 9L, "d", "IOS").materials()).isEmpty();
        assertThat(portal.pull("app_splash", 9L, "d", "WEB").materials()).hasSize(1);
        assertThatThrownBy(() -> portal.pull("missing", 9L, "d", "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AdErrorCodes.POSITION_NOT_FOUND);
    }

    private long position(String code, String form) {
        return admin.savePosition(new AdPositionSaveCommand(null, code, code, form, List.of("WEB"), "ENABLED"))
                .id();
    }

    private long material(String title, int weight) {
        return admin.saveMaterial(new AdMaterialSaveCommand(
                        null,
                        title,
                        null,
                        "https://cdn.example/" + title + ".png",
                        "NONE",
                        Map.of(),
                        weight,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        "ENABLED"))
                .id();
    }

    private void bind(long positionId, long materialId, int weight) {
        admin.bind(
                positionId,
                new AdPlacementSaveCommand(
                        materialId,
                        weight,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        List.of(),
                        "NONE",
                        null,
                        null,
                        "ENABLED"));
    }
}
