package com.mkt.admin;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.admin.arch.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** RL-01 / RL-02 / RL-03 (design §2.8 / §7.6). */
class ArchLayerRuleTest {

    private static final String[] DOMAINS = {
        "domain-identity", "domain-task", "domain-reward", "domain-risk", "domain-tracking"
    };

    private static final String[] DOMAIN_PACKAGES = {
        "com.mkt.identity..", "com.mkt.task..", "com.mkt.reward..", "com.mkt.risk..", "com.mkt.tracking.."
    };

    @Test
    void rl01_layersAreUnidirectional() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .withOptionalLayers(true)
                .layer("App")
                .definedBy("com.mkt.admin..", "com.mkt.portal..")
                .layer("Domain")
                .definedBy(DOMAIN_PACKAGES)
                .layer("Infra")
                .definedBy("com.mkt.infra..", "com.mkt.db..")
                .layer("Contract")
                .definedBy("com.mkt.contract..")
                .layer("Kernel")
                .definedBy("com.mkt.kernel..")
                .whereLayer("App")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Domain")
                .mayOnlyBeAccessedByLayers("App")
                .whereLayer("Infra")
                .mayOnlyBeAccessedByLayers("App", "Domain")
                .whereLayer("Contract")
                .mayOnlyBeAccessedByLayers("App", "Domain")
                .whereLayer("Kernel")
                .mayOnlyBeAccessedByLayers("App", "Domain", "Infra", "Contract")
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }

    @Test
    void rl02_domainsDoNotImportEachOther() {
        JavaClasses classes = ProductionClasses.load();
        for (int i = 0; i < DOMAIN_PACKAGES.length; i++) {
            String self = DOMAIN_PACKAGES[i];
            String[] others = othersOf(i);
            noDependOn(self, others).allowEmptyShould(true).check(classes);
        }
    }

    @Test
    void rl02_domainPomsHaveNoSiblingDomainDependencies() throws IOException {
        Path serverRoot = serverRoot();
        for (String domain : DOMAINS) {
            String pom = Files.readString(serverRoot.resolve(domain).resolve("pom.xml"));
            for (String other : DOMAINS) {
                if (!other.equals(domain)) {
                    assertThat(pom)
                            .as("%s must not depend on %s", domain, other)
                            .doesNotContain("<artifactId>" + other + "</artifactId>");
                }
            }
        }
    }

    @Test
    void rl03_noCrossDomainMapperEntityService() {
        JavaClasses classes = ProductionClasses.load();
        String[] forbidden = {
            "com.mkt.identity.mapper..",
            "com.mkt.identity.entity..",
            "com.mkt.identity.application..",
            "com.mkt.identity.domain..",
            "com.mkt.task.mapper..",
            "com.mkt.task.entity..",
            "com.mkt.task.application..",
            "com.mkt.task.domain..",
            "com.mkt.reward.mapper..",
            "com.mkt.reward.entity..",
            "com.mkt.reward.application..",
            "com.mkt.reward.domain..",
            "com.mkt.risk.mapper..",
            "com.mkt.risk.entity..",
            "com.mkt.risk.application..",
            "com.mkt.risk.domain..",
            "com.mkt.tracking.mapper..",
            "com.mkt.tracking.entity..",
            "com.mkt.tracking.application..",
            "com.mkt.tracking.domain.."
        };
        for (String domain : DOMAIN_PACKAGES) {
            String prefix = domain.substring(0, domain.length() - 2);
            String[] targets = Arrays.stream(forbidden)
                    .filter(pkg -> !pkg.startsWith(prefix))
                    .toArray(String[]::new);
            noDependOn(domain, targets).allowEmptyShould(true).check(classes);
        }
    }

    @Test
    void illegalReverseDependencyFailsAndCompliantPasses() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.layers");
        ArchRule noKernelToApp = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("..fixture.layers.illegal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.mkt.admin");
        assertThatThrownBy(() -> noKernelToApp.check(fixtures)).isInstanceOf(AssertionError.class);

        ArchRule legalStaysClean = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("..fixture.layers.legal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("com.mkt.admin");
        legalStaysClean.check(fixtures);
    }

    @Test
    void rl03_taskSourcesDoNotNameForeignTables() throws IOException {
        Path root = serverRoot().resolve("domain-task/src/main");
        List<String> hits = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            walk.filter(path -> {
                        String name = path.toString();
                        return name.endsWith(".java") || name.endsWith(".xml");
                    })
                    .forEach(path -> {
                        try {
                            String text = Files.readString(path);
                            if (text.contains("rwd_") || text.contains("pnt_") || text.contains("sys_portal_user")) {
                                hits.add(root.relativize(path).toString());
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                    });
        }
        assertThat(hits).isEmpty();
    }

    @Test
    void illegalCrossDomainEntityAccessFails() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.domains");
        ArchRule noTaskToIdentityEntity = ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage("..fixture.domains.task..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..fixture.domains.identity.entity..");
        assertThatThrownBy(() -> noTaskToIdentityEntity.check(fixtures)).isInstanceOf(AssertionError.class);
    }

    private static ArchRule noDependOn(String origin, String[] targets) {
        return ArchRuleDefinition.noClasses()
                .that()
                .resideInAPackage(origin)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(targets);
    }

    private static String[] othersOf(int index) {
        String[] others = new String[DOMAIN_PACKAGES.length - 1];
        int w = 0;
        for (int i = 0; i < DOMAIN_PACKAGES.length; i++) {
            if (i != index) {
                others[w++] = DOMAIN_PACKAGES[i];
            }
        }
        return others;
    }

    private static Path serverRoot() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path candidate : List.of(cwd, cwd.getParent())) {
            if (candidate != null && Files.isRegularFile(candidate.resolve("admin-app/pom.xml"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("cannot locate server/ from " + cwd);
    }
}
