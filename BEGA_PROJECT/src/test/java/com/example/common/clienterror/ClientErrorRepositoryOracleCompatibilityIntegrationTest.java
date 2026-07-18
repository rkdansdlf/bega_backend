package com.example.common.clienterror;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:client_error_oracle;DB_CLOSE_DELAY=-1;MODE=Oracle",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
class ClientErrorRepositoryOracleCompatibilityIntegrationTest {

    @Autowired
    private ClientErrorEventRepository eventRepository;

    @Test
    @DisplayName("Oracle 빈 문자열 의미에서도 고유 세션과 라우트를 집계한다")
    void aggregatesDistinctSessionAndRouteWithOracleEmptyStringSemantics() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 13, 0, 0);
        LocalDateTime to = from.plusDays(1);

        eventRepository.saveAll(List.of(
                event("evt-1", "fp-1", from.plusHours(1), "session-1", "/prediction"),
                event("evt-2", "fp-1", from.plusHours(2), null, "/prediction")));
        eventRepository.flush();

        ClientErrorTopFingerprintProjection fingerprint = eventRepository
                .findTopFingerprintSummaries(from, to, PageRequest.of(0, 10))
                .getFirst();
        ClientErrorDashboardDistinctTotalsProjection totals =
                eventRepository.findDashboardDistinctTotals(from, to);

        assertThat(fingerprint.getUniqueSessions()).isEqualTo(1L);
        assertThat(totals.getDistinctRoutes()).isEqualTo(1L);
    }

    private ClientErrorEventEntity event(
            String eventId,
            String fingerprint,
            LocalDateTime occurredAt,
            String sessionId,
            String normalizedRoute) {
        return ClientErrorEventEntity.builder()
                .eventId(eventId)
                .bucket(ClientErrorBucket.RUNTIME)
                .source(ClientErrorSource.RUNTIME)
                .message("client failed")
                .route(normalizedRoute)
                .normalizedRoute(normalizedRoute)
                .statusGroup("none")
                .occurredAt(occurredAt)
                .sessionId(sessionId)
                .fingerprint(fingerprint)
                .feedbackCount(0)
                .build();
    }
}
