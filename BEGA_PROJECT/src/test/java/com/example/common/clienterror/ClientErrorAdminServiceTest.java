package com.example.common.clienterror;

import com.example.common.clienterror.dto.ClientErrorDashboardDto;
import com.example.common.clienterror.dto.ClientErrorEventDetailDto;
import com.example.common.clienterror.dto.ClientErrorEventSummaryDto;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientErrorAdminServiceTest {

    @Mock
    private ClientErrorEventRepository eventRepository;

    @Mock
    private ClientErrorFeedbackRepository feedbackRepository;

    @Mock
    private ClientErrorAlertNotificationRepository alertNotificationRepository;

    private ClientErrorAdminService clientErrorAdminService;

    @BeforeEach
    void setUp() {
        clientErrorAdminService = new ClientErrorAdminService(eventRepository, feedbackRepository, alertNotificationRepository);
    }

    @Test
    @DisplayName("대시보드는 unhandled_rejection을 runtime 카테고리로 집계한다")
    void getDashboardBucketsUnhandledRejectionAsRuntime() {
        ClientErrorEventEntity runtimeEvent = ClientErrorEventEntity.builder()
                .eventId("evt-runtime")
                .bucket(ClientErrorBucket.RUNTIME)
                .source(ClientErrorSource.UNHANDLED_REJECTION)
                .message("render crash")
                .route("/mypage")
                .normalizedRoute("/mypage")
                .statusGroup("none")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 11, 0))
                .fingerprint("fp-runtime")
                .feedbackCount(0)
                .build();
        ClientErrorEventEntity apiEvent = ClientErrorEventEntity.builder()
                .eventId("evt-api")
                .bucket(ClientErrorBucket.API)
                .source(ClientErrorSource.API)
                .message("request failed")
                .route("/prediction")
                .normalizedRoute("/prediction")
                .statusCode(500)
                .statusGroup("5xx")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 12, 0))
                .fingerprint("fp-api")
                .feedbackCount(1)
                .build();
        ClientErrorFeedbackEntity feedback = ClientErrorFeedbackEntity.builder()
                .eventId("evt-api")
                .comment("재시도했지만 동일합니다.")
                .actionTaken("api_error_feedback")
                .route("/prediction")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 12, 5))
                .build();

        when(eventRepository.countHourlyBuckets(any(), any()))
                .thenReturn(List.of(
                        eventBucket(2026, 3, 13, 11, ClientErrorBucket.RUNTIME, 1),
                        eventBucket(2026, 3, 13, 12, ClientErrorBucket.API, 1)));
        when(feedbackRepository.countHourlyBuckets(any(), any()))
                .thenReturn(List.of(feedbackBucket(2026, 3, 13, 12, 1)));
        when(feedbackRepository.findTop10ByOccurredAtBetweenOrderByOccurredAtDesc(any(), any()))
                .thenReturn(List.of(feedback));
        when(alertNotificationRepository.findTop10ByNotifiedAtBetweenOrderByNotifiedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(alertNotificationRepository.findByFingerprintInOrderByNotifiedAtDesc(anyCollection()))
                .thenReturn(List.of());
        when(eventRepository.findTopFingerprintSummaries(any(), any(), any(Pageable.class)))
                .thenReturn(List.of(
                        topFingerprint("fp-runtime", 1, 0, runtimeEvent.getOccurredAt()),
                        topFingerprint("fp-api", 1, 0, apiEvent.getOccurredAt())));
        when(eventRepository.findLatestEventsByFingerprintInBetween(any(), any(), anyCollection()))
                .thenReturn(List.of(
                        summaryProjection(runtimeEvent),
                        summaryProjection(apiEvent)));
        when(eventRepository.findDashboardDistinctTotals(any(), any())).thenReturn(distinctTotals(2L, 2L));

        ClientErrorDashboardDto dashboard = clientErrorAdminService.getDashboard(
                OffsetDateTime.parse("2026-03-13T00:00:00Z"),
                OffsetDateTime.parse("2026-03-14T00:00:00Z"));

        assertThat(dashboard.totals().runtime()).isEqualTo(1L);
        assertThat(dashboard.totals().api()).isEqualTo(1L);
        assertThat(dashboard.totals().feedback()).isEqualTo(1L);
        assertThat(dashboard.topFingerprints()).extracting("source")
                .contains("unhandled_rejection", "api");
        verify(eventRepository, never()).findByOccurredAtBetweenOrderByOccurredAtAsc(any(), any());
        verify(feedbackRepository, never()).findByOccurredAtBetweenOrderByOccurredAtAsc(any(), any());
    }

    @Test
    @DisplayName("상세 조회는 피드백과 동일 fingerprint 최근 이벤트를 함께 반환한다")
    void getEventDetailIncludesFeedbackAndSimilarEvents() {
        ClientErrorEventEntity event = ClientErrorEventEntity.builder()
                .eventId("evt-1")
                .bucket(ClientErrorBucket.RUNTIME)
                .source(ClientErrorSource.RUNTIME)
                .message("render failed")
                .route("/mypage")
                .normalizedRoute("/mypage")
                .statusGroup("none")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 10, 0))
                .fingerprint("fp-1")
                .feedbackCount(1)
                .stack("stack")
                .componentStack("component")
                .build();
        ClientErrorFeedbackEntity feedback = ClientErrorFeedbackEntity.builder()
                .eventId("evt-1")
                .comment("새로고침해도 동일")
                .actionTaken("error_boundary_feedback")
                .route("/mypage")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 10, 5))
                .build();
        ClientErrorEventSummaryProjection similar = summaryProjection(
                "evt-2",
                ClientErrorBucket.RUNTIME,
                ClientErrorSource.UNHANDLED_REJECTION,
                "render failed",
                null,
                "none",
                null,
                "/mypage",
                "/mypage",
                null,
                null,
                null,
                "fp-1",
                LocalDateTime.of(2026, 3, 13, 10, 10),
                null,
                null,
                0);

        when(eventRepository.findByEventId("evt-1")).thenReturn(Optional.of(event));
        when(feedbackRepository.findByEventIdOrderByOccurredAtDesc("evt-1")).thenReturn(List.of(feedback));
        when(eventRepository.findRecentEventSummariesByFingerprint(eq("fp-1"), eq("evt-1"), any(Pageable.class)))
                .thenReturn(List.of(similar));

        ClientErrorEventDetailDto detail = clientErrorAdminService.getEventDetail("evt-1");

        assertThat(detail.event().eventId()).isEqualTo("evt-1");
        assertThat(detail.feedback()).hasSize(1);
        assertThat(detail.sameFingerprintRecentEvents()).extracting(ClientErrorEventSummaryDto::eventId)
                .containsExactly("evt-2");
    }

    @Test
    @DisplayName("이벤트 페이지 조회는 요약 projection을 DTO로 매핑한다")
    void getEventsMapsSummaryProjectionToPage() {
        ClientErrorEventSummaryProjection summary = summaryProjection(
                "evt-page",
                ClientErrorBucket.API,
                ClientErrorSource.API,
                "request failed",
                500,
                "5xx",
                "INTERNAL_SERVER_ERROR",
                "/prediction",
                "/prediction",
                "GET",
                "/api/predictions",
                "/api/predictions",
                "fp-page",
                LocalDateTime.of(2026, 3, 13, 9, 0),
                "session-page",
                7L,
                2);

        when(eventRepository.findEventSummaries(
                        eq(ClientErrorBucket.API),
                        isNull(),
                        eq("5xx"),
                        eq("/prediction"),
                        eq("fp-page"),
                        any(LocalDateTime.class),
                        any(LocalDateTime.class),
                        eq("request failed"),
                        org.mockito.ArgumentMatchers.<Pageable>any()))
                .thenReturn(new PageImpl<>(List.of(summary)));

        var result = clientErrorAdminService.getEvents(
                "api",
                null,
                "5XX",
                "/prediction",
                " fp-page ",
                "Request Failed",
                OffsetDateTime.parse("2026-03-13T00:00:00Z"),
                OffsetDateTime.parse("2026-03-14T00:00:00Z"),
                0,
                20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).bucket()).isEqualTo("api");
        assertThat(result.content().get(0).source()).isEqualTo("api");
        assertThat(result.content().get(0).eventId()).isEqualTo("evt-page");
        assertThat(result.content().get(0).feedbackCount()).isEqualTo(2);
    }

    private ClientErrorEventSummaryProjection summaryProjection(
            String eventId,
            ClientErrorBucket bucket,
            ClientErrorSource source,
            String message,
            Integer statusCode,
            String statusGroup,
            String responseCode,
            String route,
            String normalizedRoute,
            String method,
            String endpoint,
            String normalizedEndpoint,
            String fingerprint,
            LocalDateTime occurredAt,
            String sessionId,
            Long userId,
            Integer feedbackCount) {
        return new ClientErrorEventSummaryProjection() {
            @Override
            public String getEventId() {
                return eventId;
            }

            @Override
            public ClientErrorBucket getBucket() {
                return bucket;
            }

            @Override
            public ClientErrorSource getSource() {
                return source;
            }

            @Override
            public String getMessage() {
                return message;
            }

            @Override
            public Integer getStatusCode() {
                return statusCode;
            }

            @Override
            public String getStatusGroup() {
                return statusGroup;
            }

            @Override
            public String getResponseCode() {
                return responseCode;
            }

            @Override
            public String getRoute() {
                return route;
            }

            @Override
            public String getNormalizedRoute() {
                return normalizedRoute;
            }

            @Override
            public String getMethod() {
                return method;
            }

            @Override
            public String getEndpoint() {
                return endpoint;
            }

            @Override
            public String getNormalizedEndpoint() {
                return normalizedEndpoint;
            }

            @Override
            public String getFingerprint() {
                return fingerprint;
            }

            @Override
            public LocalDateTime getOccurredAt() {
                return occurredAt;
            }

            @Override
            public String getSessionId() {
                return sessionId;
            }

            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public Integer getFeedbackCount() {
                return feedbackCount;
            }
        };
    }

    private ClientErrorEventSummaryProjection summaryProjection(ClientErrorEventEntity entity) {
        return summaryProjection(
                entity.getEventId(),
                entity.getBucket(),
                entity.getSource(),
                entity.getMessage(),
                entity.getStatusCode(),
                entity.getStatusGroup(),
                entity.getResponseCode(),
                entity.getRoute(),
                entity.getNormalizedRoute(),
                entity.getMethod(),
                entity.getEndpoint(),
                entity.getNormalizedEndpoint(),
                entity.getFingerprint(),
                entity.getOccurredAt(),
                entity.getSessionId(),
                entity.getUserId(),
                entity.getFeedbackCount());
    }

    private ClientErrorEventTimeBucketProjection eventBucket(
            int year,
            int month,
            int day,
            int hour,
            ClientErrorBucket bucket,
            long count) {
        return new ClientErrorEventTimeBucketProjection() {
            @Override
            public Integer getBucketYear() {
                return year;
            }

            @Override
            public Integer getBucketMonth() {
                return month;
            }

            @Override
            public Integer getBucketDay() {
                return day;
            }

            @Override
            public Integer getBucketHour() {
                return hour;
            }

            @Override
            public ClientErrorBucket getBucket() {
                return bucket;
            }

            @Override
            public long getItemCount() {
                return count;
            }
        };
    }

    private ClientErrorFeedbackTimeBucketProjection feedbackBucket(
            int year,
            int month,
            int day,
            int hour,
            long count) {
        return new ClientErrorFeedbackTimeBucketProjection() {
            @Override
            public Integer getBucketYear() {
                return year;
            }

            @Override
            public Integer getBucketMonth() {
                return month;
            }

            @Override
            public Integer getBucketDay() {
                return day;
            }

            @Override
            public Integer getBucketHour() {
                return hour;
            }

            @Override
            public long getItemCount() {
                return count;
            }
        };
    }

    private ClientErrorTopFingerprintProjection topFingerprint(
            String fingerprint,
            long eventCount,
            long uniqueSessions,
            LocalDateTime latestOccurredAt) {
        return new ClientErrorTopFingerprintProjection() {
            @Override
            public String getFingerprint() {
                return fingerprint;
            }

            @Override
            public long getEventCount() {
                return eventCount;
            }

            @Override
            public long getUniqueSessions() {
                return uniqueSessions;
            }

            @Override
            public LocalDateTime getLatestOccurredAt() {
                return latestOccurredAt;
            }
        };
    }

    private ClientErrorDashboardDistinctTotalsProjection distinctTotals(long fingerprints, long routes) {
        return new ClientErrorDashboardDistinctTotalsProjection() {
            @Override
            public long getDistinctFingerprints() {
                return fingerprints;
            }

            @Override
            public long getDistinctRoutes() {
                return routes;
            }
        };
    }
}
