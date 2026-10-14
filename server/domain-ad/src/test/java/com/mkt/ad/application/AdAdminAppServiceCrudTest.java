package com.mkt.ad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.domain.AdGrayTypes;
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
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.ad.testsupport.MemoryAdStores;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.PlatformCache;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.ErrorCode;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class AdAdminAppServiceCrudTest {

    private static final Instant START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant END = Instant.parse("2026-08-31T00:00:00Z");

    private MemoryAdStores stores;
    private MemoryKeyValueStore kv;
    private TwoLevelPlatformCache cache;
    private AdAdminAppService admin;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(7L, "admin", "op"));
        stores = new MemoryAdStores();
        kv = new MemoryKeyValueStore();
        cache = new TwoLevelPlatformCache(kv);
        admin = new AdAdminAppService(stores.positions, stores.materials, stores.placements, cache);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void createNormalizesFormStatusAndPlatformsThenPages() {
        long id = admin.savePosition(new AdPositionSaveCommand(
                        null, "home_banner", "首页", " carousel ", List.of("ios", " ", "WEB", "ios", "nope"), " enabled "))
                .id();
        AdPositionView view = admin.getPosition(id);
        assertThat(view.form()).isEqualTo(AdForms.CAROUSEL);
        assertThat(view.status()).isEqualTo(AdStatuses.ENABLED);
        assertThat(view.platforms()).containsExactly(AdPlatforms.IOS, AdPlatforms.WEB);
        assertThat(stores.positions.getById(id).getCreatedBy()).isEqualTo(7L);

        admin.savePosition(new AdPositionSaveCommand(
                null, "side_image", "侧栏", AdForms.IMAGE, null, null));
        assertThat(admin.pagePositions(new AdPositionQuery(null, null, null, null)).total()).isEqualTo(2);
        assertThat(admin.pagePositions(new AdPositionQuery("home_banner", AdForms.CAROUSEL, AdStatuses.ENABLED, null))
                        .records())
                .extracting(AdPositionView::code)
                .containsExactly("home_banner");
        assertThat(admin.pagePositions(new AdPositionQuery(" ", " ", " ", PageQuery.of(1, 20))).total())
                .isEqualTo(2);
        assertThat(admin.pagePositions(new AdPositionQuery(null, null, null, PageQuery.of(2, 1))).records())
                .hasSize(1);
    }

    @Test
    void rejectsInvalidPositionAndMissingOperator() {
        assertCode(
                () -> admin.savePosition(position(null, "home_banner", null, AdStatuses.ENABLED)),
                AdErrorCodes.POSITION_FORM_INVALID);
        assertMessage(
                () -> admin.savePosition(position(null, "AB", AdForms.IMAGE, AdStatuses.ENABLED)),
                CommonErrorCodes.PARAM_INVALID,
                "广告位编码格式不正确");
        assertMessage(
                () -> admin.savePosition(position(null, "home_banner", AdForms.IMAGE, "ARCHIVED")),
                CommonErrorCodes.PARAM_INVALID,
                "状态无效");

        UserContext.clear();
        assertCode(
                () -> admin.savePosition(position(null, "home_banner", AdForms.IMAGE, null)),
                CommonErrorCodes.PERMISSION_DENIED);
        UserContext.set(new UserPrincipal(null, "admin", "op"));
        assertCode(
                () -> admin.savePosition(position(null, "home_banner", AdForms.IMAGE, null)),
                CommonErrorCodes.PERMISSION_DENIED);
        assertCode(() -> admin.getPosition(99L), AdErrorCodes.POSITION_NOT_FOUND);
    }

    @Test
    void renameCollidesEvictsBothCodesAndDeleteFreesCode() {
        long first = admin.savePosition(position(null, "home_banner", AdForms.IMAGE, AdStatuses.ENABLED))
                .id();
        long second = admin.savePosition(position(null, "side_float", AdForms.FLOAT, AdStatuses.ENABLED))
                .id();
        assertCode(
                () -> admin.savePosition(position(first, "side_float", AdForms.IMAGE, AdStatuses.ENABLED)),
                AdErrorCodes.POSITION_CODE_DUPLICATE);

        cache.put(CacheNamespace.AD_POSITION, "home_banner", "stale-old");
        cache.put(CacheNamespace.AD_POSITION, "app_banner", "stale-new");
        assertThat(admin.savePosition(position(first, "app_banner", AdForms.SPLASH, AdStatuses.DISABLED))
                        .id())
                .isEqualTo(first);
        assertThat(kv.get("ad:position:home_banner")).isNull();
        assertThat(kv.get("ad:position:app_banner")).isNull();
        assertThat(admin.getPosition(first).form()).isEqualTo(AdForms.SPLASH);
        assertThat(admin.getPosition(first).status()).isEqualTo(AdStatuses.DISABLED);

        assertThat(admin.deletePosition(first).ok()).isTrue();
        assertCode(() -> admin.getPosition(first), AdErrorCodes.POSITION_NOT_FOUND);
        assertThat(admin.pagePositions(new AdPositionQuery(null, null, null, null)).total()).isEqualTo(1);
        long reused = admin.savePosition(position(null, "home_banner", AdForms.IMAGE, AdStatuses.ENABLED))
                .id();
        assertThat(reused).isNotEqualTo(first);
        assertThat(stores.positions.getById(second).getCode()).isEqualTo("side_float");
    }

    @Test
    void materialLifecycleFiltersAndEvictsCatalog() {
        long id = admin.saveMaterial(material(null, "国庆主会场", "副标题", "LINK", Map.of("url", "https://example.com/a"), 10, "ENABLED"))
                .id();
        AdMaterialView view = admin.getMaterial(id);
        assertThat(view.title()).isEqualTo("国庆主会场");
        assertThat(view.subtitle()).isEqualTo("副标题");
        assertThat(view.imageUrl()).isEqualTo("https://cdn.example/a.png");
        assertThat(view.jumpType()).isEqualTo("LINK");
        assertThat(view.jumpParams()).containsEntry("url", "https://example.com/a");
        assertThat(view.weight()).isEqualTo(10);
        assertThat(view.startTime()).isEqualTo(START);
        assertThat(view.endTime()).isEqualTo(END);
        assertThat(stores.materials.getById(id).getCreatedBy()).isEqualTo(7L);

        admin.saveMaterial(material(null, "其他", null, "NONE", null, 2, "DISABLED"));
        assertThat(admin.pageMaterials(new AdMaterialQuery("国庆", AdStatuses.ENABLED, null)).records())
                .extracting(AdMaterialView::id)
                .containsExactly(id);
        assertThat(admin.pageMaterials(new AdMaterialQuery(null, null, PageQuery.of(2, 1))).records())
                .hasSize(1);

        cache.put(CacheNamespace.AD_POSITION, "home_banner", "stale");
        admin.saveMaterial(material(
                id, "国庆更新", null, "ROUTE", Map.of("route", "/home"), 999, " disabled "));
        assertThat(kv.get("ad:position:home_banner")).isNull();
        AdMaterialView updated = admin.getMaterial(id);
        assertThat(updated.title()).isEqualTo("国庆更新");
        assertThat(updated.jumpType()).isEqualTo("ROUTE");
        assertThat(updated.jumpParams()).containsEntry("route", "/home");
        assertThat(updated.weight()).isEqualTo(999);
        assertThat(updated.status()).isEqualTo(AdStatuses.DISABLED);
        assertThat(updated.imageUrl()).isEqualTo("https://cdn.example/a.png");

        assertThat(admin.deleteMaterial(id).ok()).isTrue();
        assertCode(() -> admin.getMaterial(id), AdErrorCodes.MATERIAL_NOT_FOUND);
        assertThat(admin.pageMaterials(new AdMaterialQuery(null, null, null)).total()).isEqualTo(1);
    }

    @Test
    void materialValidationRejectsWeightWindowJumpImageAndStatus() {
        assertCode(
                () -> admin.saveMaterial(material(null, "x", null, "NONE", Map.of(), 0, "ENABLED")),
                AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        assertCode(
                () -> admin.saveMaterial(material(null, "x", null, "NONE", Map.of(), 1000, "ENABLED")),
                AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        assertCode(
                () -> admin.saveMaterial(new AdMaterialSaveCommand(
                        null, "x", null, "https://cdn.example/a.png", "NONE", Map.of(), null, START, END, "ENABLED")),
                AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        assertCode(
                () -> admin.saveMaterial(new AdMaterialSaveCommand(
                        null, "x", null, "https://cdn.example/a.png", "NONE", Map.of(), 1, END, START, "ENABLED")),
                AdErrorCodes.TIME_WINDOW_INVALID);
        assertCode(
                () -> admin.saveMaterial(new AdMaterialSaveCommand(
                        null, "x", null, "https://cdn.example/a.png", "NONE", Map.of(), 1, START, START, "ENABLED")),
                AdErrorCodes.TIME_WINDOW_INVALID);
        assertCode(
                () -> admin.saveMaterial(new AdMaterialSaveCommand(
                        null, "x", null, "https://cdn.example/a.png", "NONE", Map.of(), 1, null, END, "ENABLED")),
                AdErrorCodes.TIME_WINDOW_INVALID);
        assertCode(
                () -> admin.saveMaterial(material(null, "x", null, "ROUTE", Map.of(), 1, "ENABLED")),
                AdErrorCodes.MATERIAL_JUMP_INVALID);
        assertCode(
                () -> admin.saveMaterial(material(null, "x", null, "LINK", Map.of("url", "http://example.com"), 1, "ENABLED")),
                AdErrorCodes.MATERIAL_JUMP_INVALID);
        assertCode(
                () -> admin.saveMaterial(material(null, "x", null, "SCHEME", Map.of(), 1, "ENABLED")),
                AdErrorCodes.MATERIAL_JUMP_INVALID);
        assertCode(
                () -> admin.saveMaterial(material(null, "x", null, "NOPE", Map.of(), 1, "ENABLED")),
                AdErrorCodes.MATERIAL_JUMP_INVALID);
        assertMessage(
                () -> admin.saveMaterial(new AdMaterialSaveCommand(
                        null, "x", null, "http://cdn.example/a.png", "NONE", Map.of(), 1, START, END, "ENABLED")),
                CommonErrorCodes.PARAM_INVALID,
                "图片须为 https 外链");
        assertMessage(
                () -> admin.saveMaterial(new AdMaterialSaveCommand(
                        null,
                        "x",
                        null,
                        "https://" + "a".repeat(505),
                        "NONE",
                        Map.of(),
                        1,
                        START,
                        END,
                        "ENABLED")),
                CommonErrorCodes.PARAM_INVALID,
                "图片须为 https 外链");
        assertMessage(
                () -> admin.saveMaterial(material(null, "x", null, "NONE", Map.of(), 1, "ARCHIVED")),
                CommonErrorCodes.PARAM_INVALID,
                "状态无效");

        long id = admin.saveMaterial(new AdMaterialSaveCommand(
                        null,
                        "trim",
                        null,
                        "  HTTPS://cdn.example/trim.png  ",
                        null,
                        null,
                        1,
                        START,
                        END,
                        null))
                .id();
        AdMaterialView saved = admin.getMaterial(id);
        assertThat(saved.imageUrl()).isEqualTo("HTTPS://cdn.example/trim.png");
        assertThat(saved.jumpType()).isEqualTo("NONE");
        assertThat(saved.jumpParams()).isEmpty();
        assertThat(saved.status()).isEqualTo(AdStatuses.ENABLED);
    }

    @Test
    void bindUpdatesInPlaceAndUnbindEvicts() {
        long positionId = admin.savePosition(position(null, "home_banner", AdForms.CAROUSEL, AdStatuses.ENABLED))
                .id();
        long materialId = admin.saveMaterial(material(null, "a", null, "NONE", Map.of(), 4, "ENABLED"))
                .id();
        long placementId = admin.bind(positionId, placement(materialId, 4, List.of(" ios "), AdGrayTypes.RATIO, 40, 7L, "enabled"))
                .id();
        AdPlacementView bound = admin.getPosition(positionId).placements().getFirst();
        assertThat(bound.id()).isEqualTo(placementId);
        assertThat(bound.weight()).isEqualTo(4);
        assertThat(bound.platforms()).containsExactly(AdPlatforms.IOS);
        assertThat(bound.grayType()).isEqualTo(AdGrayTypes.RATIO);
        assertThat(bound.grayRatio()).isEqualTo(40);
        assertThat(bound.crowdId()).isEqualTo(7L);
        assertThat(bound.status()).isEqualTo(AdStatuses.ENABLED);
        assertThat(bound.material().title()).isEqualTo("a");

        long again = admin.bind(positionId, placement(materialId, 8, List.of(), AdGrayTypes.NONE, 40, null, "DISABLED"))
                .id();
        assertThat(again).isEqualTo(placementId);
        AdPlacementView updated = admin.getPosition(positionId).placements().getFirst();
        assertThat(updated.weight()).isEqualTo(8);
        assertThat(updated.platforms()).isEmpty();
        assertThat(updated.grayType()).isEqualTo(AdGrayTypes.NONE);
        assertThat(updated.grayRatio()).isNull();
        assertThat(updated.crowdId()).isNull();
        assertThat(updated.status()).isEqualTo(AdStatuses.DISABLED);

        long other = admin.saveMaterial(material(null, "b", null, "NONE", Map.of(), 1, "ENABLED"))
                .id();
        admin.bind(
                positionId,
                placement(
                        other,
                        1,
                        null,
                        null,
                        null,
                        null,
                        null,
                        Instant.parse("2026-09-01T00:00:00Z"),
                        Instant.parse("2026-09-20T00:00:00Z")));
        assertThat(admin.getPosition(positionId).overlapCount()).isZero();

        assertCode(() -> admin.unbind(positionId, 404L), AdErrorCodes.PLACEMENT_NOT_FOUND);
        cache.put(CacheNamespace.AD_POSITION, "home_banner", "stale");
        assertThat(admin.unbind(positionId, materialId).ok()).isTrue();
        assertThat(kv.get("ad:position:home_banner")).isNull();
        assertThat(admin.getPosition(positionId).placements()).extracting(AdPlacementView::materialId)
                .containsExactly(other);
    }

    @Test
    void placementValidationAndMissingRefs() {
        long positionId = admin.savePosition(position(null, "home_banner", AdForms.IMAGE, AdStatuses.ENABLED))
                .id();
        long materialId = admin.saveMaterial(material(null, "a", null, "NONE", Map.of(), 1, "ENABLED"))
                .id();
        assertCode(() -> admin.bind(99L, placement(materialId, 1, List.of(), AdGrayTypes.NONE, null, null, "ENABLED")),
                AdErrorCodes.POSITION_NOT_FOUND);
        assertCode(() -> admin.bind(positionId, placement(99L, 1, List.of(), AdGrayTypes.NONE, null, null, "ENABLED")),
                AdErrorCodes.MATERIAL_NOT_FOUND);
        admin.deleteMaterial(materialId);
        assertCode(() -> admin.bind(positionId, placement(materialId, 1, List.of(), AdGrayTypes.NONE, null, null, "ENABLED")),
                AdErrorCodes.MATERIAL_NOT_FOUND);

        long live = admin.saveMaterial(material(null, "b", null, "NONE", Map.of(), 1, "ENABLED"))
                .id();
        assertCode(
                () -> admin.bind(positionId, placement(live, 0, List.of(), AdGrayTypes.NONE, null, null, "ENABLED")),
                AdErrorCodes.MATERIAL_WEIGHT_INVALID);
        assertCode(
                () -> admin.bind(
                        positionId,
                        placement(live, 1, List.of(), AdGrayTypes.NONE, null, null, "ENABLED", END, START)),
                AdErrorCodes.TIME_WINDOW_INVALID);
        assertMessage(
                () -> admin.bind(positionId, placement(live, 1, List.of(), "COHORT", null, null, "ENABLED")),
                CommonErrorCodes.PARAM_INVALID,
                "灰度配置无效");
        assertMessage(
                () -> admin.bind(positionId, placement(live, 1, List.of(), AdGrayTypes.RATIO, null, null, "ENABLED")),
                CommonErrorCodes.PARAM_INVALID,
                "灰度比例无效");
        assertMessage(
                () -> admin.bind(positionId, placement(live, 1, List.of(), AdGrayTypes.RATIO, 101, null, "ENABLED")),
                CommonErrorCodes.PARAM_INVALID,
                "灰度比例无效");
        assertMessage(
                () -> admin.bind(positionId, placement(live, 1, List.of(), AdGrayTypes.NONE, null, null, "NO")),
                CommonErrorCodes.PARAM_INVALID,
                "状态无效");
        assertMessage(
                () -> admin.bind(positionId, placement(live, 1, List.of("DESKTOP"), AdGrayTypes.NONE, null, null, "ENABLED")),
                CommonErrorCodes.PARAM_INVALID,
                "端定向无效");

        admin.deletePosition(positionId);
        assertCode(() -> admin.unbind(positionId, live), AdErrorCodes.POSITION_NOT_FOUND);
    }

    @Test
    void overlapCountIgnoresOpenOrTouchingWindows() {
        AdPlacementView open = placementView(null, END);
        AdPlacementView touching = placementView(END, END.plusSeconds(60));
        AdPlacementView overlapping = placementView(END.minusSeconds(1), END.plusSeconds(60));
        assertThat(AdAdminViews.overlapCount(List.of(open, touching))).isZero();
        assertThat(AdAdminViews.overlapCount(List.of(open, overlapping, touching))).isEqualTo(1);
    }

    @Test
    void duplicateKeyRacesMapToBusinessErrors() {
        AdPositionStore positions = mock(AdPositionStore.class);
        AdMaterialStore materials = mock(AdMaterialStore.class);
        AdPlacementStore placements = mock(AdPlacementStore.class);
        PlatformCache platformCache = mock(PlatformCache.class);
        AdAdminAppService service = new AdAdminAppService(positions, materials, placements, platformCache);

        AdPositionEntity live = new AdPositionEntity();
        live.setId(5L);
        live.setCode("home_banner");
        live.setDeleted(0);
        when(positions.getById(5L)).thenReturn(live);
        when(positions.getByCode("app_banner")).thenReturn(null);
        when(positions.update(any())).thenThrow(new DuplicateKeyException("uk_code"));
        assertCode(
                () -> service.savePosition(position(5L, "app_banner", AdForms.IMAGE, AdStatuses.ENABLED)),
                AdErrorCodes.POSITION_CODE_DUPLICATE);
        verify(platformCache, never()).evictAfterCommit(any(), any());

        AdMaterialEntity material = new AdMaterialEntity();
        material.setId(9L);
        material.setDeleted(0);
        material.setWeight(3);
        when(materials.getById(9L)).thenReturn(material);
        when(placements.getByPositionAndMaterial(5L, 9L)).thenReturn(null);
        when(placements.insert(any())).thenThrow(new DuplicateKeyException("uk_position_material"));
        assertCode(
                () -> service.bind(5L, placement(9L, 3, List.of(), AdGrayTypes.NONE, null, null, "ENABLED")),
                AdErrorCodes.PLACEMENT_DUPLICATE);
        verify(platformCache, never()).evictAfterCommit(any(), any());
    }

    @Test
    void deletedMaterialLeavesPlacementShell() {
        long positionId = admin.savePosition(position(null, "home_banner", AdForms.IMAGE, AdStatuses.ENABLED))
                .id();
        long materialId = admin.saveMaterial(material(null, "gone", null, "NONE", Map.of(), 1, "ENABLED"))
                .id();
        admin.bind(positionId, placement(materialId, 1, List.of(), AdGrayTypes.NONE, null, null, "ENABLED"));
        admin.deleteMaterial(materialId);
        AdPlacementView shell = admin.getPosition(positionId).placements().getFirst();
        assertThat(shell.materialId()).isEqualTo(materialId);
        assertThat(shell.material()).isNull();
    }

    private static AdPositionSaveCommand position(Long id, String code, String form, String status) {
        return new AdPositionSaveCommand(id, code, "n", form, List.of(AdPlatforms.WEB), status);
    }

    private static AdMaterialSaveCommand material(
            Long id, String title, String subtitle, String jumpType, Map<String, Object> jumpParams, int weight, String status) {
        return new AdMaterialSaveCommand(
                id, title, subtitle, "https://cdn.example/a.png", jumpType, jumpParams, weight, START, END, status);
    }

    private static AdPlacementSaveCommand placement(
            long materialId,
            Integer weight,
            List<String> platforms,
            String grayType,
            Integer grayRatio,
            Long crowdId,
            String status) {
        return placement(materialId, weight, platforms, grayType, grayRatio, crowdId, status, START, END);
    }

    private static AdPlacementSaveCommand placement(
            long materialId,
            Integer weight,
            List<String> platforms,
            String grayType,
            Integer grayRatio,
            Long crowdId,
            String status,
            Instant start,
            Instant end) {
        return new AdPlacementSaveCommand(
                materialId, weight, start, end, platforms, grayType, grayRatio, crowdId, status);
    }

    private static AdPlacementView placementView(Instant start, Instant end) {
        return new AdPlacementView(1L, 1L, 1L, 1, start, end, List.of(), AdGrayTypes.NONE, null, null, "ENABLED", null);
    }

    private static void assertCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(code);
    }

    private static void assertMessage(
            org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code, String message) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasMessage(message)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(code);
    }
}
