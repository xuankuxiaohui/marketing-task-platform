package com.mkt.reward.controller.admin;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.Result;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.reward.application.SpendAppService;
import com.mkt.reward.response.SpendResponse;
import com.mkt.reward.response.SpendRowView;
import com.mkt.reward.support.RewardPermissions;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * WebMvc slice (standalone MockMvc) for {@link SpendAdminController}: real {@link SaInterceptor} enforces
 * {@code @SaCheckPermission}; a test StpLogic grants a fixed permission set.
 */
class SpendAdminControllerTest {

    private final SpendAppService spend = mock(SpendAppService.class);
    private final GrantedStpLogic stp = new GrantedStpLogic();
    private StpLogic original;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        original = StpUtil.stpLogic;
        StpUtil.setStpLogic(stp);
        mvc = MockMvcBuilders.standaloneSetup(new SpendAdminController(spend))
                .addInterceptors(new SaInterceptor())
                .setControllerAdvice(new NotPermissionAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(original);
    }

    @Test
    void spendWithoutRecordQueryIs403() throws Exception {
        stp.grant(RewardPermissions.RECON_QUERY);
        mvc.perform(get("/admin/reward/spend"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(spend);
    }

    /**
     * Spend has no bean-validation params; its only input check is query-param type conversion.
     * Malformed Instant query params raise MethodArgumentTypeMismatchException → 400 common.param-invalid.
     */
    @Test
    void malformedInstantParamIs400() throws Exception {
        stp.grant(RewardPermissions.RECORD_QUERY);
        mvc.perform(get("/admin/reward/spend").param("from", "not-an-instant"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(spend);
    }

    @Test
    void spendHappyPathPassesFilters() throws Exception {
        stp.grant(RewardPermissions.RECORD_QUERY);
        Instant from = Instant.parse("2026-10-01T00:00:00Z");
        Instant to = Instant.parse("2026-10-13T00:00:00Z");
        when(spend.spend(any(), any(), any(), any()))
                .thenReturn(new SpendResponse(List.of(new SpendRowView("TOKEN_A", 3, 300, 1, 100))));
        mvc.perform(get("/admin/reward/spend")
                        .param("categoryCode", "TOKEN_A")
                        .param("from", "2026-10-01T00:00:00Z")
                        .param("to", "2026-10-13T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.rows[0].categoryCode").value("TOKEN_A"))
                .andExpect(jsonPath("$.data.rows[0].arrivedCostFen").value(300))
                .andExpect(jsonPath("$.data.rows[0].sendingCount").value(1));
        verify(spend).spend("TOKEN_A", null, from, to);
    }

    @Test
    void spendWithoutFiltersPassesNulls() throws Exception {
        stp.grant(RewardPermissions.RECORD_QUERY);
        when(spend.spend(isNull(), isNull(), isNull(), isNull())).thenReturn(new SpendResponse(List.of()));
        mvc.perform(get("/admin/reward/spend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rows").isEmpty());
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
     * which serves this module at runtime in admin-app but is not on domain-reward's classpath.
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
