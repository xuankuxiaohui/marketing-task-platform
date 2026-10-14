package com.mkt.spike.misc;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArchUnitSmokeTest {

    private static final ArchRule RULE = noClasses()
            .that().resideInAPackage("..bad..")
            .should().dependOnClassesThat().resideInAPackage("..ok..");

    @Test
    void illegalClassFailsRule() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.mkt.spike.misc.bad");
        assertThrows(AssertionError.class, () -> RULE.check(classes));
    }

    @Test
    void legalClassPassesRule() {
        JavaClasses classes = new ClassFileImporter().importPackages("com.mkt.spike.misc");
        assertDoesNotThrow(() -> RULE.allowEmptyShould(true).check(
                new ClassFileImporter().importPackages("com.mkt.spike.misc.ok")));
        assertThrows(AssertionError.class, () -> RULE.check(classes));
    }
}
