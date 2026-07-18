package com.example.ai.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

@Component("aiUpstream")
public class AiUpstreamHealthIndicator implements HealthIndicator {

    private static final Duration DEFAULT_HEALTH_TIMEOUT = Duration.ofSeconds(1);

    private final AiServiceSettings aiServiceSettings;
    private final WebClient.Builder webClientBuilder;
    private final Duration healthTimeout;

    @Autowired
    public AiUpstreamHealthIndicator(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            @Value("${app.ai.proxy.health-timeout-ms:1000}") long healthTimeoutMs) {
        this(
                aiServiceSettings,
                webClientBuilder,
                healthTimeoutMs > 0 ? Duration.ofMillis(healthTimeoutMs) : DEFAULT_HEALTH_TIMEOUT);
    }

    AiUpstreamHealthIndicator(
            AiServiceSettings aiServiceSettings,
            WebClient.Builder webClientBuilder,
            Duration healthTimeout) {
        this.aiServiceSettings = aiServiceSettings;
        this.webClientBuilder = webClientBuilder;
        this.healthTimeout = healthTimeout == null || healthTimeout.isZero() || healthTimeout.isNegative()
                ? DEFAULT_HEALTH_TIMEOUT
                : healthTimeout;
    }

    @Override
    public Health health() {
        String target = aiServiceSettings.sanitizedServiceTarget();
        String healthUrl = aiServiceSettings.buildUrl("/health");
        if (!StringUtils.hasText(healthUrl)) {
            return Health.down()
                    .withDetail("target", target)
                    .withDetail("reason", "AI service URL is not configured")
                    .build();
        }

        try {
            HttpStatusCode status = webClientBuilder.build()
                    .get()
                    .uri(healthUrl)
                    .exchangeToMono(response -> response.releaseBody().thenReturn(response.statusCode()))
                    .block(healthTimeout);

            if (status != null && status.is2xxSuccessful()) {
                return Health.up()
                        .withDetail("target", target)
                        .withDetail("status", status.value())
                        .build();
            }

            return Health.down()
                    .withDetail("target", target)
                    .withDetail("status", status == null ? "empty" : status.value())
                    .build();
        } catch (RuntimeException e) {
            Throwable rootCause = rootCause(e);
            return Health.down()
                    .withDetail("target", target)
                    .withDetail("cause", rootCause.getClass().getName())
                    .withDetail("message", rootCause.getMessage())
                    .build();
        }
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? throwable : current;
    }
}
