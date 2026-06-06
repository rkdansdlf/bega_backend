package com.example.homepage;

import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import java.time.Clock;
import java.time.LocalDate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class HomeBootstrapWarmupService {

    private final HomePageFacadeService homePageFacadeService;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public HomeBootstrapWarmupService(
            HomePageFacadeService homePageFacadeService,
            @Value("${app.home.bootstrap.warmup.enabled:true}") boolean enabled) {
        this(homePageFacadeService, Clock.systemDefaultZone(), enabled);
    }

    HomeBootstrapWarmupService(
            HomePageFacadeService homePageFacadeService,
            Clock clock,
            boolean enabled) {
        this.homePageFacadeService = homePageFacadeService;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
        this.enabled = enabled;
    }

    @Scheduled(fixedDelayString = "${app.home.bootstrap.warmup.fixed-delay-ms:50000}", initialDelay = 5000)
    public void warmupTodayBootstrap() {
        if (!enabled) {
            return;
        }

        LocalDate today = LocalDate.now(clock);
        try {
            homePageFacadeService.refreshBootstrap(today);
            log.info("event=home_bootstrap_warmup_completed date={}", today);
        } catch (ManualBaseballDataRequiredException ex) {
            Object data = ex.getData();
            String scope = data instanceof ManualBaseballDataRequest request ? request.scope() : null;
            log.warn(
                    "event=home_bootstrap_warmup_manual_data_required date={} scope={}",
                    today,
                    scope);
        } catch (Exception ex) {
            log.warn("event=home_bootstrap_warmup_failed date={} reason={}", today, ex.getMessage());
        }
    }
}
