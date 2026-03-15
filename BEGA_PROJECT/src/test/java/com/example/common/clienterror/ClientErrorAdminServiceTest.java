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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
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

        when(eventRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of(runtimeEvent, apiEvent));
        when(feedbackRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(any(), any()))
                .thenReturn(List.of(feedback));
        when(alertNotificationRepository.findTop10ByNotifiedAtBetweenOrderByNotifiedAtDesc(any(), any()))
                .thenReturn(List.of());
        when(alertNotificationRepository.findByFingerprintInOrderByNotifiedAtDesc(anyCollection()))
                .thenReturn(List.of());
        when(eventRepository.countDistinctFingerprints(any(), any())).thenReturn(2L);
        when(eventRepository.countDistinctRoutes(any(), any())).thenReturn(2L);

        ClientErrorDashboardDto dashboard = clientErrorAdminService.getDashboard(
                OffsetDateTime.parse("2026-03-13T00:00:00Z"),
                OffsetDateTime.parse("2026-03-14T00:00:00Z"));

        assertThat(dashboard.totals().runtime()).isEqualTo(1L);
        assertThat(dashboard.totals().api()).isEqualTo(1L);
        assertThat(dashboard.totals().feedback()).isEqualTo(1L);
        assertThat(dashboard.topFingerprints()).extracting("source")
                .contains("unhandled_rejection", "api");
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
        ClientErrorEventEntity similar = ClientErrorEventEntity.builder()
                .eventId("evt-2")
                .bucket(ClientErrorBucket.RUNTIME)
                .source(ClientErrorSource.UNHANDLED_REJECTION)
                .message("render failed")
                .route("/mypage")
                .normalizedRoute("/mypage")
                .statusGroup("none")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 10, 10))
                .fingerprint("fp-1")
                .feedbackCount(0)
                .build();

        when(eventRepository.findByEventId("evt-1")).thenReturn(Optional.of(event));
        when(feedbackRepository.findByEventIdOrderByOccurredAtDesc("evt-1")).thenReturn(List.of(feedback));
        when(eventRepository.findTop10ByFingerprintAndEventIdNotOrderByOccurredAtDesc("fp-1", "evt-1"))
                .thenReturn(List.of(similar));

        ClientErrorEventDetailDto detail = clientErrorAdminService.getEventDetail("evt-1");

        assertThat(detail.event().eventId()).isEqualTo("evt-1");
        assertThat(detail.feedback()).hasSize(1);
        assertThat(detail.sameFingerprintRecentEvents()).extracting(ClientErrorEventSummaryDto::eventId)
                .containsExactly("evt-2");
    }

    @Test
    @DisplayName("이벤트 페이지 조회는 raw 엔티티를 요약 DTO로 매핑한다")
    void getEventsMapsEntitiesToPage() {
        ClientErrorEventEntity entity = ClientErrorEventEntity.builder()
                .eventId("evt-page")
                .bucket(ClientErrorBucket.API)
                .source(ClientErrorSource.API)
                .message("request failed")
                .route("/prediction")
                .normalizedRoute("/prediction")
                .statusCode(500)
                .statusGroup("5xx")
                .method("GET")
                .endpoint("/api/predictions")
                .normalizedEndpoint("/api/predictions")
                .occurredAt(LocalDateTime.of(2026, 3, 13, 9, 0))
                .fingerprint("fp-page")
                .feedbackCount(2)
                .build();

        when(eventRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        var result = clientErrorAdminService.getEvents(
                "api",
                null,
                null,
                null,
                null,
                null,
                OffsetDateTime.parse("2026-03-13T00:00:00Z"),
                OffsetDateTime.parse("2026-03-14T00:00:00Z"),
                0,
                20);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).bucket()).isEqualTo("api");
        assertThat(result.content().get(0).feedbackCount()).isEqualTo(2);
    }
}
