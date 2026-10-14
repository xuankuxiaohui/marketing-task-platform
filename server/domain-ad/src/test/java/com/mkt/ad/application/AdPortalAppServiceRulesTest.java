package com.mkt.ad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.domain.AdGrayTypes;
import com.mkt.ad.domain.AdPlatforms;
import com.mkt.ad.entity.AdMaterialEntity;
import com.mkt.ad.entity.AdPositionMaterialEntity;
import com.mkt.ad.response.PortalAdPositionView;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.ad.support.AdSettings;
import com.mkt.ad.testsupport.MemoryAdStores;
import com.mkt.contract.CrowdPort;
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

class AdPortalAppServiceRulesTest {

    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-31T00:00:00Z");
    private static final CrowdPort NO_CROWD = new CrowdPort() {
        @Override
        public boolean memberOf(long crowdId, long userId) {
            return false;
        }

        @Override
        public boolean memberOfCode(String crowdCode, long userId) {
            return false;
        }
    };

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
                stores.positions, stores.materials, stores.placements, cache, new AdFreqStore(kv), settings, NO_CROWD, clock);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void disabledPositionReturnsEmptyWithFormMetadata() {
        long splash = position("app_splash", AdForms.SPLASH, "DISABLED");
        bind(splash, material("s", 1), 1, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        PortalAdPositionView splashView = portal.pull("app_splash", 9L, "d", "WEB");
        assertThat(splashView.materials()).isEmpty();
        assertThat(splashView.splashDurationSeconds()).isEqualTo(3);
        assertThat(splashView.carouselIntervalSeconds()).isNull();

        position("home_banner", AdForms.CAROUSEL, "DISABLED");
        PortalAdPositionView carousel = portal.pull("home_banner", 9L, "d", "WEB");
        assertThat(carousel.materials()).isEmpty();
        assertThat(carousel.carouselIntervalSeconds()).isEqualTo(5);
        assertThat(carousel.splashDurationSeconds()).isNull();
    }

    @Test
    void hidesDisabledDeletedAndOutOfWindowRows() {
        long offPlace = position("off_place", AdForms.IMAGE, "ENABLED");
        bind(offPlace, material("off-place", 5), 5, List.of(), AdGrayTypes.NONE, null, "DISABLED");
        assertThat(portal.pull("off_place", 9L, "d", "WEB").materials()).isEmpty();

        long offMat = position("off_mat", AdForms.IMAGE, "ENABLED");
        long disabledMaterial = material("off-mat", 6);
        bind(offMat, disabledMaterial, 6, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        admin.saveMaterial(materialCommand(disabledMaterial, "off-mat", 6, "DISABLED"));
        assertThat(portal.pull("off_mat", 9L, "d", "WEB").materials()).isEmpty();

        long gonePos = position("gone_pos", AdForms.IMAGE, "ENABLED");
        long deleted = material("gone", 7);
        bind(gonePos, deleted, 7, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        admin.deleteMaterial(deleted);
        assertThat(portal.pull("gone_pos", 9L, "d", "WEB").materials()).isEmpty();

        long scheduled = position("sched_image", AdForms.IMAGE, "ENABLED");
        bind(
                scheduled,
                material("early", 3),
                3,
                List.of(),
                AdGrayTypes.NONE,
                null,
                "ENABLED",
                Instant.parse("2026-08-21T00:00:00Z"),
                END);
        assertThat(portal.pull("sched_image", null, "dev", "WEB").materials()).isEmpty();
    }

    @Test
    void placementPlatformOverridesPositionAndFullRatioHits() {
        long positionId = position("app_slot", AdForms.IMAGE, "ENABLED");
        long materialId = material("ios-only", 4);
        bind(positionId, materialId, 4, List.of(" ios "), AdGrayTypes.RATIO, 100, "ENABLED");
        assertThat(portal.pull("app_slot", 9L, "d", "WEB").materials()).isEmpty();
        assertThat(portal.pull("app_slot", 9L, "d", null).materials()).isEmpty();
        assertThat(portal.pull("app_slot", 9L, "d", "desktop").materials()).isEmpty();
        PortalAdPositionView hit = portal.pull("app_slot", 9L, "d", "IOS");
        assertThat(hit.materials()).extracting(m -> m.materialId()).containsExactly(materialId);
        assertThat(hit.materials().getFirst().trackId()).isEqualTo(positionId + ":" + materialId + ":20260820");
    }

    @Test
    void singleSlotKeepsHeavierAndSplashReportsDuration() {
        long image = position("home_image", AdForms.IMAGE, "ENABLED");
        long light = material("light", 1);
        long heavy = material("heavy", 9);
        bind(image, light, 1, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        bind(image, heavy, 9, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        assertThat(portal.pull("home_image", 9L, "d", "WEB").materials())
                .extracting(m -> m.materialId())
                .containsExactly(heavy);

        long splash = position("app_splash", AdForms.SPLASH, "ENABLED");
        long splashMaterial = material("splash", 2);
        bind(splash, splashMaterial, 2, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        PortalAdPositionView view = portal.pull("app_splash", 9L, "d", "WEB");
        assertThat(view.materials()).extracting(m -> m.materialId()).containsExactly(splashMaterial);
        assertThat(view.splashDurationSeconds()).isEqualTo(3);
        assertThat(view.materials().getFirst().jumpParams()).isEmpty();
    }

    @Test
    void dailyCapSkipsConsumedCandidate() {
        settings.setDailyImpressionLimit(1);
        settings.setCarouselMaxItems(1);
        long positionId = position("home_banner", AdForms.CAROUSEL, "ENABLED");
        long heavy = material("heavy", 10);
        long light = material("light", 5);
        bind(positionId, heavy, 50, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        bind(positionId, light, 10, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        assertThat(portal.pull("home_banner", 11L, null, "WEB").materials())
                .extracting(m -> m.materialId())
                .containsExactly(heavy);
        assertThat(portal.pull("home_banner", 11L, null, "WEB").materials())
                .extracting(m -> m.materialId())
                .containsExactly(light);
        assertThat(portal.pull("home_banner", 11L, null, "WEB").materials()).isEmpty();
        assertThat(portal.pull("home_banner", 12L, null, "WEB").materials())
                .extracting(m -> m.materialId())
                .containsExactly(heavy);
    }

    @Test
    void dismissOnlyBurnsFloatBoundToASubject() {
        long image = position("home_image", AdForms.IMAGE, "ENABLED");
        long imageMaterial = material("img", 2);
        bind(image, imageMaterial, 2, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        assertThat(portal.dismiss("home_image", 404L, 9L, "dev").ok()).isTrue();
        assertThat(portal.pull("home_image", 9L, "dev", "WEB").materials()).hasSize(1);

        assertThatThrownBy(() -> portal.dismiss("missing", imageMaterial, 9L, "dev"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AdErrorCodes.POSITION_NOT_FOUND);
        assertThatThrownBy(() -> portal.pull(" ", 9L, "dev", "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AdErrorCodes.POSITION_NOT_FOUND);
        assertThatThrownBy(() -> portal.pull(null, 9L, "dev", "WEB"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AdErrorCodes.POSITION_NOT_FOUND);

        long floating = position("home_float", AdForms.FLOAT, "ENABLED");
        long floatMaterial = material("float", 3);
        bind(floating, floatMaterial, 3, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        assertThatThrownBy(() -> portal.dismiss("home_float", 404L, 9L, "dev"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AdErrorCodes.MATERIAL_NOT_FOUND);
        assertThat(portal.dismiss("home_float", floatMaterial, null, " ").ok()).isTrue();
        assertThat(portal.pull("home_float", null, " ", "WEB").materials()).hasSize(1);
        assertThat(portal.dismiss("home_float", floatMaterial, 9L, null).ok()).isTrue();
        assertThat(portal.pull("home_float", 9L, null, "WEB").materials()).isEmpty();
        assertThat(portal.pull("home_float", 8L, null, "WEB").materials()).hasSize(1);
    }

    @Test
    void popupWithoutSubjectServesEveryPull() {
        long positionId = position("home_popup", AdForms.POPUP, "ENABLED");
        bind(positionId, material("p", 1), 1, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        assertThat(portal.pull("home_popup", null, " ", "WEB").materials()).hasSize(1);
        assertThat(portal.pull("home_popup", null, null, "WEB").materials()).hasSize(1);
        assertThat(portal.pull("home_popup", null, "dev-a", "WEB").materials()).hasSize(1);
        assertThat(portal.pull("home_popup", null, "dev-a", "WEB").materials()).isEmpty();
    }

    @Test
    void unknownGrayHidesLoggedInUserAndAnonymousSkipsIt() {
        long positionId = position("home_image", AdForms.IMAGE, "ENABLED");
        long materialId = material("g", 2);
        bind(positionId, materialId, 2, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        AdPositionMaterialEntity rel = stores.placements.getByPositionAndMaterial(positionId, materialId);
        rel.setGrayType("COHORT");
        stores.placements.update(rel);
        assertThat(portal.pull("home_image", 9L, null, "WEB").materials()).isEmpty();
        assertThat(portal.pull("home_image", null, "dev", "WEB").materials()).hasSize(1);
    }

    @Test
    void catalogStaysCachedUntilMaterialSaveEvicts() {
        long positionId = position("home_image", AdForms.IMAGE, "ENABLED");
        long materialId = material("orig", 2);
        bind(positionId, materialId, 2, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        assertThat(portal.pull("home_image", 9L, "d", "WEB").materials().getFirst().title()).isEqualTo("orig");

        AdMaterialEntity stored = stores.materials.getById(materialId);
        stored.setTitle("changed-under-cache");
        stores.materials.update(stored);
        assertThat(portal.pull("home_image", 9L, "d", "WEB").materials().getFirst().title()).isEqualTo("orig");

        admin.saveMaterial(materialCommand(materialId, "fresh", 2, "ENABLED"));
        assertThat(portal.pull("home_image", 9L, "d", "WEB").materials().getFirst().title()).isEqualTo("fresh");
    }

    @Test
    void freqOutageFailsClosedAndDismissDoesNotThrow() {
        long image = position("home_image", AdForms.IMAGE, "ENABLED");
        long materialId = material("img", 2);
        bind(image, materialId, 2, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        assertThat(portal.pull("home_image", 9L, "d", "WEB").materials()).hasSize(1);

        long floating = position("home_float", AdForms.FLOAT, "ENABLED");
        long floatMaterial = material("float", 3);
        bind(floating, floatMaterial, 3, List.of(), AdGrayTypes.NONE, null, "ENABLED");

        kv.setAvailable(false);
        assertThat(portal.pull("home_image", 9L, "d", "WEB").materials()).isEmpty();
        assertThat(portal.dismiss("home_float", floatMaterial, 9L, "d").ok()).isTrue();

        long popup = position("home_popup", AdForms.POPUP, "ENABLED");
        bind(popup, material("p", 1), 1, List.of(), AdGrayTypes.NONE, null, "ENABLED");
        PortalAdPositionView blocked = portal.pull("home_popup", 9L, "d", "WEB");
        assertThat(blocked.materials()).isEmpty();
        assertThat(blocked.code()).isEqualTo("home_popup");

        kv.setAvailable(true);
        assertThat(portal.pull("home_image", 9L, "d", "WEB").materials()).hasSize(1);
    }

    private long position(String code, String form, String status) {
        return admin.savePosition(new AdPositionSaveCommand(null, code, code, form, List.of(AdPlatforms.WEB), status))
                .id();
    }

    private long material(String title, int weight) {
        return admin.saveMaterial(materialCommand(null, title, weight, "ENABLED")).id();
    }

    private static AdMaterialSaveCommand materialCommand(Long id, String title, int weight, String status) {
        return new AdMaterialSaveCommand(
                id,
                title,
                null,
                "https://cdn.example/" + title + ".png",
                "NONE",
                Map.of(),
                weight,
                START,
                END,
                status);
    }

    private void bind(
            long positionId, long materialId, int weight, List<String> platforms, String gray, Integer ratio, String status) {
        bind(positionId, materialId, weight, platforms, gray, ratio, status, START, END);
    }

    private void bind(
            long positionId,
            long materialId,
            int weight,
            List<String> platforms,
            String gray,
            Integer ratio,
            String status,
            Instant start,
            Instant end) {
        admin.bind(
                positionId,
                new AdPlacementSaveCommand(materialId, weight, start, end, platforms, gray, ratio, null, status));
    }
}
