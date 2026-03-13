package com.example.leaderboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.example.leaderboard.service.LeaderboardService;

@DisplayName("LeaderboardDevController profile tests")
class LeaderboardDevControllerProfileTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(LeaderboardService.class, () -> mock(LeaderboardService.class))
            .withUserConfiguration(LeaderboardDevControllerConfig.class);

    @Test
    @DisplayName("dev/local profiles should register leaderboard dev controller")
    void registersInLocalProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=local")
                .run(context -> assertThat(context).hasSingleBean(LeaderboardDevController.class));
    }

    @Test
    @DisplayName("prod profile should not register leaderboard dev controller")
    void doesNotRegisterInProdProfile() {
        contextRunner
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> assertThat(context).doesNotHaveBean(LeaderboardDevController.class));
    }

    @Configuration
    @Import(LeaderboardDevController.class)
    static class LeaderboardDevControllerConfig {
    }
}
