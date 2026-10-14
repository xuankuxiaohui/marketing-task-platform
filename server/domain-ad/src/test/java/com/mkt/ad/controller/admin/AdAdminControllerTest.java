package com.mkt.ad.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.ad.application.AdAdminAppService;
import com.mkt.ad.command.AdPlacementSaveCommand;
import com.mkt.ad.command.AdPositionSaveCommand;
import com.mkt.ad.query.AdMaterialQuery;
import com.mkt.ad.query.AdPositionQuery;
import com.mkt.ad.response.AdMaterialView;
import com.mkt.ad.response.AdPositionView;
import com.mkt.ad.response.AdSaveResponse;
import com.mkt.ad.response.OkResponse;
import com.mkt.ad.support.AdPermissions;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * WebMvc slice (standalone MockMvc) for {@link AdAdminController}: real {@link SaInterceptor} enforces
 * {@code @SaCheckPermission}; a test StpLogic grants a fixed permission set.
 */
class AdAdminControllerTest {

    private static final String VALID_POSITION =
            "{\"code\":\"home_banner\",\"name\":\"首页横幅\",\"form\":\"CAROUSEL\",\"platforms\":[\"H5\"]}";

    private final AdAdminAppService appService = mock(AdAdminAppService.class);
    private final GrantedStpLogic stp = new GrantedStpLogic();
    private StpLogic original;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        original = StpUtil.stpLogic;
        StpUtil.setStpLogic(stp);
        mvc = MockMvcBuilders.standaloneSetup(new AdAdminController(appService))
                .addInterceptors(new SaInterceptor())
                .setControllerAdvice(new NotPermissionAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(original);
    }

    @Test
    void pagePositionsWithoutQueryPermissionIs403() throws Exception {
        mvc.perform(get("/admin/ad/positions"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(appService);
    }

    @Test
    void createPositionWithOnlyUpdatePermissionIsRejectedByInMethodCheck() throws Exception {
        // @SaCheckPermission(OR create/update) passes, then StpUtil.checkPermission(create) rejects id==null
        stp.grant(AdPermissions.POSITION_UPDATE);
        mvc.perform(post("/admin/ad/positions").contentType(MediaType.APPLICATION_JSON).content(VALID_POSITION))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(appService);
    }

    @Test
    void updateMaterialWithOnlyCreatePermissionIs403() throws Exception {
        stp.grant(AdPermissions.MATERIAL_CREATE);
        mvc.perform(post("/admin/ad/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"title\":\"t\",\"imageUrl\":\"https://x/a.png\",\"weight\":1,"
                                + "\"startTime\":\"2026-10-01T00:00:00Z\",\"endTime\":\"2026-10-31T00:00:00Z\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(appService);
    }

    @Test
    void savePositionWithInvalidBodyIs400() throws Exception {
        stp.grant(AdPermissions.POSITION_CREATE);
        mvc.perform(post("/admin/ad/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"ab\",\"name\":\"\",\"form\":\"CAROUSEL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(appService);
    }

    @Test
    void bindWithoutMaterialIdIs400() throws Exception {
        stp.grant(AdPermissions.POSITION_UPDATE);
        mvc.perform(post("/admin/ad/positions/3/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weight\":1,\"startTime\":\"2026-10-01T00:00:00Z\","
                                + "\"endTime\":\"2026-10-31T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(appService);
    }

    @Test
    void pagePositionsHappyPathPassesFilters() throws Exception {
        stp.grant(AdPermissions.POSITION_QUERY);
        when(appService.pagePositions(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new AdPositionView(
                                3L, "home_banner", "首页横幅", "CAROUSEL", List.of("H5"), "ENABLED", List.of(), 0))));
        mvc.perform(get("/admin/ad/positions").param("code", "home").param("page", "2").param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].code").value("home_banner"));
        ArgumentCaptor<AdPositionQuery> query = ArgumentCaptor.forClass(AdPositionQuery.class);
        verify(appService).pagePositions(query.capture());
        assertThat(query.getValue().code()).isEqualTo("home");
    }

    @Test
    void createPositionHappyPath() throws Exception {
        stp.grant(AdPermissions.POSITION_CREATE);
        when(appService.savePosition(any())).thenReturn(new AdSaveResponse(11L));
        mvc.perform(post("/admin/ad/positions").contentType(MediaType.APPLICATION_JSON).content(VALID_POSITION))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
        ArgumentCaptor<AdPositionSaveCommand> command = ArgumentCaptor.forClass(AdPositionSaveCommand.class);
        verify(appService).savePosition(command.capture());
        assertThat(command.getValue().code()).isEqualTo("home_banner");
        assertThat(command.getValue().id()).isNull();
    }

    @Test
    void deleteMaterialHappyPath() throws Exception {
        stp.grant(AdPermissions.MATERIAL_DELETE);
        when(appService.deleteMaterial(eq(5L))).thenReturn(OkResponse.yes());
        mvc.perform(delete("/admin/ad/materials/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        verify(appService).deleteMaterial(5L);
    }

    @Test
    void getAndDeletePositionRequireTheirPermissions() throws Exception {
        mvc.perform(get("/admin/ad/positions/3"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        mvc.perform(delete("/admin/ad/positions/3"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(appService);

        stp.grant(AdPermissions.POSITION_QUERY, AdPermissions.POSITION_DELETE);
        when(appService.getPosition(3L))
                .thenReturn(new AdPositionView(
                        3L, "home_banner", "首页横幅", "CAROUSEL", List.of("H5"), "ENABLED", List.of(), 0));
        when(appService.deletePosition(3L)).thenReturn(OkResponse.yes());
        mvc.perform(get("/admin/ad/positions/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("home_banner"))
                .andExpect(jsonPath("$.data.overlapCount").value(0));
        mvc.perform(delete("/admin/ad/positions/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        verify(appService).getPosition(3L);
        verify(appService).deletePosition(3L);
    }

    @Test
    void updatePositionRequiresUpdatePermission() throws Exception {
        stp.grant(AdPermissions.POSITION_CREATE);
        mvc.perform(post("/admin/ad/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"code\":\"home_banner\",\"name\":\"首页横幅\",\"form\":\"CAROUSEL\","
                                + "\"platforms\":[\"H5\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(appService);

        stp.grant(AdPermissions.POSITION_UPDATE);
        when(appService.savePosition(any())).thenReturn(new AdSaveResponse(4L));
        mvc.perform(post("/admin/ad/positions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":4,\"code\":\"home_banner\",\"name\":\"首页横幅\",\"form\":\"CAROUSEL\","
                                + "\"platforms\":[\"H5\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(4));
        ArgumentCaptor<AdPositionSaveCommand> command = ArgumentCaptor.forClass(AdPositionSaveCommand.class);
        verify(appService).savePosition(command.capture());
        assertThat(command.getValue().id()).isEqualTo(4L);
    }

    @Test
    void bindAndUnbindPassIds() throws Exception {
        mvc.perform(delete("/admin/ad/positions/3/materials/9")).andExpect(status().isForbidden());
        verifyNoInteractions(appService);

        stp.grant(AdPermissions.POSITION_UPDATE);
        when(appService.bind(eq(3L), any())).thenReturn(new AdSaveResponse(15L));
        when(appService.unbind(3L, 9L)).thenReturn(OkResponse.yes());
        mvc.perform(post("/admin/ad/positions/3/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"materialId\":9,\"weight\":10,\"startTime\":\"2026-10-01T00:00:00Z\","
                                + "\"endTime\":\"2026-10-31T00:00:00Z\",\"grayType\":\"RATIO\",\"grayRatio\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(15));
        ArgumentCaptor<AdPlacementSaveCommand> command = ArgumentCaptor.forClass(AdPlacementSaveCommand.class);
        verify(appService).bind(eq(3L), command.capture());
        assertThat(command.getValue().materialId()).isEqualTo(9L);
        assertThat(command.getValue().weight()).isEqualTo(10);
        assertThat(command.getValue().grayRatio()).isEqualTo(20);

        mvc.perform(delete("/admin/ad/positions/3/materials/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        verify(appService).unbind(3L, 9L);
    }

    @Test
    void pageAndGetMaterialPassFilters() throws Exception {
        mvc.perform(get("/admin/ad/materials")).andExpect(status().isForbidden());
        verifyNoInteractions(appService);

        stp.grant(AdPermissions.MATERIAL_QUERY);
        when(appService.pageMaterials(any()))
                .thenReturn(new PageData<>(
                        1,
                        List.of(new AdMaterialView(
                                5L,
                                "国庆",
                                null,
                                "https://cdn.example/a.png",
                                "NONE",
                                Map.of(),
                                10,
                                Instant.parse("2026-10-01T00:00:00Z"),
                                Instant.parse("2026-10-31T00:00:00Z"),
                                "ENABLED"))));
        when(appService.getMaterial(5L))
                .thenReturn(new AdMaterialView(
                        5L,
                        "国庆",
                        "副标题",
                        "https://cdn.example/a.png",
                        "LINK",
                        Map.of("url", "https://example.com"),
                        10,
                        Instant.parse("2026-10-01T00:00:00Z"),
                        Instant.parse("2026-10-31T00:00:00Z"),
                        "ENABLED"));
        mvc.perform(get("/admin/ad/materials").param("title", "国庆").param("status", "ENABLED").param("pageSize", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].title").value("国庆"));
        ArgumentCaptor<AdMaterialQuery> query = ArgumentCaptor.forClass(AdMaterialQuery.class);
        verify(appService).pageMaterials(query.capture());
        assertThat(query.getValue().title()).isEqualTo("国庆");
        assertThat(query.getValue().status()).isEqualTo("ENABLED");
        assertThat(query.getValue().page().pageSize()).isEqualTo(5);

        mvc.perform(get("/admin/ad/materials/5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subtitle").value("副标题"))
                .andExpect(jsonPath("$.data.jumpType").value("LINK"));
        verify(appService).getMaterial(5L);
    }

    @Test
    void createMaterialHappyPathAndInvalidBodyIs400() throws Exception {
        stp.grant(AdPermissions.MATERIAL_CREATE);
        mvc.perform(post("/admin/ad/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"\",\"imageUrl\":\"https://cdn.example/a.png\",\"weight\":1,"
                                + "\"startTime\":\"2026-10-01T00:00:00Z\",\"endTime\":\"2026-10-31T00:00:00Z\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(appService);

        when(appService.saveMaterial(any())).thenReturn(new AdSaveResponse(21L));
        mvc.perform(post("/admin/ad/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"国庆\",\"imageUrl\":\"https://cdn.example/a.png\",\"weight\":10,"
                                + "\"startTime\":\"2026-10-01T00:00:00Z\",\"endTime\":\"2026-10-31T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(21));
        verify(appService).saveMaterial(any());

        stp.grant(AdPermissions.MATERIAL_UPDATE);
        when(appService.saveMaterial(any())).thenReturn(new AdSaveResponse(5L));
        mvc.perform(post("/admin/ad/materials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":5,\"title\":\"国庆\",\"imageUrl\":\"https://cdn.example/a.png\",\"weight\":10,"
                                + "\"startTime\":\"2026-10-01T00:00:00Z\",\"endTime\":\"2026-10-31T00:00:00Z\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(5));
    }

    /** Grants a fixed permission set; otherwise throws the real Sa-Token {@link NotPermissionException}. */
    static final class GrantedStpLogic extends StpLogic {

        private final Set<String> granted = new HashSet<>();

        GrantedStpLogic() {
            super("login");
        }

        void grant(String... permissions) {
            granted.addAll(List.of(permissions));
        }

        @Override
        public void checkPermission(String permission) {
            if (!granted.contains(permission)) {
                throw new NotPermissionException(permission);
            }
        }

        @Override
        public void checkPermissionAnd(String... permissions) {
            for (String permission : permissions) {
                checkPermission(permission);
            }
        }

        @Override
        public void checkPermissionOr(String... permissions) {
            for (String permission : permissions) {
                if (granted.contains(permission)) {
                    return;
                }
            }
            throw new NotPermissionException(permissions.length == 0 ? "" : permissions[0]);
        }
    }

    /**
     * Mirrors identity {@code SaTokenExceptionHandler#handleNotPermission} (403 common.permission-denied),
     * which serves this module at runtime in admin-app but is not on domain-ad's classpath.
     */
    @RestControllerAdvice
    @Order(Ordered.HIGHEST_PRECEDENCE)
    static final class NotPermissionAdvice {

        @ExceptionHandler(NotPermissionException.class)
        ResponseEntity<Result<Void>> handle(NotPermissionException ex) {
            return ResponseEntity.status(CommonErrorCodes.PERMISSION_DENIED.httpStatus())
                    .body(Result.fail(CommonErrorCodes.PERMISSION_DENIED));
        }
    }
}
