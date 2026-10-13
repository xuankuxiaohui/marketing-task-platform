package com.mkt.reward.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.PageData;
import com.mkt.kernel.Result;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.reward.application.ReconAppService;
import com.mkt.reward.command.ReconActionCommand;
import com.mkt.reward.command.ReconBatchCreateCommand;
import com.mkt.reward.command.ReconImportCommand;
import com.mkt.reward.response.ReconActionResponse;
import com.mkt.reward.response.ReconBatchResponse;
import com.mkt.reward.response.ReconMatchResponse;
import com.mkt.reward.response.ReconReviewResponse;
import com.mkt.reward.support.RewardPermissions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
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
 * WebMvc slice (standalone MockMvc) for {@link ReconAdminController}: real {@link SaInterceptor} enforces
 * {@code @SaCheckPermission}; a test StpLogic grants a fixed permission set.
 */
class ReconAdminControllerTest {

    private static final LocalDate BILL_DATE = LocalDate.parse("2026-10-12");

    private final ReconAppService recon = mock(ReconAppService.class);
    private final GrantedStpLogic stp = new GrantedStpLogic();
    private StpLogic original;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        original = StpUtil.stpLogic;
        StpUtil.setStpLogic(stp);
        mvc = MockMvcBuilders.standaloneSetup(new ReconAdminController(recon))
                .addInterceptors(new SaInterceptor())
                .setControllerAdvice(new NotPermissionAdvice(), new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        StpUtil.setStpLogic(original);
    }

    @Test
    void batchesWithoutQueryPermissionIs403() throws Exception {
        mvc.perform(get("/admin/reward/recon/batches"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(recon);
    }

    @Test
    void queryOnlyOperatorCannotMatchImportOrAct() throws Exception {
        stp.grant(RewardPermissions.RECON_QUERY);
        mvc.perform(post("/admin/reward/recon/batches/4/match"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        mvc.perform(post("/admin/reward/recon/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryCode\":\"TOKEN_A\",\"billDate\":\"2026-10-12\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/admin/reward/recon/items/8/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REISSUE\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(recon);
    }

    @Test
    void createBatchWithoutBillDateIs400() throws Exception {
        stp.grant(RewardPermissions.RECON_IMPORT);
        mvc.perform(post("/admin/reward/recon/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryCode\":\"TOKEN_A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(recon);
    }

    @Test
    void importWithEmptyLinesOrBlankRefIs400() throws Exception {
        stp.grant(RewardPermissions.RECON_IMPORT);
        mvc.perform(post("/admin/reward/recon/batches/4/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        mvc.perform(post("/admin/reward/recon/batches/4/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"fulfillmentRef\":\"\",\"amountFen\":100}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(recon);
    }

    @Test
    void reviewWithoutRemarkIs400() throws Exception {
        stp.grant(RewardPermissions.RECON_ACTION);
        mvc.perform(post("/admin/reward/recon/items/8/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRMED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(recon);
    }

    @Test
    void batchesHappyPathPassesFilters() throws Exception {
        stp.grant(RewardPermissions.RECON_QUERY);
        when(recon.page(eq("TOKEN_A"), eq(BILL_DATE), eq("MATCHED"), any()))
                .thenReturn(new PageData<>(1, List.of(batch(4L, "MATCHED"))));
        mvc.perform(get("/admin/reward/recon/batches")
                        .param("categoryCode", "TOKEN_A")
                        .param("billDate", "2026-10-12")
                        .param("status", "MATCHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(4))
                .andExpect(jsonPath("$.data.records[0].categoryCode").value("TOKEN_A"));
    }

    @Test
    void createImportMatchHappyPath() throws Exception {
        stp.grant(RewardPermissions.RECON_IMPORT, RewardPermissions.RECON_MATCH);
        when(recon.create(any())).thenReturn(batch(4L, "CREATED"));
        mvc.perform(post("/admin/reward/recon/batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryCode\":\"TOKEN_A\",\"billDate\":\"2026-10-12\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(4))
                .andExpect(jsonPath("$.data.status").value("CREATED"));
        ArgumentCaptor<ReconBatchCreateCommand> create = ArgumentCaptor.forClass(ReconBatchCreateCommand.class);
        verify(recon).create(create.capture());
        assertThat(create.getValue().billDate()).isEqualTo(BILL_DATE);

        mvc.perform(post("/admin/reward/recon/batches/4/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"fulfillmentRef\":\"F-1\",\"amountFen\":100}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(true));
        ArgumentCaptor<ReconImportCommand> lines = ArgumentCaptor.forClass(ReconImportCommand.class);
        verify(recon).importLines(eq(4L), lines.capture());
        assertThat(lines.getValue().lines()).hasSize(1);

        when(recon.match(4L)).thenReturn(new ReconMatchResponse(2, 2, 1, 1, 1, 0));
        mvc.perform(post("/admin/reward/recon/batches/4/match"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchedCount").value(1))
                .andExpect(jsonPath("$.data.platformOnly").value(1));
    }

    @Test
    void reviewAndActionHappyPath() throws Exception {
        stp.grant(RewardPermissions.RECON_ACTION);
        when(recon.review(eq(8L), any())).thenReturn(new ReconReviewResponse("CONFIRMED"));
        mvc.perform(post("/admin/reward/recon/items/8/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"CONFIRMED\",\"remark\":\"渠道已到账\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("CONFIRMED"));

        when(recon.action(eq(8L), any())).thenReturn(new ReconActionResponse("REISSUE", "G-99"));
        mvc.perform(post("/admin/reward/recon/items/8/action")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REISSUE\",\"reason\":\"补发\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("REISSUE"))
                .andExpect(jsonPath("$.data.actionRef").value("G-99"));
        ArgumentCaptor<ReconActionCommand> action = ArgumentCaptor.forClass(ReconActionCommand.class);
        verify(recon).action(eq(8L), action.capture());
        assertThat(action.getValue().reason()).isEqualTo("补发");
    }

    private static ReconBatchResponse batch(long id, String status) {
        return new ReconBatchResponse(
                id, "TOKEN_A", BILL_DATE, status, 0, 0, 0, 0, 0, 0, Instant.parse("2026-10-13T00:00:00Z"));
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
