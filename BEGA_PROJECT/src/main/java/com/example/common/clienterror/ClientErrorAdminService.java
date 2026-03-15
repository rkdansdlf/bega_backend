package com.example.common.clienterror;

import com.example.common.clienterror.dto.ClientErrorAlertNotificationDto;
import com.example.common.clienterror.dto.ClientErrorDashboardDto;
import com.example.common.clienterror.dto.ClientErrorDashboardTotalsDto;
import com.example.common.clienterror.dto.ClientErrorEventDetailDto;
import com.example.common.clienterror.dto.ClientErrorEventPageDto;
import com.example.common.clienterror.dto.ClientErrorEventSummaryDto;
import com.example.common.clienterror.dto.ClientErrorRecentFeedbackDto;
import com.example.common.clienterror.dto.ClientErrorTimeSeriesPointDto;
import com.example.common.clienterror.dto.ClientErrorTopFingerprintDto;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClientErrorAdminService {

    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int TOP_FINGERPRINT_LIMIT = 10;

    private final ClientErrorEventRepository eventRepository;
    private final ClientErrorFeedbackRepository feedbackRepository;
    private final ClientErrorAlertNotificationRepository alertNotificationRepository;

    public ClientErrorDashboardDto getDashboard(OffsetDateTime from, OffsetDateTime to) {
        QueryWindow window = resolveWindow(from, to);
        List<ClientErrorEventEntity> events = eventRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(
                window.from(), window.to());
        List<ClientErrorFeedbackEntity> feedback = feedbackRepository.findByOccurredAtBetweenOrderByOccurredAtAsc(
                window.from(), window.to());
        List<ClientErrorAlertNotificationEntity> recentAlerts =
                alertNotificationRepository.findTop10ByNotifiedAtBetweenOrderByNotifiedAtDesc(window.from(), window.to());

        ClientErrorDashboardTotalsDto totals = new ClientErrorDashboardTotalsDto(
                events.stream().filter(event -> event.getBucket() == ClientErrorBucket.API).count(),
                events.stream().filter(event -> event.getBucket() == ClientErrorBucket.RUNTIME).count(),
                feedback.size(),
                eventRepository.countDistinctFingerprints(window.from(), window.to()),
                eventRepository.countDistinctRoutes(window.from(), window.to()));

        List<ClientErrorTopFingerprintDto> topFingerprints = buildTopFingerprints(events);
        Map<String, OffsetDateTime> latestAlertByFingerprint = latestAlertByFingerprint(topFingerprints);
        List<ClientErrorTopFingerprintDto> topFingerprintsWithAlerts = topFingerprints.stream()
                .map(item -> new ClientErrorTopFingerprintDto(
                        item.fingerprint(),
                        item.bucket(),
                        item.source(),
                        item.message(),
                        item.route(),
                        item.endpoint(),
                        item.statusGroup(),
                        item.method(),
                        item.count(),
                        item.uniqueSessions(),
                        item.latestEventId(),
                        item.latestOccurredAt(),
                        latestAlertByFingerprint.get(item.fingerprint())))
                .toList();

        return new ClientErrorDashboardDto(
                ClientErrorSupport.toOffsetDateTime(window.from()),
                ClientErrorSupport.toOffsetDateTime(window.to()),
                window.granularity(),
                totals,
                buildTimeSeries(events, feedback, window),
                topFingerprintsWithAlerts,
                feedback.stream()
                        .sorted(Comparator.comparing(ClientErrorFeedbackEntity::getOccurredAt).reversed())
                        .limit(10)
                        .map(this::toRecentFeedbackDto)
                        .toList(),
                recentAlerts.stream()
                        .map(this::toAlertDto)
                        .toList());
    }

    public ClientErrorEventPageDto getEvents(
            String bucket,
            String source,
            String statusGroup,
            String route,
            String fingerprint,
            String search,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size) {
        Specification<ClientErrorEventEntity> spec = buildEventSpecification(
                bucket,
                source,
                statusGroup,
                route,
                fingerprint,
                search,
                from,
                to);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<ClientErrorEventEntity> result = eventRepository.findAll(spec, pageable);
        return new ClientErrorEventPageDto(
                result.getContent().stream().map(this::toEventSummaryDto).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getSize(),
                result.getNumber(),
                result.isLast());
    }

    public ClientErrorEventDetailDto getEventDetail(String eventId) {
        ClientErrorEventEntity event = eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new NoSuchElementException("클라이언트 에러 이벤트를 찾을 수 없습니다. eventId=" + eventId));

        List<ClientErrorRecentFeedbackDto> feedback = feedbackRepository.findByEventIdOrderByOccurredAtDesc(eventId)
                .stream()
                .map(this::toRecentFeedbackDto)
                .toList();

        List<ClientErrorEventSummaryDto> sameFingerprintRecentEvents =
                eventRepository.findTop10ByFingerprintAndEventIdNotOrderByOccurredAtDesc(event.getFingerprint(), eventId)
                        .stream()
                        .map(this::toEventSummaryDto)
                        .toList();

        return new ClientErrorEventDetailDto(
                toEventSummaryDto(event),
                event.getStack(),
                event.getComponentStack(),
                feedback,
                sameFingerprintRecentEvents);
    }

    private Map<String, OffsetDateTime> latestAlertByFingerprint(List<ClientErrorTopFingerprintDto> topFingerprints) {
        Set<String> fingerprints = topFingerprints.stream()
                .map(ClientErrorTopFingerprintDto::fingerprint)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (fingerprints.isEmpty()) {
            return Map.of();
        }

        Map<String, OffsetDateTime> result = new HashMap<>();
        for (ClientErrorAlertNotificationEntity notification : alertNotificationRepository
                .findByFingerprintInOrderByNotifiedAtDesc(fingerprints)) {
            result.putIfAbsent(notification.getFingerprint(), ClientErrorSupport.toOffsetDateTime(notification.getNotifiedAt()));
        }
        return result;
    }

    private Specification<ClientErrorEventEntity> buildEventSpecification(
            String bucket,
            String source,
            String statusGroup,
            String route,
            String fingerprint,
            String search,
            OffsetDateTime from,
            OffsetDateTime to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();

            ClientErrorBucket bucketFilter = ClientErrorBucket.fromValue(bucket);
            if (bucketFilter != null) {
                predicates.add(builder.equal(root.get("bucket"), bucketFilter));
            }

            ClientErrorSource sourceFilter = ClientErrorSource.fromCategory(source);
            if (source != null && !source.isBlank() && sourceFilter != ClientErrorSource.UNKNOWN) {
                predicates.add(builder.equal(root.get("source"), sourceFilter));
            }

            if (statusGroup != null && !statusGroup.isBlank()) {
                predicates.add(builder.equal(root.get("statusGroup"), statusGroup.trim().toLowerCase()));
            }

            if (route != null && !route.isBlank()) {
                String normalizedRoute = ClientErrorSupport.normalizeRoute(route);
                predicates.add(builder.like(root.get("normalizedRoute"), "%" + normalizedRoute + "%"));
            }

            if (fingerprint != null && !fingerprint.isBlank()) {
                predicates.add(builder.equal(root.get("fingerprint"), fingerprint.trim()));
            }

            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(
                        root.get("occurredAt"),
                        from.withOffsetSameInstant(ClientErrorSupport.UTC).toLocalDateTime()));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(
                        root.get("occurredAt"),
                        to.withOffsetSameInstant(ClientErrorSupport.UTC).toLocalDateTime()));
            }

            if (search != null && !search.isBlank()) {
                String term = "%" + search.trim().toLowerCase() + "%";
                predicates.add(builder.or(
                        builder.like(builder.lower(root.get("eventId")), term),
                        builder.like(builder.lower(root.get("message")), term),
                        builder.like(builder.lower(root.get("route")), term),
                        builder.like(builder.lower(root.get("endpoint")), term),
                        builder.like(builder.lower(root.get("fingerprint")), term)));
            }

            query.orderBy(builder.desc(root.get("occurredAt")));
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private List<ClientErrorTimeSeriesPointDto> buildTimeSeries(
            List<ClientErrorEventEntity> events,
            List<ClientErrorFeedbackEntity> feedback,
            QueryWindow window) {
        Map<LocalDateTime, EnumMap<ClientErrorBucket, Long>> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = window.seriesStart();
        while (!cursor.isAfter(window.to())) {
            EnumMap<ClientErrorBucket, Long> counts = new EnumMap<>(ClientErrorBucket.class);
            counts.put(ClientErrorBucket.API, 0L);
            counts.put(ClientErrorBucket.RUNTIME, 0L);
            counts.put(ClientErrorBucket.FEEDBACK, 0L);
            buckets.put(cursor, counts);
            cursor = advance(cursor, window.granularity());
        }

        for (ClientErrorEventEntity event : events) {
            LocalDateTime bucketStart = truncate(event.getOccurredAt(), window.granularity());
            EnumMap<ClientErrorBucket, Long> counts = buckets.get(bucketStart);
            if (counts != null) {
                counts.put(event.getBucket(), counts.getOrDefault(event.getBucket(), 0L) + 1L);
            }
        }

        for (ClientErrorFeedbackEntity item : feedback) {
            LocalDateTime bucketStart = truncate(item.getOccurredAt(), window.granularity());
            EnumMap<ClientErrorBucket, Long> counts = buckets.get(bucketStart);
            if (counts != null) {
                counts.put(ClientErrorBucket.FEEDBACK, counts.getOrDefault(ClientErrorBucket.FEEDBACK, 0L) + 1L);
            }
        }

        return buckets.entrySet().stream()
                .map(entry -> new ClientErrorTimeSeriesPointDto(
                        ClientErrorSupport.toOffsetDateTime(entry.getKey()),
                        entry.getValue().getOrDefault(ClientErrorBucket.API, 0L),
                        entry.getValue().getOrDefault(ClientErrorBucket.RUNTIME, 0L),
                        entry.getValue().getOrDefault(ClientErrorBucket.FEEDBACK, 0L)))
                .toList();
    }

    private List<ClientErrorTopFingerprintDto> buildTopFingerprints(List<ClientErrorEventEntity> events) {
        Map<String, FingerprintAggregate> aggregates = new HashMap<>();
        for (ClientErrorEventEntity event : events) {
            aggregates.computeIfAbsent(event.getFingerprint(), key -> new FingerprintAggregate(event.getFingerprint()))
                    .add(event);
        }

        return aggregates.values().stream()
                .sorted(Comparator
                        .comparingLong(FingerprintAggregate::count)
                        .reversed()
                        .thenComparing(FingerprintAggregate::latestOccurredAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(TOP_FINGERPRINT_LIMIT)
                .map(FingerprintAggregate::toDto)
                .toList();
    }

    private ClientErrorEventSummaryDto toEventSummaryDto(ClientErrorEventEntity event) {
        return new ClientErrorEventSummaryDto(
                event.getEventId(),
                event.getBucket().getValue(),
                event.getSource().getValue(),
                event.getMessage(),
                event.getStatusCode(),
                event.getStatusGroup(),
                event.getResponseCode(),
                event.getRoute(),
                event.getNormalizedRoute(),
                event.getMethod(),
                event.getEndpoint(),
                event.getNormalizedEndpoint(),
                event.getFingerprint(),
                ClientErrorSupport.toOffsetDateTime(event.getOccurredAt()),
                event.getSessionId(),
                event.getUserId(),
                event.getFeedbackCount());
    }

    private ClientErrorRecentFeedbackDto toRecentFeedbackDto(ClientErrorFeedbackEntity feedback) {
        return new ClientErrorRecentFeedbackDto(
                feedback.getEventId(),
                feedback.getRoute(),
                feedback.getActionTaken(),
                feedback.getComment(),
                ClientErrorSupport.toOffsetDateTime(feedback.getOccurredAt()));
    }

    private ClientErrorAlertNotificationDto toAlertDto(ClientErrorAlertNotificationEntity notification) {
        return new ClientErrorAlertNotificationDto(
                notification.getId(),
                notification.getFingerprint(),
                notification.getBucket().getValue(),
                notification.getSource().getValue(),
                notification.getRoute(),
                notification.getStatusGroup(),
                notification.getObservedCount(),
                notification.getThresholdCount(),
                notification.getWindowMinutes(),
                notification.getLatestEventId(),
                notification.getLatestMessage(),
                ClientErrorSupport.toOffsetDateTime(notification.getLatestOccurredAt()),
                ClientErrorSupport.toOffsetDateTime(notification.getNotifiedAt()),
                notification.getDeliveryStatus().name(),
                notification.getFailureReason());
    }

    private QueryWindow resolveWindow(OffsetDateTime from, OffsetDateTime to) {
        OffsetDateTime resolvedTo = to != null ? to : OffsetDateTime.now(ClientErrorSupport.UTC);
        OffsetDateTime resolvedFrom;
        if (from != null) {
            resolvedFrom = from;
        } else {
            resolvedFrom = resolvedTo.minusHours(DEFAULT_WINDOW_HOURS);
        }

        LocalDateTime fromAt = resolvedFrom.withOffsetSameInstant(ClientErrorSupport.UTC).toLocalDateTime();
        LocalDateTime toAt = resolvedTo.withOffsetSameInstant(ClientErrorSupport.UTC).toLocalDateTime();
        String granularity = Duration.between(fromAt, toAt).toHours() > 48 ? "day" : "hour";
        LocalDateTime seriesStart = truncate(fromAt, granularity);
        return new QueryWindow(fromAt, toAt, seriesStart, granularity);
    }

    private LocalDateTime truncate(LocalDateTime value, String granularity) {
        if ("day".equals(granularity)) {
            return value.truncatedTo(ChronoUnit.DAYS);
        }
        return value.truncatedTo(ChronoUnit.HOURS);
    }

    private LocalDateTime advance(LocalDateTime value, String granularity) {
        if ("day".equals(granularity)) {
            return value.plusDays(1);
        }
        return value.plusHours(1);
    }

    private record QueryWindow(LocalDateTime from, LocalDateTime to, LocalDateTime seriesStart, String granularity) {
    }

    private static final class FingerprintAggregate {
        private final String fingerprint;
        private final Set<String> sessionIds = new java.util.HashSet<>();
        private long count;
        private ClientErrorBucket bucket;
        private ClientErrorSource source;
        private String message;
        private String route;
        private String endpoint;
        private String statusGroup;
        private String method;
        private String latestEventId;
        private LocalDateTime latestOccurredAt;

        private FingerprintAggregate(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        void add(ClientErrorEventEntity event) {
            count += 1;
            if (event.getSessionId() != null && !event.getSessionId().isBlank()) {
                sessionIds.add(event.getSessionId());
            }
            if (latestOccurredAt == null || event.getOccurredAt().isAfter(latestOccurredAt)) {
                bucket = event.getBucket();
                source = event.getSource();
                message = event.getMessage();
                route = event.getRoute();
                endpoint = event.getEndpoint();
                statusGroup = event.getStatusGroup();
                method = event.getMethod();
                latestEventId = event.getEventId();
                latestOccurredAt = event.getOccurredAt();
            }
        }

        long count() {
            return count;
        }

        LocalDateTime latestOccurredAt() {
            return latestOccurredAt;
        }

        ClientErrorTopFingerprintDto toDto() {
            return new ClientErrorTopFingerprintDto(
                    fingerprint,
                    bucket.getValue(),
                    source.getValue(),
                    message,
                    route,
                    endpoint,
                    statusGroup,
                    method,
                    count,
                    sessionIds.size(),
                    latestEventId,
                    ClientErrorSupport.toOffsetDateTime(latestOccurredAt),
                    null);
        }
    }
}
