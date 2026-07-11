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
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
        List<ClientErrorEventTimeBucketProjection> eventBuckets = loadEventTimeBuckets(window);
        List<ClientErrorFeedbackTimeBucketProjection> feedbackBuckets = loadFeedbackTimeBuckets(window);
        List<ClientErrorFeedbackEntity> recentFeedback =
                feedbackRepository.findTop10ByOccurredAtBetweenOrderByOccurredAtDesc(window.from(), window.to());
        List<ClientErrorAlertNotificationEntity> recentAlerts =
                alertNotificationRepository.findTop10ByNotifiedAtBetweenOrderByNotifiedAtDesc(window.from(), window.to());
        ClientErrorDashboardDistinctTotalsProjection distinctTotals =
                eventRepository.findDashboardDistinctTotals(window.from(), window.to());

        ClientErrorDashboardTotalsDto totals = new ClientErrorDashboardTotalsDto(
                sumEventBucket(eventBuckets, ClientErrorBucket.API),
                sumEventBucket(eventBuckets, ClientErrorBucket.RUNTIME),
                feedbackBuckets.stream().mapToLong(ClientErrorFeedbackTimeBucketProjection::getItemCount).sum(),
                distinctTotals.getDistinctFingerprints(),
                distinctTotals.getDistinctRoutes());

        List<ClientErrorTopFingerprintDto> topFingerprints = findTopFingerprints(window);
        Map<String, LatestAlertInfo> latestAlertByFingerprint = latestAlertByFingerprint(topFingerprints);
        List<ClientErrorTopFingerprintDto> topFingerprintsWithAlerts = topFingerprints.stream()
                .map(item -> {
                    LatestAlertInfo latestAlertInfo = latestAlertByFingerprint.get(item.fingerprint());
                    return new ClientErrorTopFingerprintDto(
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
                            latestAlertInfo != null ? latestAlertInfo.notifiedAt() : null,
                            latestAlertInfo != null ? latestAlertInfo.channel() : null);
                })
                .toList();

        return new ClientErrorDashboardDto(
                ClientErrorSupport.toOffsetDateTime(window.from()),
                ClientErrorSupport.toOffsetDateTime(window.to()),
                window.granularity(),
                totals,
                buildTimeSeries(eventBuckets, feedbackBuckets, window),
                topFingerprintsWithAlerts,
                recentFeedback.stream()
                        .map(this::toRecentFeedbackDto)
                        .toList(),
                recentAlerts.stream()
                        .map(this::toAlertDto)
                        .toList());
    }

    private List<ClientErrorEventTimeBucketProjection> loadEventTimeBuckets(QueryWindow window) {
        if ("day".equals(window.granularity())) {
            return eventRepository.countDailyBuckets(window.from(), window.to());
        }
        return eventRepository.countHourlyBuckets(window.from(), window.to());
    }

    private List<ClientErrorFeedbackTimeBucketProjection> loadFeedbackTimeBuckets(QueryWindow window) {
        if ("day".equals(window.granularity())) {
            return feedbackRepository.countDailyBuckets(window.from(), window.to());
        }
        return feedbackRepository.countHourlyBuckets(window.from(), window.to());
    }

    private long sumEventBucket(List<ClientErrorEventTimeBucketProjection> eventBuckets, ClientErrorBucket bucket) {
        return eventBuckets.stream()
                .filter(item -> item.getBucket() == bucket)
                .mapToLong(ClientErrorEventTimeBucketProjection::getItemCount)
                .sum();
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
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<ClientErrorEventSummaryProjection> result = eventRepository.findEventSummaries(
                ClientErrorBucket.fromValue(bucket),
                resolveSourceFilter(source),
                normalizeStatusGroupFilter(statusGroup),
                normalizeRouteFilter(route),
                normalizeFingerprintFilter(fingerprint),
                toUtcLocalDateTime(from),
                toUtcLocalDateTime(to),
                normalizeSearchTerm(search),
                pageable);
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
                eventRepository.findRecentEventSummariesByFingerprint(
                                event.getFingerprint(),
                                eventId,
                                PageRequest.of(0, 10))
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

    private Map<String, LatestAlertInfo> latestAlertByFingerprint(List<ClientErrorTopFingerprintDto> topFingerprints) {
        Set<String> fingerprints = topFingerprints.stream()
                .map(ClientErrorTopFingerprintDto::fingerprint)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (fingerprints.isEmpty()) {
            return Map.of();
        }

        Map<String, LatestAlertInfo> result = new HashMap<>();
        for (ClientErrorAlertNotificationEntity notification : alertNotificationRepository
                .findByFingerprintInOrderByNotifiedAtDesc(fingerprints)) {
            result.putIfAbsent(
                    notification.getFingerprint(),
                    new LatestAlertInfo(
                            ClientErrorSupport.toOffsetDateTime(notification.getNotifiedAt()),
                            notification.getChannel().getValue()));
        }
        return result;
    }

    private List<ClientErrorTimeSeriesPointDto> buildTimeSeries(
            List<ClientErrorEventTimeBucketProjection> eventBuckets,
            List<ClientErrorFeedbackTimeBucketProjection> feedbackBuckets,
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

        for (ClientErrorEventTimeBucketProjection item : eventBuckets) {
            LocalDateTime bucketStart = bucketStart(item);
            EnumMap<ClientErrorBucket, Long> counts = buckets.get(bucketStart);
            if (counts != null) {
                counts.put(item.getBucket(), counts.getOrDefault(item.getBucket(), 0L) + item.getItemCount());
            }
        }

        for (ClientErrorFeedbackTimeBucketProjection item : feedbackBuckets) {
            LocalDateTime bucketStart = bucketStart(item);
            EnumMap<ClientErrorBucket, Long> counts = buckets.get(bucketStart);
            if (counts != null) {
                counts.put(
                        ClientErrorBucket.FEEDBACK,
                        counts.getOrDefault(ClientErrorBucket.FEEDBACK, 0L) + item.getItemCount());
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

    private LocalDateTime bucketStart(ClientErrorEventTimeBucketProjection item) {
        return LocalDateTime.of(
                item.getBucketYear(),
                item.getBucketMonth(),
                item.getBucketDay(),
                item.getBucketHour() != null ? item.getBucketHour() : 0,
                0);
    }

    private LocalDateTime bucketStart(ClientErrorFeedbackTimeBucketProjection item) {
        return LocalDateTime.of(
                item.getBucketYear(),
                item.getBucketMonth(),
                item.getBucketDay(),
                item.getBucketHour() != null ? item.getBucketHour() : 0,
                0);
    }

    private List<ClientErrorTopFingerprintDto> findTopFingerprints(QueryWindow window) {
        List<ClientErrorTopFingerprintProjection> summaries = eventRepository.findTopFingerprintSummaries(
                window.from(),
                window.to(),
                PageRequest.of(0, TOP_FINGERPRINT_LIMIT));
        if (summaries.isEmpty()) {
            return List.of();
        }

        List<String> fingerprints = summaries.stream()
                .map(ClientErrorTopFingerprintProjection::getFingerprint)
                .toList();
        Map<String, ClientErrorEventSummaryProjection> latestEvents = new LinkedHashMap<>();
        for (ClientErrorEventSummaryProjection event : eventRepository.findLatestEventsByFingerprintInBetween(
                window.from(),
                window.to(),
                fingerprints)) {
            latestEvents.putIfAbsent(event.getFingerprint(), event);
        }

        List<ClientErrorTopFingerprintDto> result = new ArrayList<>();
        for (ClientErrorTopFingerprintProjection summary : summaries) {
            ClientErrorEventSummaryProjection latestEvent = latestEvents.get(summary.getFingerprint());
            if (latestEvent == null) {
                continue;
            }
            result.add(new ClientErrorTopFingerprintDto(
                    summary.getFingerprint(),
                    latestEvent.getBucket().getValue(),
                    latestEvent.getSource().getValue(),
                    latestEvent.getMessage(),
                    latestEvent.getRoute(),
                    latestEvent.getEndpoint(),
                    latestEvent.getStatusGroup(),
                    latestEvent.getMethod(),
                    summary.getEventCount(),
                    summary.getUniqueSessions(),
                    latestEvent.getEventId(),
                    ClientErrorSupport.toOffsetDateTime(latestEvent.getOccurredAt()),
                    null,
                    null));
        }
        return result;
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

    private ClientErrorEventSummaryDto toEventSummaryDto(ClientErrorEventSummaryProjection event) {
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
                notification.getChannel().getValue(),
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

    private ClientErrorSource resolveSourceFilter(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        ClientErrorSource sourceFilter = ClientErrorSource.fromCategory(source);
        return sourceFilter == ClientErrorSource.UNKNOWN ? null : sourceFilter;
    }

    private String normalizeStatusGroupFilter(String statusGroup) {
        return blankToNull(statusGroup) == null ? null : statusGroup.trim().toLowerCase();
    }

    private String normalizeRouteFilter(String route) {
        String normalized = blankToNull(route);
        if (normalized == null) {
            return null;
        }
        return blankToNull(ClientErrorSupport.normalizeRoute(normalized));
    }

    private String normalizeFingerprintFilter(String fingerprint) {
        return blankToNull(fingerprint);
    }

    private String normalizeSearchTerm(String search) {
        String normalized = blankToNull(search);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private LocalDateTime toUtcLocalDateTime(OffsetDateTime value) {
        return value == null ? null : value.withOffsetSameInstant(ClientErrorSupport.UTC).toLocalDateTime();
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record QueryWindow(LocalDateTime from, LocalDateTime to, LocalDateTime seriesStart, String granularity) {
    }

    private record LatestAlertInfo(OffsetDateTime notifiedAt, String channel) {
    }
}
