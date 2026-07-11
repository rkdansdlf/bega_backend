package com.example.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class BackendBoundaryArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.example");

    @Test
    void commonRateLimitMustNotDependOnAuthImplementations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.example.common.ratelimit..")
                .should().dependOnClassesThat().resideInAPackage("com.example.auth..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void ticketAnalysisServiceMustNotOwnAiTransport() {
        ArchRule rule = noClasses()
                .that().haveFullyQualifiedName("com.example.kbo.service.TicketAnalysisService")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.example.ai.config..",
                        "org.springframework.web.reactive.function.client..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void stadiumAdminControllerMustNotDependOnPersistence() {
        ArchRule rule = noClasses()
                .that().haveFullyQualifiedName("com.example.stadium.controller.StadiumAdminController")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.example.stadium.repository..",
                        "com.example.stadium.entity..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void reissueControllerMustNotDependOnPersistence() {
        ArchRule rule = noClasses()
                .that().haveFullyQualifiedName("com.example.auth.controller.ReissueController")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.example.auth.repository..",
                        "com.example.auth.entity..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void seatViewClassificationServiceMustNotOwnAiTransport() {
        ArchRule rule = noClasses()
                .that().haveFullyQualifiedName(
                        "com.example.BegaDiary.Service.SeatViewClassificationService")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.example.ai.config..",
                        "org.springframework.web.reactive.function.client..");

        rule.check(PRODUCTION_CLASSES);
    }

    @Test
    void moderationServiceMustNotOwnAiTransport() {
        ArchRule rule = noClasses()
                .that().haveFullyQualifiedName("com.example.common.service.AIModerationService")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.example.ai.config..",
                        "org.springframework.web.client..");

        rule.check(PRODUCTION_CLASSES);
    }
}
