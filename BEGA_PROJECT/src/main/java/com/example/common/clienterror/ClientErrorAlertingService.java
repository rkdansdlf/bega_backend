package com.example.common.clienterror;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientErrorAlertingService {

    private final ClientErrorEventRepository eventRepository;
    private final ClientErrorFeedbackRepository feedbackRepository;
    private final ClientErrorAlertNotificationRepository alertNotificationRepository;
    private final ClientErrorMonitoringProperties monitoringProperties;
    private final List<ClientErrorAlertSender> alertSenders;

    @Value("${app.frontend.url:http://localhost:3000}")
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
        List<ClientErrorEventEntity> recentEvents =
                eventRepository.findByOccurredAtGreaterThanEqualOrderByOccurredAtAsc(windowStart);
        Map<String, AlertCandidate> candidates = buildCandidates(recentEvents);
        LocalDateTime cooldownCutoff = now.minusMinutes(Math.max(alerts.getCooldownMinutes(), 1));

        for (AlertCandidate candidate : candidates.values()) {
            if (!candidate.shouldAlert(alerts)) {
                continue;
            }
            if (alertNotificationRepository.existsByFingerprintAndNotifiedAtAfter(candidate.fingerprint(), cooldownCutoff)) {
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

    private Map<String, AlertCandidate> buildCandidates(List<ClientErrorEventEntity> recentEvents) {
        Map<String, AlertCandidate> candidates = new LinkedHashMap<>();
        for (ClientErrorEventEntity event : recentEvents) {
            if (event.getBucket() == ClientErrorBucket.FEEDBACK) {
                continue;
            }
            candidates.computeIfAbsent(event.getFingerprint(), key -> new AlertCandidate(event.getFingerprint()))
                    .add(event);
        }
        return candidates;
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

        private AlertCandidate(String fingerprint) {
            this.fingerprint = fingerprint;
        }

        void add(ClientErrorEventEntity event) {
            count += 1;
            if (latestOccurredAt == null || event.getOccurredAt().isAfter(latestOccurredAt)) {
                bucket = event.getBucket();
                source = event.getSource();
                route = event.getRoute();
                statusGroup = event.getStatusGroup();
                latestEventId = event.getEventId();
                latestMessage = event.getMessage();
                latestOccurredAt = event.getOccurredAt();
            }
        }

        boolean shouldAlert(ClientErrorMonitoringProperties.Alerts alerts) {
            if (bucket == ClientErrorBucket.RUNTIME) {
                return count >= alerts.getRuntimeThreshold();
            }
            if (bucket == ClientErrorBucket.API) {
                return "5xx".equals(statusGroup) && count >= alerts.getApi5xxThreshold();
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
