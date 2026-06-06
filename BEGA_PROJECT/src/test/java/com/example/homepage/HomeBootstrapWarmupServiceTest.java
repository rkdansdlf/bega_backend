package com.example.homepage;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HomeBootstrapWarmupServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-15T00:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private HomePageFacadeService homePageFacadeService;

    @Test
    @DisplayName("warm-up이 활성화되면 서버 기준 오늘 bootstrap을 미리 조회한다")
    void warmupTodayBootstrapLoadsTodayWhenEnabled() {
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, true);

        service.warmupTodayBootstrap();

        verify(homePageFacadeService).refreshBootstrap(LocalDate.of(2026, 5, 15));
    }

    @Test
    @DisplayName("warm-up이 비활성화되면 bootstrap을 조회하지 않는다")
    void warmupTodayBootstrapSkipsWhenDisabled() {
        HomeBootstrapWarmupService service =
                new HomeBootstrapWarmupService(homePageFacadeService, FIXED_CLOCK, false);

        service.warmupTodayBootstrap();

        verifyNoInteractions(homePageFacadeService);
    }
}
