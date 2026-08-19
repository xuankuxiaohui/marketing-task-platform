package com.mkt.admin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.mkt.admin.arch.ProductionClasses;
import org.junit.jupiter.api.Test;

/**
 * RL-12 skeleton: audit / event / hit / snapshot mappers only insert|select.
 * Empty until those mappers exist (task 13+).
 */
class EventImmutabilityArchTest {

    @Test
    void immutableTableMappersOnlyInsertOrSelect() {
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .haveSimpleNameEndingWith("Mapper")
                .and()
                .areDeclaredInClassesThat()
                .haveNameMatching(".*(AuditLog|EventLog|HitLog|VersionSnapshot).*")
                .should()
                .haveNameNotMatching("^(insert|select).*")
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }
}
