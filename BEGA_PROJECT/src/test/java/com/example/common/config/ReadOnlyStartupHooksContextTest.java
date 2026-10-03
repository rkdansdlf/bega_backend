package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.lang.reflect.Constructor;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import com.example.ai.scheduler.AiIngestScheduler;
import com.example.ai.service.AiIngestOrchestrationService;
import com.example.auth.scheduler.AccountSecurityScheduler;
import com.example.cheerboard.scheduler.CheerBattleScheduler;
import com.example.cheerboard.scheduler.CheerStorageScheduler;
import com.example.cheerboard.service.PostSyncScheduler;
import com.example.mate.scheduler.PartyLifecycleScheduler;
import com.example.mate.scheduler.PaymentCompensationScheduler;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class ReadOnlyStartupHooksContextTest {

    @ParameterizedTest(name = "no lifecycle registration, deletion or enqueue by {0}")
    @MethodSource("startupComponents")
    void readonlyExcludesRealStartupComponentsAcrossRefreshRunnersAndReadyEvent(Class<?> type) throws Exception {
        List<Object> dependencies = new ArrayList<>();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev", ReadOnlyVerificationTestEnvironment.PROFILE);
            registerDependencies(context, type, dependencies);
            context.register(type);
            context.refresh();
            for (ApplicationRunner runner : context.getBeansOfType(ApplicationRunner.class).values()) {
                runner.run(new DefaultApplicationArguments(new String[0]));
            }
            for (CommandLineRunner runner : context.getBeansOfType(CommandLineRunner.class).values()) {
                runner.run();
            }
            context.publishEvent(new ApplicationReadyEvent(new SpringApplication(Object.class), new String[0], context, Duration.ZERO));
            assertThat(context.getBeanNamesForType(type)).isEmpty();
            verifyNoInteractions(dependencies.toArray());
        }
    }

    @Test
    void normalProfileStillInvokesPostConstructRecurringRegistration() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev");
            registerDependencies(context, AccountSecurityScheduler.class, new ArrayList<>());
            context.register(AccountSecurityScheduler.class);
            context.refresh();
            assertThat(context.getBeanNamesForType(AccountSecurityScheduler.class)).hasSize(1);
            verify(context.getBean(JobScheduler.class), times(2))
                    .scheduleRecurrently(any(String.class), any(String.class), any(JobLambda.class));
        }
    }

    private static Stream<Class<?>> startupComponents() {
        return Stream.of(AccountSecurityScheduler.class, PartyLifecycleScheduler.class, PaymentCompensationScheduler.class,
                PostSyncScheduler.class, CheerStorageScheduler.class, CheerBattleScheduler.class,
                AiIngestScheduler.class, AiIngestOrchestrationService.class);
    }

    private static void registerDependencies(AnnotationConfigApplicationContext context, Class<?> type, List<Object> dependencies) {
        Constructor<?> constructor = Arrays.stream(type.getConstructors())
                .filter(candidate -> candidate.isAnnotationPresent(Autowired.class)).findFirst()
                .orElseGet(() -> type.getConstructors()[0]);
        for (Parameter parameter : constructor.getParameters()) {
            if (parameter.isAnnotationPresent(Value.class)) {
                continue;
            }
            Object dependency = mock(parameter.getType());
            dependencies.add(dependency);
            // Prebuilt mock singletons bypass dependency lifecycle methods and cannot contact services.
            context.getBeanFactory().registerSingleton(parameter.getType().getName(), dependency);
        }
    }
}
