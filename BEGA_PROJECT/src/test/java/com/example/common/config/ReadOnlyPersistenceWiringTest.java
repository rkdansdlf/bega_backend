package com.example.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import javax.sql.DataSource;

import com.example.kbo.config.KboGamePostgresJpaConfig;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.mock.env.MockEnvironment;

class ReadOnlyPersistenceWiringTest {

    @Test
    void profileKeepsPrimaryAndStadiumValidationWhileDisablingMigrations() {
        MockEnvironment environment = ReadOnlyVerificationTestEnvironment.create();
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("baseball.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("baseball.flyway.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("rag.flyway.enabled")).isEqualTo("false");
        assertThat(environment.getProperty("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void disabledBaseballFlywayStillSatisfiesDependsOnWithoutConnectingOrMigrating() {
        assertThat(Arrays.stream(KboGamePostgresJpaConfig.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(DependsOn.class)).filter(annotation -> annotation != null)
                .flatMap(annotation -> Arrays.stream(annotation.value()))).contains("baseballFlyway");
        DataSource dataSource = mock(DataSource.class);
        FluentConfiguration configuration = mock(FluentConfiguration.class, RETURNS_SELF);
        Flyway flyway = mock(Flyway.class);
        when(configuration.load()).thenReturn(flyway);
        try (MockedStatic<Flyway> flywayFactory = mockStatic(Flyway.class)) {
            flywayFactory.when(Flyway::configure).thenReturn(configuration);
            new ApplicationContextRunner()
                    .withInitializer(context -> context.setEnvironment(ReadOnlyVerificationTestEnvironment.create()))
                    .withUserConfiguration(BaseballFlywayMigrationConfig.class)
                    .withBean("stadiumDataSource", DataSource.class, () -> dataSource)
                    .run(context -> {
                        assertThat(context).hasNotFailed().hasBean("baseballFlyway");
                        assertThat(context.getBean("baseballFlyway")).isSameAs(flyway);
                        verifyNoInteractions(dataSource, flyway);
                    });
        }
    }
}
