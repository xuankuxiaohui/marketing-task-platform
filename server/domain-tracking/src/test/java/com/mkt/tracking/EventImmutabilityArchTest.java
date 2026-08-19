package com.mkt.tracking;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * R28.2 / RL-12: {@code evt_event_log} mappers only insert|select; no update/delete service methods
 * or HTTP endpoints.
 */
class EventImmutabilityArchTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.mkt.tracking");

    @Test
    void eventLogMapperMethodsAreInsertOrSelect() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Mapper")
                .and()
                .areDeclaredInClassesThat()
                .haveSimpleNameContaining("EventLog")
                .should()
                .haveNameNotMatching("^(insert|select).*")
                .allowEmptyShould(true)
                .check(CLASSES);
    }

    @Test
    void eventLogStoreHasNoUpdateOrDelete() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameContaining("EventLogStore")
                .should()
                .haveNameMatching(".*(update|delete|remove).*")
                .check(CLASSES);
    }

    @Test
    void noEventLogWriteEndpointsExceptBatchPost() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .resideInAPackage("..controller.portal..")
                .should()
                .beAnnotatedWith("org.springframework.web.bind.annotation.DeleteMapping")
                .orShould()
                .beAnnotatedWith("org.springframework.web.bind.annotation.PutMapping")
                .orShould()
                .beAnnotatedWith("org.springframework.web.bind.annotation.PatchMapping")
                .check(CLASSES);
    }
}
