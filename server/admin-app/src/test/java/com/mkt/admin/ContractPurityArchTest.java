package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mkt.admin.arch.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** RL-06: platform-contract is JDK + kernel only (design §2.2.2). */
class ContractPurityArchTest {

    private static final ArchRule NO_SPRING_JDBC_REDIS = noClasses()
            .that()
            .resideInAPackage("com.mkt.contract..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "java.sql..",
                    "javax.sql..",
                    "jakarta.persistence..",
                    "org.mybatis..",
                    "com.baomidou..",
                    "org.redisson..",
                    "org.springframework.data.redis..");

    @Test
    void contractProductionTypesStayPure() {
        NO_SPRING_JDBC_REDIS.check(ProductionClasses.load());
    }

    @Test
    void contractPomHasNoSpringJdbcRedis() throws IOException {
        Path pom = serverRoot().resolve("platform-contract").resolve("pom.xml");
        String xml = Files.readString(pom);
        assertThat(xml).contains("<artifactId>platform-kernel</artifactId>");
        assertThat(xml)
                .doesNotContain("<artifactId>spring-boot-starter-web</artifactId>")
                .doesNotContain("<artifactId>mybatis-plus-spring-boot4-starter</artifactId>")
                .doesNotContain("<artifactId>redisson</artifactId>")
                .doesNotContain("<artifactId>redisson-spring-boot-starter</artifactId>");
    }

    @Test
    void illegalSpringImportFailsAndLegalPortPasses() {
        JavaClasses illegal =
                new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.contract.illegal");
        ArchRule fixtureRule = noClasses()
                .that()
                .resideInAPackage("..fixture.contract.illegal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..");
        assertThatThrownBy(() -> fixtureRule.check(illegal)).isInstanceOf(AssertionError.class);

        JavaClasses legal = new ClassFileImporter().importPackages("com.mkt.admin.arch.fixture.contract.legal");
        noClasses()
                .that()
                .resideInAPackage("..fixture.contract.legal..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("org.springframework..")
                .check(legal);
    }

    private static Path serverRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        if (cwd.getFileName().toString().equals("admin-app")) {
            return cwd.getParent();
        }
        return cwd.resolve("server");
    }
}
