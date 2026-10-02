package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.mockConstruction;

import java.util.stream.Stream;

import com.example.BegaProjectApplication;
import com.example.ai.scheduler.AiIngestScheduler;
import com.example.ai.service.AiIngestOrchestrationService;
import com.example.auth.config.DevDataInitializer;
import com.example.auth.scheduler.AccountSecurityScheduler;
import com.example.cheerboard.scheduler.CheerBattleScheduler;
import com.example.cheerboard.scheduler.CheerStorageScheduler;
import com.example.cheerboard.service.PostSyncScheduler;
import com.example.common.realtime.RealtimeOutboxSchedulingConfig;
import com.example.mate.scheduler.PartyLifecycleScheduler;
import com.example.mate.scheduler.PaymentCompensationScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedConstruction;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.env.Profiles;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ReadOnlyStartupSchedulingBoundaryTest {

    private static final String READ_ONLY_PROFILE = "local-readonly-verification";

    @ParameterizedTest(name = "readonly excludes {0} before construction")
    @MethodSource("startupComponents")
    void readonlyProfileExcludesStartupComponentsBeforeConstruction(Class<?> component) {
        assertAll(
                () -> assertThat(profileAllows(component, READ_ONLY_PROFILE))
                        .as("%s with readonly alone", component.getSimpleName()).isFalse(),
                () -> assertThat(profileAllows(component, "dev", READ_ONLY_PROFILE))
                        .as("%s with dev and readonly", component.getSimpleName()).isFalse());
    }

    @ParameterizedTest(name = "normal profiles retain {0}")
    @MethodSource("startupComponents")
    void normalProfilesKeepStartupComponentsEligible(Class<?> component) {
        assertAll(
                () -> assertThat(profileAllows(component)).isTrue(),
                () -> assertThat(profileAllows(component, "dev")).isTrue(),
                () -> assertThat(profileAllows(component, "prod")).isTrue());
    }

    @ParameterizedTest
    @ValueSource(strings = { "dev", "dev-adb", "local" })
    void readonlyProfileExcludesDevSeedEvenWithAnEligibleCompanionProfile(String companionProfile) {
        assertThat(profileAllows(DevDataInitializer.class, companionProfile)).isTrue();
        assertThat(profileAllows(DevDataInitializer.class, companionProfile, READ_ONLY_PROFILE)).isFalse();
    }

    @Test
    void applicationDoesNotUnconditionallyEnableScheduling() {
        assertThat(AnnotatedElementUtils.hasAnnotation(BegaProjectApplication.class, EnableScheduling.class))
                .as("EnableScheduling must be owned by a configuration excluded from readonly verification")
                .isFalse();
    }

    @Test
    void readonlyProfileHasNoEligibleSchedulingEnabler() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev", READ_ONLY_PROFILE);
        ClassPathScanningCandidateComponentProvider scanner = schedulingScanner(environment);

        // Metadata scanning only: do not create the application, repositories, or scheduled targets.
        assertThat(scanner.findCandidateComponents("com.example")).isEmpty();
    }

    @Test
    void normalProfileKeepsSchedulingEnabled() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");

        assertThat(schedulingScanner(environment).findCandidateComponents("com.example")).isNotEmpty();
    }

    @Test
    void readonlyProfileDoesNotConstructOutboxThreadPoolEvenWhenOutboxFlagIsTrue() {
        try (MockedConstruction<ThreadPoolTaskScheduler> schedulers = mockConstruction(ThreadPoolTaskScheduler.class)) {
            new ApplicationContextRunner()
                    .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", READ_ONLY_PROFILE))
                    .withUserConfiguration(RealtimeOutboxSchedulingConfig.class)
                    .withPropertyValues("app.realtime.outbox.enabled=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertAll(
                                () -> assertThat(context).doesNotHaveBean("realtimeOutboxTaskScheduler"),
                                () -> assertThat(context).doesNotHaveBean(ThreadPoolTaskScheduler.class),
                                () -> assertThat(schedulers.constructed()).isEmpty());
                    });
        }
    }

    private static Stream<Class<?>> startupComponents() {
        return Stream.of(
                AccountSecurityScheduler.class,
                PartyLifecycleScheduler.class,
                PaymentCompensationScheduler.class,
                PostSyncScheduler.class,
                CheerStorageScheduler.class,
                CheerBattleScheduler.class,
                AiIngestScheduler.class,
                AiIngestOrchestrationService.class);
    }

    private static boolean profileAllows(Class<?> component, String... activeProfiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(activeProfiles);
        Profile profile = AnnotatedElementUtils.findMergedAnnotation(component, Profile.class);
        return profile == null || environment.acceptsProfiles(Profiles.of(profile.value()));
    }

    private static ClassPathScanningCandidateComponentProvider schedulingScanner(MockEnvironment environment) {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false, environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(EnableScheduling.class));
        return scanner;
    }
}
