package com.mkt.admin;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.mkt.admin.arch.ProductionClasses;
import org.junit.jupiter.api.Test;

/**
 * RL-07 skeleton: {@code @Transactional} types must not depend on remote HTTP clients.
 * Empty while no transactional domain code exists; turns red when a tx method calls out.
 */
class ArchTxRemoteCallTest {

    @Test
    void transactionalTypesDoNotDependOnRemoteClients() {
        noClasses()
                .that()
                .areAnnotatedWith("org.springframework.transaction.annotation.Transactional")
                .or()
                .containAnyMethodsThat(annotatedWith("org.springframework.transaction.annotation.Transactional"))
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework.web.client..",
                        "org.springframework.web.reactive.function.client..",
                        "java.net.http..",
                        "okhttp3..")
                .allowEmptyShould(true)
                .check(ProductionClasses.load());
    }
}
