package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.mkt.admin.arch.ProductionClasses;
import com.mkt.kernel.audit.Audited;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

/** Mechanical @Audited surface: non-GET /admin/** only (design §6.5 / 05-security §3). */
class AuditedPlacementArchTest {

    @Test
    void auditedIsNeverOnGet() {
        noMethods()
                .that()
                .areAnnotatedWith(Audited.class)
                .should()
                .beAnnotatedWith(GetMapping.class)
                .check(ProductionClasses.load());
    }

    @Test
    void portalAndInternalMustNotBeAudited() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAnyPackage("..controller.portal..", "..controller.internal..")
                .should()
                .beAnnotatedWith(Audited.class)
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }

    @Test
    void adminWritesAreAudited() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..controller.admin..")
                .and()
                .areAnnotatedWith(PostMapping.class)
                .should()
                .beAnnotatedWith(Audited.class)
                .check(ProductionClasses.load());
        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..controller.admin..")
                .and()
                .areAnnotatedWith(PutMapping.class)
                .should()
                .beAnnotatedWith(Audited.class)
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..controller.admin..")
                .and()
                .areAnnotatedWith(DeleteMapping.class)
                .should()
                .beAnnotatedWith(Audited.class)
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
        methods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..controller.admin..")
                .and()
                .areAnnotatedWith(PatchMapping.class)
                .should()
                .beAnnotatedWith(Audited.class)
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }
}
