package com.mkt.risk.controller.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mkt.contract.RiskAction;
import com.mkt.kernel.BusinessException;
import com.mkt.kernel.CommonErrorCodes;
import com.mkt.kernel.web.GlobalExceptionHandler;
import com.mkt.risk.application.RiskRuleAppService;
import com.mkt.risk.command.RiskRuleUpdateCommand;
import com.mkt.risk.response.RiskRuleResponse;
import com.mkt.risk.support.RiskListPermissions;
import com.mkt.risk.support.RiskPermissionGuard;
import com.mkt.risk.testsupport.RecordingRiskPermissionGuard;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * WebMvc slice (standalone MockMvc) for {@link RiskRuleAdminController}: permission enforced via
 * {@link RiskPermissionGuard} (same pattern as {@link RiskCaseAdminControllerTest}).
 */
class RiskRuleAdminControllerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T00:00:00Z");

    private final RiskRuleAppService appService = mock(RiskRuleAppService.class);
    private final RecordingRiskPermissionGuard permissions = new RecordingRiskPermissionGuard();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = build(permissions);
    }

    private MockMvc build(RiskPermissionGuard guard) {
        return MockMvcBuilders.standaloneSetup(new RiskRuleAdminController(appService, guard))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void listAndUpdateWithoutPermissionAre403() throws Exception {
        RiskPermissionGuard deny = permission -> {
            throw new BusinessException(CommonErrorCodes.PERMISSION_DENIED);
        };
        MockMvc denied = build(deny);
        denied.perform(get("/admin/risk/rules"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        denied.perform(put("/admin/risk/rules/R-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"threshold\":5,\"windowSeconds\":60,\"action\":\"REJECT\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("common.permission-denied"));
        verifyNoInteractions(appService);
    }

    @Test
    void updateWithMissingThresholdIs400() throws Exception {
        mvc.perform(put("/admin/risk/rules/R-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"action\":\"REJECT\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(appService);
    }

    @Test
    void updateWithUnknownActionIs400() throws Exception {
        mvc.perform(put("/admin/risk/rules/R-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"threshold\":5,\"action\":\"FREEZE_ALL\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("common.param-invalid"));
        verifyNoInteractions(appService);
    }

    @Test
    void listHappyPathChecksRuleQuery() throws Exception {
        when(appService.list())
                .thenReturn(List.of(
                        new RiskRuleResponse("R-a", true, 5L, 60L, "REJECT", NOW),
                        new RiskRuleResponse("R-b", false, 3L, null, "MARK", NOW)));
        mvc.perform(get("/admin/risk/rules"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].ruleCode").value("R-a"))
                .andExpect(jsonPath("$.data[1].action").value("MARK"));
        assertThat(permissions.required).containsExactly(RiskListPermissions.RULE_QUERY);
    }

    @Test
    void updateHappyPathChecksRuleConfig() throws Exception {
        when(appService.update(eq("R-a"), any()))
                .thenReturn(new RiskRuleResponse("R-a", true, 8L, 120L, "SILENT_REJECT", NOW));
        mvc.perform(put("/admin/risk/rules/R-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"threshold\":8,\"windowSeconds\":120,"
                                + "\"action\":\"SILENT_REJECT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ruleCode").value("R-a"))
                .andExpect(jsonPath("$.data.threshold").value(8))
                .andExpect(jsonPath("$.data.action").value("SILENT_REJECT"));
        ArgumentCaptor<RiskRuleUpdateCommand> command = ArgumentCaptor.forClass(RiskRuleUpdateCommand.class);
        verify(appService).update(eq("R-a"), command.capture());
        assertThat(command.getValue().action()).isEqualTo(RiskAction.SILENT_REJECT);
        assertThat(permissions.required).containsExactly(RiskListPermissions.RULE_CONFIG);
    }
}
