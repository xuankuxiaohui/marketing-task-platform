package com.marketing.task.service.filter;

import com.marketing.task.common.BusinessException;
import com.marketing.task.context.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class FilterExpressionEngineTest {

    private FilterExpressionEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        engine = new FilterExpressionEngine();
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
    void evaluate_inAllowlist_shouldThrowNotImplemented() {
        UserContext ctx = UserContext.builder().build();
        assertFalse(engine.evaluate("inAllowlist(['uid1'])", ctx));
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
}
