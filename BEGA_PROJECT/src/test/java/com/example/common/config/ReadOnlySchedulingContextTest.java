package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.example.ai.service.CoachAutoBriefMonitoringService;
import com.example.common.clienterror.ClientErrorAlertingService;
import com.example.common.realtime.RealtimeOutboxRelay;
import com.example.common.realtime.RealtimeOutboxSchedulingConfig;
import com.example.homepage.HomeBootstrapWarmupService;
import com.example.kbo.repository.TicketVerificationRepository;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.leaderboard.scheduler.GameResultScheduler;
import com.example.mate.service.PayoutService;
import com.example.media.service.MediaCleanupScheduler;
import com.example.prediction.PredictionWarmupService;
import com.example.prediction.scheduler.RankingPredictionSettlementScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class ReadOnlySchedulingContextTest {

    private static final List<Class<?>> SCHEDULED_TYPES = List.of(
            TicketVerificationTokenStore.class, PayoutService.class, RealtimeOutboxRelay.class,
            ClientErrorAlertingService.class, GameResultScheduler.class, MediaCleanupScheduler.class,
            HomeBootstrapWarmupService.class, PredictionWarmupService.class,
            CoachAutoBriefMonitoringService.class, RankingPredictionSettlementScheduler.class);

    @Test
    void readonlyContextRegistersNoneOfTheFourteenActualScheduledMethods() {
        assertThat(SCHEDULED_TYPES.stream().flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, Scheduled.class)).count()).isEqualTo(14);

        // These are the actual scheduled target types. Mocking skips constructors and all external effects.
        List<Object> targets = new ArrayList<>();
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", ReadOnlyVerificationTestEnvironment.PROFILE))
                .withUserConfiguration(ApplicationSchedulingConfig.class, RealtimeOutboxSchedulingConfig.class)
                // Include the relay's two real scheduled methods even if its feature flag is on.
                // This slice tests the profile boundary; the early guard rejects this flag separately.
                .withPropertyValues("app.realtime.outbox.enabled=true")
                .withBean(TaskScheduler.class, () -> taskScheduler);
        for (Class<?> type : SCHEDULED_TYPES) {
            runner = withMockTarget(runner, type, targets);
        }

        runner.run(context -> {
            assertThat(context).hasNotFailed();
            for (Class<?> type : SCHEDULED_TYPES) {
                assertThat(context).hasSingleBean(type);
            }
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(Object.class), new String[0],
                    context.getSourceApplicationContext(), Duration.ZERO));
            assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
            assertThat(context).doesNotHaveBean(ThreadPoolTaskScheduler.class);
            assertThat(context).doesNotHaveBean("realtimeOutboxTaskScheduler");
            assertThat(context.getBeansOfType(ScheduledAnnotationBeanPostProcessor.class).values().stream()
                    .flatMap(processor -> processor.getScheduledTasks().stream())).isEmpty();
            verifyNoInteractions(taskScheduler);
            verifyNoInteractions(targets.toArray());
        });
    }

    @Test
    void readonlyContextRetainsTicketReadsWithoutSchedulingCleanup() {
        TicketVerificationRepository repository = mock(TicketVerificationRepository.class);
        when(repository.findByTokenAndConsumedFalseAndExpiresAtAfter(eq("fixture-token"), any(Instant.class)))
                .thenReturn(Optional.empty());
        TaskScheduler scheduler = mock(TaskScheduler.class);
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev", ReadOnlyVerificationTestEnvironment.PROFILE))
                .withUserConfiguration(ApplicationSchedulingConfig.class, TicketVerificationTokenStore.class)
                .withBean(TicketVerificationRepository.class, () -> repository)
                .withBean(TaskScheduler.class, () -> scheduler)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(TicketVerificationTokenStore.class);
                    assertThat(context.getBean(TicketVerificationTokenStore.class).peekToken("fixture-token")).isNull();
                    verify(repository).findByTokenAndConsumedFalseAndExpiresAtAfter(eq("fixture-token"), any(Instant.class));
                    verify(repository, never()).deleteExpiredTokens(any(Instant.class));
                    assertThat(context).doesNotHaveBean(ScheduledAnnotationBeanPostProcessor.class);
                    verifyNoInteractions(scheduler);
                });
    }

    @Test
    void normalProfileStillRegistersTicketCleanupUsingAnInertScheduler() {
        TicketVerificationRepository repository = mock(TicketVerificationRepository.class);
        TaskScheduler scheduler = mock(TaskScheduler.class, RETURNS_DEEP_STUBS);
        when(scheduler.getClock()).thenReturn(Clock.systemUTC());
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .withUserConfiguration(ApplicationSchedulingConfig.class, TicketVerificationTokenStore.class)
                .withBean(TicketVerificationRepository.class, () -> repository)
                .withBean(TaskScheduler.class, () -> scheduler)
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                    assertThat(context.getBean(ScheduledAnnotationBeanPostProcessor.class).getScheduledTasks()).hasSize(1);
                    verify(scheduler).scheduleAtFixedRate(any(Runnable.class), eq(Duration.ofMillis(900_000)));
                    verifyNoInteractions(repository);
                });
    }

    private static <T> ApplicationContextRunner withMockTarget(ApplicationContextRunner runner, Class<T> type, List<Object> targets) {
        T target = mock(type);
        targets.add(target);
        return runner.withBean(type, () -> target);
    }
}
