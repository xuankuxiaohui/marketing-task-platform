package com.mkt.admin;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.mkt.admin.arch.ProductionClasses;
import com.tngtech.archunit.core.domain.JavaCall;
import org.junit.jupiter.api.Test;

/**
 * RL-11 skeleton: SysConfigMapper only from identity.config; ObjectMapper only from kernel JsonUtil.
 */
class ArchConfigAccessTest {

    @Test
    void sysConfigMapperOnlyFromIdentityConfig() {
        noClasses()
                .that()
                .resideOutsideOfPackage("com.mkt.identity.config..")
                .should()
                .dependOnClassesThat()
                .haveSimpleName("SysConfigMapper")
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }

    @Test
    void objectMapperCtorOnlyInJsonUtil() {
        noClasses()
                .that()
                .doNotHaveFullyQualifiedName("com.mkt.kernel.json.JsonUtil")
                .should()
                .callConstructorWhere(JavaCall.Predicates.target(
                        owner(assignableTo("tools.jackson.databind.ObjectMapper"))))
                .orShould()
                .callConstructorWhere(JavaCall.Predicates.target(
                        owner(assignableTo("com.fasterxml.jackson.databind.ObjectMapper"))))
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }
}
