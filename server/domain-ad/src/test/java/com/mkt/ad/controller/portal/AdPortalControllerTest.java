package com.mkt.ad.controller.portal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.ad.application.AdPortalAppService;
import com.mkt.ad.response.OkResponse;
import com.mkt.ad.response.PortalAdMaterialView;
import com.mkt.ad.response.PortalAdPositionView;
import com.mkt.ad.support.AdErrorCodes;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.UserContext;
import com.mkt.kernel.UserPrincipal;
import com.mkt.kernel.web.GlobalExceptionHandler;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * WebMvc slice (standalone MockMvc) for {@link AdPortalController}. The endpoints are anonymous by design
 * (R30.6, no permission guard), so the "auth" axis covers portal {@link UserContext} propagation: anonymous
 * → userId null; logged-in → principal userId, plus device/platform headers.
 */
class AdPortalControllerTest {

    private final AdPortalAppService portal = mock(AdPortalAppService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        mvc = MockMvcBuilders.standaloneSetup(new AdPortalController(portal))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void anonymousPullPassesNullUserId() throws Exception {
        when(portal.pull(eq("home_banner"), isNull(), isNull(), isNull()))
                .thenReturn(new PortalAdPositionView("home_banner", "CAROUSEL", List.of(), null, 5));
        mvc.perform(get("/api/common/ad/positions/home_banner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.code").value("home_banner"))
                .andExpect(jsonPath("$.data.materials").isEmpty());
        verify(portal).pull("home_banner", null, null, null);
    }

    @Test
    void loggedInPullPassesPrincipalAndHeaders() throws Exception {
        UserContext.set(new UserPrincipal(7L, "portal", "u7"));
        when(portal.pull(any(), any(), any(), any()))
                .thenReturn(new PortalAdPositionView(
                        "home_banner",
                        "CAROUSEL",
                        List.of(new PortalAdMaterialView(
                                21L, "trk-1", "国庆", null, "https://x/a.png", "NONE", Map.of(), 10)),
                        null,
                        5));
        mvc.perform(get("/api/common/ad/positions/home_banner")
                        .header("X-Device-Id", "dev-1")
                        .header("X-Client-Platform", "H5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[0].materialId").value(21))
                .andExpect(jsonPath("$.data.materials[0].trackId").value("trk-1"));
        verify(portal).pull("home_banner", 7L, "dev-1", "H5");
    }

    @Test
    void dismissWithBlankPositionCodeIs400() throws Exception {
        mvc.perform(post("/api/common/ad/materials/21/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionCode\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(portal);
    }

    @Test
    void dismissWithMalformedBodyIs400() throws Exception {
        mvc.perform(post("/api/common/ad/materials/21/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verify(portal, never()).dismiss(anyString(), anyLong(), any(), any());
    }

    @Test
    void dismissHappyPathPassesUserAndDevice() throws Exception {
        UserContext.set(new UserPrincipal(7L, "portal", "u7"));
        when(portal.dismiss("float_corner", 21L, 7L, "dev-1")).thenReturn(OkResponse.yes());
        mvc.perform(post("/api/common/ad/materials/21/dismiss")
                        .header("X-Device-Id", "dev-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionCode\":\"float_corner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        verify(portal).dismiss("float_corner", 21L, 7L, "dev-1");
    }

    @Test
    void anonymousDismissPassesNullUser() throws Exception {
        when(portal.dismiss("float_corner", 21L, null, null)).thenReturn(OkResponse.yes());
        mvc.perform(post("/api/common/ad/materials/21/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionCode\":\"float_corner\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        verify(portal).dismiss("float_corner", 21L, null, null);
    }

    @Test
    void missingPositionAndUnboundMaterialMapToErrorCodes() throws Exception {
        when(portal.pull(eq("missing"), isNull(), isNull(), isNull()))
                .thenThrow(new BusinessException(AdErrorCodes.POSITION_NOT_FOUND));
        mvc.perform(get("/api/common/ad/positions/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ad.position.not-found"));

        when(portal.dismiss(eq("home_float"), eq(21L), isNull(), isNull()))
                .thenThrow(new BusinessException(AdErrorCodes.MATERIAL_NOT_FOUND));
        mvc.perform(post("/api/common/ad/materials/21/dismiss")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"positionCode\":\"home_float\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ad.material.not-found"));
    }
}
