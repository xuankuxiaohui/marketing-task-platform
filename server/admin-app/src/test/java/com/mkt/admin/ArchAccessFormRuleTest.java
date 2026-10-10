package com.mkt.admin;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.Architectures.LayeredArchitecture;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * F13: architecture access-form coverage for RL-01 / RL-02 / RL-03.
 *
 * <p>Production code depends via constructor injection ({@code private final} field types + ctor
 * params), method calls, occasional inheritance, generics, and annotations. Rules must use {@code
 * dependOnClassesThat()} (full dependency set), not {@code accessClassesThat()} (field get/set +
 * calls only). Fixtures prove each form fails under {@code dependOnClassesThat} and that field-type
 * / implements / return-type / annotation are invisible to {@code accessClassesThat}
 * (extends still surfaces via implicit {@code super()} ctor call).
 *
 * <p>ArchUnit 1.5.0. RL-02/RL-03 helpers keep the default full dependency set (no limited
 * DependencyType filter). RL-01 uses {@code consideringOnlyDependenciesInAnyPackage("com.mkt..")}
 * so non-layer packages under {@code com.mkt} cannot conceal cross-layer edges.
 */
class ArchAccessFormRuleTest {

    private static final String ILLEGAL = "..fixture.accessforms.illegal..";
    private static final String TARGET = "..fixture.accessforms.target..";
    private static final String LEGAL = "..fixture.accessforms.legal..";

    private static JavaClasses ACCESS_FORM_CLASSES;
    private static JavaClasses OUTSIDE_BRIDGE_CLASSES;

    @BeforeAll
    static void importFixtures() {
        ACCESS_FORM_CLASSES =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.accessforms");
        OUTSIDE_BRIDGE_CLASSES = new ClassFileImporter()
                .importClasses(
                        com.mkt.admin.arch.fixture.accessforms.illegal.DependsViaOutsideBridge.class,
                        com.mkt.admin.arch.fixture.accessforms.outside.ShadyBridge.class,
                        com.mkt.admin.arch.fixture.accessforms.target.ForbiddenType.class);
    }

    static Stream<String> illegalSimpleNames() {
        return Stream.of(
                "DependsViaFieldType",
                "DependsViaCtorParam",
                "DependsViaMethodCall",
                "DependsViaExtends",
                "DependsViaImplements",
                "DependsViaGenericField",
                "DependsViaAnnotation",
                "DependsViaReturnType");
    }

    private static ArchRule dependOnTargetFrom(String originSimpleName) {
        return ArchRuleDefinition.noClasses()
                .that()
                .haveSimpleName(originSimpleName)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(TARGET);
    }

    private static ArchRule accessTargetFrom(String originSimpleName) {
        return ArchRuleDefinition.noClasses()
                .that()
                .haveSimpleName(originSimpleName)
                .should()
                .accessClassesThat()
                .resideInAPackage(TARGET);
    }

    @ParameterizedTest
    @MethodSource("illegalSimpleNames")
    void dependOnClassesThat_catchesEachAccessForm(String simpleName) {
        assertThatThrownBy(() -> dependOnTargetFrom(simpleName).check(ACCESS_FORM_CLASSES))
                .as("dependOnClassesThat must fail for %s", simpleName)
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void accessClassesThat_missesDeclaredTypeForms() {
        // Declared field/return types, implements, and annotations are dependencies but not
        // "accesses". (extends still shows up via implicit super() ctor call — covered separately.)
        assertThatCode(() -> accessTargetFrom("DependsViaFieldType")
                        .allowEmptyShould(true)
                        .check(ACCESS_FORM_CLASSES))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessTargetFrom("DependsViaImplements")
                        .allowEmptyShould(true)
                        .check(ACCESS_FORM_CLASSES))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessTargetFrom("DependsViaReturnType")
                        .allowEmptyShould(true)
                        .check(ACCESS_FORM_CLASSES))
                .doesNotThrowAnyException();
        assertThatCode(() -> accessTargetFrom("DependsViaAnnotation")
                        .allowEmptyShould(true)
                        .check(ACCESS_FORM_CLASSES))
                .doesNotThrowAnyException();
    }

    @Test
    void accessClassesThat_stillSeesMethodCall() {
        assertThatThrownBy(() -> accessTargetFrom("DependsViaMethodCall").check(ACCESS_FORM_CLASSES))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void legalFixtureHasNoForbiddenDependency() {
        ArchRule clean = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(LEGAL)
                .should()
                .dependOnClassesThat()
                .resideInAPackage(TARGET);
        clean.check(ACCESS_FORM_CLASSES);
    }

    @Test
    void consideringOnlyDependenciesInLayers_missesOutsidePackageBridge() {
        LayeredArchitecture inLayersOnly =
                outsideBridgeLayers(layeredArchitecture().consideringOnlyDependenciesInLayers());
        EvaluationResult result = inLayersOnly.evaluate(OUTSIDE_BRIDGE_CLASSES);
        assertThat(result.hasViolation())
                .as("in-layers-only ignores Service→outside and outside→Controller edges")
                .isFalse();
    }

    @Test
    void consideringOnlyDependenciesInAnyPackage_catchesOutsidePackageBridge() {
        LayeredArchitecture scoped = outsideBridgeLayers(layeredArchitecture()
                .consideringOnlyDependenciesInAnyPackage(
                        "com.mkt.admin.arch.fixture.accessforms.."));
        assertThatThrownBy(() -> scoped.check(OUTSIDE_BRIDGE_CLASSES))
                .as("package-scoped settings see outside→Controller (Controller not only accessed by Service)")
                .isInstanceOf(AssertionError.class);
    }

    /**
     * Mirrors ArchUnit's documented blind spot: Service → non-layer util → Controller. Controller
     * may only be accessed by Service; the non-layer bridge conceals the path when only in-layer
     * edges are considered.
     */
    private static LayeredArchitecture outsideBridgeLayers(LayeredArchitecture base) {
        return base.withOptionalLayers(true)
                .layer("Service")
                .definedBy("..fixture.accessforms.illegal..")
                .layer("Controller")
                .definedBy(TARGET)
                .whereLayer("Controller")
                .mayOnlyBeAccessedByLayers("Service");
    }
}
