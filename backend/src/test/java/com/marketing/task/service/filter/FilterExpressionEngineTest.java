package com.marketing.task.service.filter;

import com.marketing.common.BusinessException;
import com.marketing.context.UserContext;
import com.marketing.task.service.EventTrackingService;
import com.marketing.task.service.MetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilterExpressionEngineTest {

    @Mock
    private ListDataService listDataService;
    @Mock
    private EventTrackingService eventTrackingService;
    @Mock
    private MetricsService metricsService;
    @Mock
    private GrayService grayService;

    private FilterExpressionEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = new FilterExpressionEngine(listDataService, eventTrackingService, metricsService, grayService);
    }

    @Test
    void validate_shouldPassValidExpression() {
        engine.validate("inProvince(['BJ'])");
    }

    @Test
    void validate_shouldRejectEmpty() {
        assertThrows(BusinessException.class, () -> engine.validate(null));
        assertThrows(BusinessException.class, () -> engine.validate(""));
        assertThrows(BusinessException.class, () -> engine.validate("  "));
    }

    @Test
    void validate_shouldRejectTooLong() {
        String longExpr = "a".repeat(1025);
        assertThrows(BusinessException.class, () -> engine.validate(longExpr));
    }

    @Test
    void validate_shouldRejectForbiddenKeyword() {
        assertThrows(BusinessException.class, () -> engine.validate("System.exit(0)"));
        assertThrows(BusinessException.class, () -> engine.validate("Runtime.getRuntime()"));
        assertThrows(BusinessException.class, () -> engine.validate("Class.forName('xxx')"));
        assertThrows(BusinessException.class, () -> engine.validate("import java.io"));
        assertThrows(BusinessException.class, () -> engine.validate("exec('rm')"));
        assertThrows(BusinessException.class, () -> engine.validate("eval('1+1')"));
    }

    @Test
    void evaluate_inProvince_shouldMatch() {
        UserContext ctx = UserContext.builder().province("BJ").build();
        assertTrue(engine.evaluate("inProvince(['BJ'])", ctx));
    }

    @Test
    void evaluate_inProvince_shouldNotMatch() {
        UserContext ctx = UserContext.builder().province("SH").build();
        assertFalse(engine.evaluate("inProvince(['BJ'])", ctx));
    }

    @Test
    void evaluate_hasTag_shouldMatch() {
        UserContext ctx = UserContext.builder().tags(Set.of("vip")).build();
        assertTrue(engine.evaluate("hasTag('vip')", ctx));
    }

    @Test
    void evaluate_hasTag_shouldNotMatch() {
        UserContext ctx = UserContext.builder().tags(Set.of("normal")).build();
        assertFalse(engine.evaluate("hasTag('vip')", ctx));
    }

    @Test
    void evaluate_roleEquals_shouldMatch() {
        UserContext ctx = UserContext.builder().role("admin").build();
        assertTrue(engine.evaluate("roleEquals('admin')", ctx));
    }

    @Test
    void evaluate_levelGte_shouldMatch() {
        UserContext ctx = UserContext.builder().level(10).build();
        assertTrue(engine.evaluate("levelGte(5)", ctx));
    }

    @Test
    void evaluate_levelGte_shouldNotMatch() {
        UserContext ctx = UserContext.builder().level(3).build();
        assertFalse(engine.evaluate("levelGte(5)", ctx));
    }

    @Test
    void evaluate_inAllowlist_shouldReturnTrueWhenInList() {
        UserContext ctx = UserContext.builder().userId("u1").build();
        when(listDataService.isInList("ALLOWLIST", "vip_users", "u1")).thenReturn(true);
        assertTrue(engine.evaluate("inAllowlist('vip_users')", ctx));
    }

    @Test
    void evaluate_inAllowlist_shouldReturnFalseWhenNotInList() {
        UserContext ctx = UserContext.builder().userId("u2").build();
        when(listDataService.isInList("ALLOWLIST", "vip_users", "u2")).thenReturn(false);
        assertFalse(engine.evaluate("inAllowlist('vip_users')", ctx));
    }

    @Test
    void evaluate_notInDenylist_shouldReturnTrueWhenNotInBlacklist() {
        UserContext ctx = UserContext.builder().userId("u1").build();
        when(listDataService.isInList("DENYLIST", "blocked", "u1")).thenReturn(false);
        assertTrue(engine.evaluate("notInDenylist('blocked')", ctx));
    }

    @Test
    void evaluate_notInDenylist_shouldReturnFalseWhenInBlacklist() {
        UserContext ctx = UserContext.builder().userId("u3").build();
        when(listDataService.isInList("DENYLIST", "blocked", "u3")).thenReturn(true);
        assertFalse(engine.evaluate("notInDenylist('blocked')", ctx));
    }

    @Test
    void evaluate_combinedFilters_shouldAllMatch() {
        UserContext ctx = UserContext.builder()
                .province("BJ")
                .level(10)
                .tags(Set.of("vip"))
                .build();
        assertTrue(engine.evaluate("inProvince(['BJ','SH']) && levelGte(5) && hasTag('vip')", ctx));
    }

    @Test
    void evaluate_combinedFilters_shouldFailOnOneMismatch() {
        UserContext ctx = UserContext.builder()
                .province("GZ")
                .level(10)
                .tags(Set.of("vip"))
                .build();
        assertFalse(engine.evaluate("inProvince(['BJ','SH']) && levelGte(5) && hasTag('vip')", ctx));
    }

    @Test
    void evaluate_inGrayPercent_shouldReturnTrueWhenInGray() {
        UserContext ctx = UserContext.builder().userId("u1").build();
        engine.setTaskGrayConfig(1L, "PERCENTAGE", "{\"percent\":10}");
        when(grayService.isInGray("u1", 1L, "PERCENTAGE", "{\"percent\":10}")).thenReturn(true);
        assertTrue(engine.evaluate("inGrayPercent(10)", ctx));
        engine.clearTaskGrayConfig();
    }

    @Test
    void evaluate_inABGroup_shouldReturnTrueWhenInGroup() {
        UserContext ctx = UserContext.builder().userId("u1").build();
        String grayConfig = "{\"groups\":[{\"name\":\"A\",\"percent\":50},{\"name\":\"B\",\"percent\":50}]}";
        engine.setTaskGrayConfig(1L, "AB", grayConfig);
        when(grayService.getABGroup("u1", 1L, grayConfig)).thenReturn("A");
        assertTrue(engine.evaluate("inABGroup('A')", ctx));
        engine.clearTaskGrayConfig();
    }

    @Test
    void evaluate_inCrowd_shouldReturnTrueWhenInCrowd() {
        UserContext ctx = UserContext.builder().userId("u1").build();
        when(grayService.isInGray("u1", null, "CROWD", "{\"crowdIds\":[1]}")).thenReturn(true);
        assertTrue(engine.evaluate("inCrowd(1)", ctx));
    }
}
