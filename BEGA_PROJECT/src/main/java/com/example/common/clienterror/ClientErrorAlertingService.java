package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientErrorAlertingService {

    private static final int FINGERPRINT_QUERY_BATCH_SIZE = 500;
    private static final String API_ALERT_STATUS_GROUP = "5xx";

    private final ClientErrorEventRepository eventRepository;
    private final ClientErrorFeedbackRepository feedbackRepository;
    private final ClientErrorAlertNotificationRepository alertNotificationRepository;
    private final ClientErrorMonitoringProperties monitoringProperties;
    private final List<ClientErrorAlertSender> alertSenders;

    @Value("${app.frontend.url:http://localhost:5176}")
    private String frontendUrl;

    @Scheduled(fixedRateString = "${app.client-error-monitoring.alerts.poll-interval-ms:60000}")
    @Transactional
    public void evaluateAlerts() {
        ClientErrorMonitoringProperties.Alerts alerts = monitoringProperties.getAlerts();
        if (!alerts.isEnabled()) {
            return;
        }

        ClientErrorAlertSender sender = resolveSender(alerts);
        if (sender == null || !sender.isConfigured(alerts)) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ClientErrorSupport.UTC);
        LocalDateTime windowStart = now.minusMinutes(Math.max(alerts.getWindowMinutes(), 1));
        Map<String, AlertCandidate> candidates = loadCandidates(windowStart, alerts);
        if (candidates.isEmpty()) {
            return;
        }

        LocalDateTime cooldownCutoff = now.minusMinutes(Math.max(alerts.getCooldownMinutes(), 1));
        List<AlertCandidate> alertableCandidates = candidates.values().stream()
                .filter(candidate -> candidate.shouldAlert(alerts))
                .toList();
        if (alertableCandidates.isEmpty()) {
            return;
        }
        Set<String> cooledDownFingerprints = loadCooledDownFingerprints(alertableCandidates, cooldownCutoff);

        for (AlertCandidate candidate : alertableCandidates) {
            if (cooledDownFingerprints.contains(candidate.fingerprint())) {
                continue;
            }

            ClientErrorAlertDeliveryResult delivery = sender.send(
                    buildAlertPayload(candidate, alerts.getWindowMinutes()),
                    alerts);
            alertNotificationRepository.save(ClientErrorAlertNotificationEntity.builder()
                    .fingerprint(candidate.fingerprint())
                    .bucket(candidate.bucket())
                    .source(candidate.source())
                    .channel(sender.channel())
                    .route(candidate.route())
                    .statusGroup(candidate.statusGroup())
                    .observedCount(candidate.count())
                    .thresholdCount(candidate.threshold(alerts))
                    .windowMinutes(alerts.getWindowMinutes())
                    .latestEventId(candidate.latestEventId())
                    .latestMessage(candidate.latestMessage())
                    .latestOccurredAt(candidate.latestOccurredAt())
                    .deliveryStatus(delivery.status())
                    .failureReason(delivery.failureReason())
                    .build());
        }
    }

    @Scheduled(cron = "${app.client-error-monitoring.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void cleanupExpiredData() {
        int retentionDays = Math.max(monitoringProperties.getRetentionDays(), 1);
        LocalDateTime cutoff = LocalDateTime.now(ClientErrorSupport.UTC).minusDays(retentionDays);

        long deletedFeedback = feedbackRepository.deleteByOccurredAtBefore(cutoff);
        long deletedEvents = eventRepository.deleteByOccurredAtBefore(cutoff);
        long deletedAlerts = alertNotificationRepository.deleteByNotifiedAtBefore(cutoff);

        if (deletedFeedback > 0 || deletedEvents > 0 || deletedAlerts > 0) {
            log.info("Client error cleanup completed deletedEvents={} deletedFeedback={} deletedAlerts={}",
                    deletedEvents, deletedFeedback, deletedAlerts);
        }
    }

    private Map<String, AlertCandidate> loadCandidates(
            LocalDateTime windowStart,
            ClientErrorMonitoringProperties.Alerts alerts) {
        List<ClientErrorAlertCandidateProjection> summaries = eventRepository.findAlertCandidateSummaries(
                windowStart,
                ClientErrorBucket.RUNTIME,
                ClientErrorBucket.API,
                API_ALERT_STATUS_GROUP,
                minimumAlertThreshold(alerts),
                PageRequest.of(0, normalizedMaxCandidates(alerts)));
        if (summaries.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> fingerprints = summaries.stream()
                .map(ClientErrorAlertCandidateProjection::getFingerprint)
                .toList();
        Map<String, ClientErrorEventSummaryProjection> latestEvents = latestEventsByFingerprint(windowStart, fingerprints);
        Map<String, AlertCandidate> candidates = new LinkedHashMap<>();
        for (ClientErrorAlertCandidateProjection summary : summaries) {
            ClientErrorEventSummaryProjection latestEvent = latestEvents.get(summary.getFingerprint());
            if (latestEvent == null) {
                continue;
            }
            candidates.put(summary.getFingerprint(), new AlertCandidate(
                    summary.getFingerprint(),
                    summary.getObservedCount(),
                    latestEvent));
        }
        return candidates;
    }

    private long minimumAlertThreshold(ClientErrorMonitoringProperties.Alerts alerts) {
        return Math.max(Math.min(alerts.getRuntimeThreshold(), alerts.getApi5xxThreshold()), 1);
    }

    private int normalizedMaxCandidates(ClientErrorMonitoringProperties.Alerts alerts) {
        return Math.max(alerts.getMaxCandidates(), 1);
    }

    private Map<String, ClientErrorEventSummaryProjection> latestEventsByFingerprint(
            LocalDateTime windowStart,
            List<String> fingerprints) {
        Map<String, ClientErrorEventSummaryProjection> latestEvents = new LinkedHashMap<>();
        for (int start = 0; start < fingerprints.size(); start += FINGERPRINT_QUERY_BATCH_SIZE) {
            int end = Math.min(start + FINGERPRINT_QUERY_BATCH_SIZE, fingerprints.size());
            List<ClientErrorEventSummaryProjection> batch = eventRepository.findLatestAlertEventsByFingerprint(
                    windowStart,
                    ClientErrorBucket.RUNTIME,
                    ClientErrorBucket.API,
                    API_ALERT_STATUS_GROUP,
                    fingerprints.subList(start, end));
            for (ClientErrorEventSummaryProjection event : batch) {
                latestEvents.putIfAbsent(event.getFingerprint(), event);
            }
        }
        return latestEvents;
    }

    private Set<String> loadCooledDownFingerprints(
            List<AlertCandidate> candidates,
            LocalDateTime cooldownCutoff) {
        if (candidates.isEmpty()) {
            return Collections.emptySet();
        }
        List<String> fingerprints = candidates.stream()
                .map(AlertCandidate::fingerprint)
                .toList();
        Set<String> cooledDownFingerprints = new HashSet<>();
        for (int start = 0; start < fingerprints.size(); start += FINGERPRINT_QUERY_BATCH_SIZE) {
            int end = Math.min(start + FINGERPRINT_QUERY_BATCH_SIZE, fingerprints.size());
            cooledDownFingerprints.addAll(alertNotificationRepository.findFingerprintsNotifiedAfter(
                    fingerprints.subList(start, end),
                    cooldownCutoff));
        }
        return cooledDownFingerprints;
    }

    private ClientErrorAlertPayload buildAlertPayload(AlertCandidate candidate, int windowMinutes) {
        String adminUrl = frontendUrl.endsWith("/") ? frontendUrl + "admin" : frontendUrl + "/admin";
        return new ClientErrorAlertPayload(
                candidate.bucket(),
                candidate.source(),
                candidate.count(),
                windowMinutes,
                candidate.route(),
                candidate.statusGroup(),
                candidate.latestEventId(),
                candidate.fingerprint(),
                candidate.latestMessage(),
                adminUrl);
    }

    private ClientErrorAlertSender resolveSender(ClientErrorMonitoringProperties.Alerts alerts) {
        ClientErrorAlertChannel configuredChannel = alerts.getChannel() != null
                ? alerts.getChannel()
                : ClientErrorAlertChannel.TELEGRAM;

        for (ClientErrorAlertSender sender : alertSenders) {
            if (sender.channel() == configuredChannel) {
                return sender;
            }
        }

        log.warn("No client error alert sender is registered for channel={}", configuredChannel);
        return null;
    }

    private static final class AlertCandidate {
        private final String fingerprint;
        private long count;
        private ClientErrorBucket bucket;
        private ClientErrorSource source;
        private String route = "/";
        private String statusGroup = "none";
        private String latestEventId;
        private String latestMessage;
        private LocalDateTime latestOccurredAt;

        private AlertCandidate(String fingerprint, long count, ClientErrorEventSummaryProjection latestEvent) {
            this.fingerprint = fingerprint;
            this.count = count;
            this.bucket = latestEvent.getBucket();
            this.source = latestEvent.getSource();
            this.route = latestEvent.getRoute();
            this.statusGroup = latestEvent.getStatusGroup();
            this.latestEventId = latestEvent.getEventId();
            this.latestMessage = latestEvent.getMessage();
            this.latestOccurredAt = latestEvent.getOccurredAt();
        }

        boolean shouldAlert(ClientErrorMonitoringProperties.Alerts alerts) {
            if (bucket == ClientErrorBucket.RUNTIME) {
                return count >= alerts.getRuntimeThreshold();
            }
            if (bucket == ClientErrorBucket.API) {
                return API_ALERT_STATUS_GROUP.equals(statusGroup) && count >= alerts.getApi5xxThreshold();
            }
            return false;
        }

        int threshold(ClientErrorMonitoringProperties.Alerts alerts) {
            if (bucket == ClientErrorBucket.RUNTIME) {
                return alerts.getRuntimeThreshold();
            }
            return alerts.getApi5xxThreshold();
        }

        String fingerprint() {
            return fingerprint;
        }

        long count() {
            return count;
        }

        ClientErrorBucket bucket() {
            return bucket;
        }

        ClientErrorSource source() {
            return source;
        }

        String route() {
            return route;
        }

        String statusGroup() {
            return statusGroup;
        }

        String latestEventId() {
            return latestEventId;
        }

        String latestMessage() {
            return latestMessage;
        }

        LocalDateTime latestOccurredAt() {
            return latestOccurredAt;
        }
    }
}
