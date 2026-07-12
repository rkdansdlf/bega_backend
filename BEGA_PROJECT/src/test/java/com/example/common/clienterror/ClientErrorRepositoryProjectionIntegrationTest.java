package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:client_error_projection;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false",
        "logging.level.org.hibernate.SQL=ERROR",
        "logging.level.org.hibernate.orm.jdbc.bind=ERROR"
})
class ClientErrorRepositoryProjectionIntegrationTest {

    @Autowired
    private ClientErrorEventRepository eventRepository;

    @Autowired
    private ClientErrorFeedbackRepository feedbackRepository;

    @Test
    @DisplayName("dashboard projection queries aggregate events, feedback, and latest fingerprint rows")
    void dashboardProjectionQueriesAggregateRows() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 13, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 3, 14, 0, 0);
        LocalDateTime runtimeAt = LocalDateTime.of(2026, 3, 13, 11, 10);
        LocalDateTime runtimeLatestAt = LocalDateTime.of(2026, 3, 13, 11, 20);
        LocalDateTime apiAt = LocalDateTime.of(2026, 3, 13, 12, 5);
        LocalDateTime api4xxAt = LocalDateTime.of(2026, 3, 13, 12, 10);

        eventRepository.saveAll(List.of(
                event("evt-runtime-1", ClientErrorBucket.RUNTIME, ClientErrorSource.RUNTIME, "fp-runtime", runtimeAt, "session-1"),
                event("evt-runtime-2", ClientErrorBucket.RUNTIME, ClientErrorSource.RUNTIME, "fp-runtime", runtimeLatestAt, ""),
                event("evt-api", ClientErrorBucket.API, ClientErrorSource.API, "fp-api", apiAt, "session-api"),
                event("evt-api-4xx", ClientErrorBucket.API, ClientErrorSource.API,
                        "fp-api-4xx", api4xxAt, "session-api-4xx", 404, "4xx")));
        feedbackRepository.save(ClientErrorFeedbackEntity.builder()
                .eventId("evt-api")
                .comment("api failed")
                .actionTaken("api_error_feedback")
                .route("/prediction")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 12, 15))
                .build());
        eventRepository.flush();
        feedbackRepository.flush();

        List<ClientErrorEventTimeBucketProjection> eventBuckets = eventRepository.countHourlyBuckets(from, to);
        assertThat(eventBuckets)
                .filteredOn(item -> item.getBucket() == ClientErrorBucket.RUNTIME)
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getBucketHour()).isEqualTo(11);
                    assertThat(item.getItemCount()).isEqualTo(2L);
                });
        assertThat(eventBuckets)
                .filteredOn(item -> item.getBucket() == ClientErrorBucket.API)
                .singleElement()
                .extracting(ClientErrorEventTimeBucketProjection::getItemCount)
                .isEqualTo(2L);

        assertThat(feedbackRepository.countHourlyBuckets(from, to))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getBucketHour()).isEqualTo(12);
                    assertThat(item.getItemCount()).isEqualTo(1L);
                });

        ClientErrorDashboardDistinctTotalsProjection distinctTotals =
                eventRepository.findDashboardDistinctTotals(from, to);
        assertThat(distinctTotals.getDistinctFingerprints()).isEqualTo(3L);
        assertThat(distinctTotals.getDistinctRoutes()).isEqualTo(1L);

        List<ClientErrorTopFingerprintProjection> topFingerprints =
                eventRepository.findTopFingerprintSummaries(from, to, PageRequest.of(0, 10));
        assertThat(topFingerprints).first().satisfies(item -> {
            assertThat(item.getFingerprint()).isEqualTo("fp-runtime");
            assertThat(item.getEventCount()).isEqualTo(2L);
            assertThat(item.getUniqueSessions()).isEqualTo(1L);
        });

        assertThat(eventRepository.findLatestEventsByFingerprintInBetween(from, to, List.of("fp-runtime")))
                .singleElement()
                .extracting(ClientErrorEventSummaryProjection::getEventId)
                .isEqualTo("evt-runtime-2");

        assertThat(eventRepository.findEventSummaries(
                        ClientErrorBucket.API,
                        ClientErrorSource.API,
                        "5xx",
                        "/prediction",
                        "fp-api",
                        from,
                        to,
                        "client",
                        PageRequest.of(0, 10)).getContent())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getEventId()).isEqualTo("evt-api");
                    assertThat(item.getBucket()).isEqualTo(ClientErrorBucket.API);
                    assertThat(item.getSource()).isEqualTo(ClientErrorSource.API);
                });

        assertThat(eventRepository.findRecentEventSummariesByFingerprint(
                        "fp-runtime",
                        "evt-runtime-2",
                        PageRequest.of(0, 10)))
                .singleElement()
                .extracting(ClientErrorEventSummaryProjection::getEventId)
                .isEqualTo("evt-runtime-1");

        List<ClientErrorAlertCandidateProjection> alertCandidates = eventRepository.findAlertCandidateSummaries(
                from,
                ClientErrorBucket.RUNTIME,
                ClientErrorBucket.API,
                "5xx",
                1L,
                PageRequest.of(0, 10));
        assertThat(alertCandidates)
                .extracting(ClientErrorAlertCandidateProjection::getFingerprint)
                .contains("fp-api", "fp-runtime")
                .doesNotContain("fp-api-4xx");
        assertThat(alertCandidates)
                .filteredOn(item -> "fp-api".equals(item.getFingerprint()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getFingerprint()).isEqualTo("fp-api");
                    assertThat(item.getObservedCount()).isEqualTo(1L);
                });

        assertThat(eventRepository.findLatestAlertEventsByFingerprint(
                from,
                ClientErrorBucket.RUNTIME,
                ClientErrorBucket.API,
                "5xx",
                List.of("fp-api", "fp-api-4xx")))
                .singleElement()
                .extracting(ClientErrorEventSummaryProjection::getEventId)
                .isEqualTo("evt-api");
    }

    @Test
    @DisplayName("event summary pagination uses id descending as the occurredAt tie breaker")
    void eventSummaryPaginationUsesStableIdTieBreaker() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 3, 13, 12, 0);
        ClientErrorEventEntity olderId = event(
                "evt-same-time-1",
                ClientErrorBucket.RUNTIME,
                ClientErrorSource.RUNTIME,
                "fp-same-time",
                occurredAt,
                "session-1");
        ClientErrorEventEntity newerId = event(
                "evt-same-time-2",
                ClientErrorBucket.RUNTIME,
                ClientErrorSource.RUNTIME,
                "fp-same-time",
                occurredAt,
                "session-2");
        eventRepository.saveAndFlush(olderId);
        eventRepository.saveAndFlush(newerId);

        List<String> eventIds = eventRepository.findEventSummaries(
                        ClientErrorBucket.RUNTIME,
                        ClientErrorSource.RUNTIME,
                        null,
                        null,
                        "fp-same-time",
                        occurredAt.minusMinutes(1),
                        occurredAt.plusMinutes(1),
                        null,
                        PageRequest.of(0, 2))
                .map(ClientErrorEventSummaryProjection::getEventId)
                .getContent();

        assertThat(eventIds).containsExactly("evt-same-time-2", "evt-same-time-1");
    }

    @Test
    @DisplayName("client error JPQL keeps Oracle blank handling and stable pagination contracts")
    void repositoryQueriesKeepOracleAndStablePaginationContracts() {
        String topFingerprintQuery = queryValue("findTopFingerprintSummaries");
        String distinctTotalsQuery = queryValue("findDashboardDistinctTotals");
        String eventSummariesQuery = queryValue("findEventSummaries");

        assertThat(topFingerprintQuery)
                .doesNotContain("<> ''")
                .containsIgnoringCase("length(trim(e.sessionId)) > 0");
        assertThat(distinctTotalsQuery)
                .doesNotContain("<> ''")
                .containsIgnoringCase("length(trim(e.normalizedRoute)) > 0");
        assertThat(eventSummariesQuery)
                .containsIgnoringCase("order by e.occurredAt desc, e.id desc");
    }

    private String queryValue(String methodName) {
        return Arrays.stream(ClientErrorEventRepository.class.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .findFirst()
                .orElseThrow()
                .getAnnotation(org.springframework.data.jpa.repository.Query.class)
                .value();
    }

    private ClientErrorEventEntity event(
            String eventId,
            ClientErrorBucket bucket,
            ClientErrorSource source,
            String fingerprint,
            LocalDateTime occurredAt,
            String sessionId) {
        return event(
                eventId,
                bucket,
                source,
                fingerprint,
                occurredAt,
                sessionId,
                bucket == ClientErrorBucket.API ? 500 : null,
                bucket == ClientErrorBucket.API ? "5xx" : "none");
    }

    private ClientErrorEventEntity event(
            String eventId,
            ClientErrorBucket bucket,
            ClientErrorSource source,
            String fingerprint,
            LocalDateTime occurredAt,
            String sessionId,
            Integer statusCode,
            String statusGroup) {
        return ClientErrorEventEntity.builder()
                .eventId(eventId)
                .bucket(bucket)
                .source(source)
                .message("client failed")
                .route("/prediction")
                .normalizedRoute("/prediction")
                .statusCode(statusCode)
                .statusGroup(statusGroup)
                .method(bucket == ClientErrorBucket.API ? "GET" : null)
                .endpoint(bucket == ClientErrorBucket.API ? "/api/predictions" : null)
                .normalizedEndpoint(bucket == ClientErrorBucket.API ? "/api/predictions" : null)
                .occurredAt(occurredAt)
                .sessionId(sessionId)
                .fingerprint(fingerprint)
                .feedbackCount(0)
                .build();
    }
}
