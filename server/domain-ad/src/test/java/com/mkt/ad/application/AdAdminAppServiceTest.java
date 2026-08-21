package com.mkt.ad.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.ad.command.AdMaterialSaveCommand;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.domain.AdForms;
import com.mkt.ad.query.AdPositionQuery;
import com.mkt.ad.response.AdPositionView;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.ad.testsupport.MemoryAdStores;
import com.mkt.infra.cache.CacheNamespace;
import com.mkt.infra.cache.TwoLevelPlatformCache;
import com.mkt.infra.redis.MemoryKeyValueStore;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.PageQuery;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdAdminAppServiceTest {

    private MemoryAdStores stores;
    private MemoryKeyValueStore kv;
    private TwoLevelPlatformCache cache;
    private AdAdminAppService admin;

    @BeforeEach
    void setUp() {
        UserContext.set(new UserPrincipal(1L, "admin", "op"));
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
    void saveBindAndOverlapCountThenEvictsPositionCache() {
        long positionId = admin.savePosition(
                        new AdPositionSaveCommand(null, "home_banner", "首页轮播", AdForms.CAROUSEL, List.of("WEB"), "ENABLED"))
                .id();
        long a = material("a", 10);
        long b = material("b", 20);
        admin.bind(
                positionId,
                new AdPlacementSaveCommand(
                        a,
                        10,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-20T00:00:00Z"),
                        List.of(),
                        "NONE",
                        null,
                        null,
                        "ENABLED"));
        admin.bind(
                positionId,
                new AdPlacementSaveCommand(
                        b,
                        20,
                        Instant.parse("2026-08-10T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        List.of(),
                        "NONE",
                        null,
                        null,
                        "ENABLED"));
        AdPositionView view = admin.getPosition(positionId);
        assertThat(view.overlapCount()).isEqualTo(1);
        assertThat(view.placements()).hasSize(2);
        cache.put(CacheNamespace.AD_POSITION, "home_banner", "stale");
        admin.savePosition(new AdPositionSaveCommand(
                positionId, "home_banner", "首页轮播", AdForms.CAROUSEL, List.of("WEB"), "DISABLED"));
        assertThat(kv.get("ad:position:home_banner")).isNull();
        assertThat(admin.pagePositions(new AdPositionQuery(null, null, null, PageQuery.of(1, 20))).total())
                .isEqualTo(1);
    }

    @Test
    void duplicateCodeAndInvalidWeight() {
        admin.savePosition(
                new AdPositionSaveCommand(null, "home_banner", "n", AdForms.IMAGE, List.of("WEB"), "ENABLED"));
        assertThatThrownBy(() -> admin.savePosition(
                        new AdPositionSaveCommand(null, "home_banner", "n", AdForms.IMAGE, List.of("WEB"), "ENABLED")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AdErrorCodes.POSITION_CODE_DUPLICATE);
        assertThatThrownBy(() -> admin.saveMaterial(new AdMaterialSaveCommand(
                        null,
                        "x",
                        null,
                        "https://cdn.example/x.png",
                        "NONE",
                        Map.of(),
                        0,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        "ENABLED")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(AdErrorCodes.MATERIAL_WEIGHT_INVALID);
    }

    private long material(String title, int weight) {
        return admin.saveMaterial(new AdMaterialSaveCommand(
                        null,
                        title,
                        null,
                        "https://cdn.example/" + title + ".png",
                        "LINK",
                        Map.of("url", "https://example.com/" + title),
                        weight,
                        Instant.parse("2026-08-01T00:00:00Z"),
                        Instant.parse("2026-08-31T00:00:00Z"),
                        "ENABLED"))
                .id();
    }
}
