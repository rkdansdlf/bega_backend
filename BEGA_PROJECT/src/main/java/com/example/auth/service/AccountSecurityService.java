package com.example.auth.service;

import com.example.auth.entity.AccountSecurityEvent;
import com.example.auth.entity.RefreshToken;
import com.example.auth.entity.TrustedDevice;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountSecurityEventRepository;
import com.example.auth.repository.TrustedDeviceRepository;
import com.example.common.exception.NotFoundBusinessException;
import com.example.common.web.ClientIpResolver;
import com.example.mypage.dto.AccountSecurityEventDto;
import com.example.mypage.dto.TrustedDeviceDto;
import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.HttpServletRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountSecurityService {

    private static final int SECURITY_EVENT_RETENTION_DAYS = 90;

    private final AccountSecurityEventRepository accountSecurityEventRepository;
    private final TrustedDeviceRepository trustedDeviceRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;
    private final ClientIpResolver clientIpResolver;

    @Transactional
    public void handleSuccessfulLogin(UserEntity user, HttpServletRequest request) {
        if (user == null || user.getId() == null || request == null) {
            return;
        }

        try {
            DeviceMetadata metadata = resolveDeviceMetadata(request);
            recordEvent(user.getId(), AccountSecurityEvent.EventType.LOGIN_SUCCESS,
                    buildLoginMessage(metadata), metadata);

            TrustedDevice trustedDevice = trustedDeviceRepository
                    .findByUserIdAndFingerprintAndRevokedAtIsNull(user.getId(), metadata.fingerprint())
                    .orElse(null);

            if (trustedDevice == null) {
                TrustedDevice revokedTrustedDevice = trustedDeviceRepository
                        .findByUserIdAndFingerprint(user.getId(), metadata.fingerprint())
                        .orElse(null);

                if (revokedTrustedDevice != null) {
                    revokedTrustedDevice.setRevokedAt(null);
                    revokedTrustedDevice.setDeviceLabel(metadata.deviceLabel());
                    revokedTrustedDevice.setDeviceType(metadata.deviceType());
                    revokedTrustedDevice.setBrowser(metadata.browser());
                    revokedTrustedDevice.setOs(metadata.os());
                    revokedTrustedDevice.setLastSeenAt(metadata.now());
                    revokedTrustedDevice.setLastLoginAt(metadata.now());
                    revokedTrustedDevice.setLastIp(metadata.ip());
                    trustedDeviceRepository.save(revokedTrustedDevice);
                } else {
                    trustedDeviceRepository.save(TrustedDevice.builder()
                            .userId(user.getId())
                            .fingerprint(metadata.fingerprint())
                            .deviceLabel(metadata.deviceLabel())
                            .deviceType(metadata.deviceType())
                            .browser(metadata.browser())
                            .os(metadata.os())
                            .firstSeenAt(metadata.now())
                            .lastSeenAt(metadata.now())
                            .lastLoginAt(metadata.now())
                            .lastIp(metadata.ip())
                            .build());
                }

                recordEvent(user.getId(), AccountSecurityEvent.EventType.NEW_DEVICE_LOGIN,
                        buildNewDeviceMessage(metadata), metadata);
                notifyNewDeviceLogin(user, metadata);
                return;
            }

            trustedDevice.setDeviceLabel(metadata.deviceLabel());
            trustedDevice.setDeviceType(metadata.deviceType());
            trustedDevice.setBrowser(metadata.browser());
            trustedDevice.setOs(metadata.os());
            trustedDevice.setLastSeenAt(metadata.now());
            trustedDevice.setLastLoginAt(metadata.now());
            trustedDevice.setLastIp(metadata.ip());
            trustedDeviceRepository.save(trustedDevice);
        } catch (RuntimeException e) {
            log.warn("Failed to handle successful login security bookkeeping for userId={}", user.getId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<AccountSecurityEventDto> getSecurityEvents(Long userId) {
        return accountSecurityEventRepository.findTop20ByUserIdOrderByOccurredAtDesc(userId).stream()
                .map(AccountSecurityEventDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TrustedDeviceDto> getTrustedDevices(Long userId) {
        return trustedDeviceRepository.findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(userId).stream()
                .map(TrustedDeviceDto::from)
                .toList();
    }

    @Transactional
    public void revokeTrustedDevice(Long userId, Long deviceId) {
        TrustedDevice trustedDevice = trustedDeviceRepository.findByIdAndUserIdAndRevokedAtIsNull(deviceId, userId)
                .orElseThrow(() -> new NotFoundBusinessException("TRUSTED_DEVICE_NOT_FOUND", "신뢰 기기 정보를 찾을 수 없습니다."));

        trustedDevice.setRevokedAt(LocalDateTime.now());
        trustedDeviceRepository.save(trustedDevice);

        recordEvent(userId, AccountSecurityEvent.EventType.TRUSTED_DEVICE_REMOVED,
                "신뢰 기기가 제거되었습니다.", toMetadata(trustedDevice, LocalDateTime.now()));
    }

    @Transactional
    public void recordPasswordChanged(Long userId) {
        recordEvent(userId, AccountSecurityEvent.EventType.PASSWORD_CHANGED,
                "비밀번호가 변경되었습니다.", null);
    }

    @Transactional
    public void recordProviderLinked(Long userId, String provider) {
        recordEvent(userId, AccountSecurityEvent.EventType.PROVIDER_LINKED,
                String.format("%s 계정이 연동되었습니다.", normalizeProviderLabel(provider)), null);
    }

    @Transactional
    public void recordProviderUnlinked(Long userId, String provider) {
        recordEvent(userId, AccountSecurityEvent.EventType.PROVIDER_UNLINKED,
                String.format("%s 계정 연동이 해제되었습니다.", normalizeProviderLabel(provider)), null);
    }

    @Transactional
    public void recordSessionRevoked(Long userId, RefreshToken refreshToken) {
        LocalDateTime now = LocalDateTime.now();
        recordEvent(userId, AccountSecurityEvent.EventType.SESSION_REVOKED,
                String.format("%s 세션이 종료되었습니다.", normalizeText(refreshToken == null ? null : refreshToken.getDeviceLabel(), "선택한")),
                refreshToken == null ? null : new DeviceMetadata(
                        fingerprintOf(
                                normalizeText(refreshToken.getDeviceType(), "desktop"),
                                normalizeText(refreshToken.getDeviceLabel(), "알 수 없는 기기"),
                                normalizeText(refreshToken.getBrowser(), "Unknown"),
                                normalizeText(refreshToken.getOs(), "Unknown")),
                        normalizeText(refreshToken.getDeviceType(), "desktop"),
                        normalizeText(refreshToken.getDeviceLabel(), "알 수 없는 기기"),
                        normalizeText(refreshToken.getBrowser(), "Unknown"),
                        normalizeText(refreshToken.getOs(), "Unknown"),
                        normalizeText(refreshToken.getIp(), "unknown"),
                        now));
    }

    @Transactional
    public void recordOtherSessionsRevoked(Long userId, int count) {
        recordEvent(userId, AccountSecurityEvent.EventType.OTHER_SESSIONS_REVOKED,
                String.format("다른 기기 세션 %d개가 종료되었습니다.", count), null);
    }

    @Transactional
    public void recordAccountDeletionScheduled(Long userId, LocalDateTime scheduledFor) {
        String message = scheduledFor == null
                ? "계정 삭제가 예약되었습니다."
                : String.format("계정 삭제가 %s까지 유예 상태로 예약되었습니다.", scheduledFor);
        recordEvent(userId, AccountSecurityEvent.EventType.ACCOUNT_DELETION_SCHEDULED, message, null);
    }

    @Transactional
    public void recordAccountDeletionCancelled(Long userId) {
        recordEvent(userId, AccountSecurityEvent.EventType.ACCOUNT_DELETION_CANCELLED,
                "계정 삭제 예약이 취소되었습니다.", null);
    }

    @Transactional
    public void cleanupOldSecurityEvents() {
        accountSecurityEventRepository.deleteByOccurredAtBefore(LocalDateTime.now().minusDays(SECURITY_EVENT_RETENTION_DAYS));
    }

    private void notifyNewDeviceLogin(UserEntity user, DeviceMetadata metadata) {
        try {
            notificationService.createNotification(
                    user.getId(),
                    Notification.NotificationType.NEW_DEVICE_LOGIN,
                    "새 기기 로그인 감지",
                    String.format("%s에서 로그인이 감지되었습니다. IP: %s", metadata.deviceLabel(), metadata.ip()),
                    null);
        } catch (RuntimeException e) {
            log.warn("Failed to create in-app notification for new device login userId={}", user.getId(), e);
        }

        try {
            emailService.sendNewDeviceLoginEmail(
                    user.getEmail(),
                    metadata.deviceLabel(),
                    metadata.browser(),
                    metadata.os(),
                    metadata.ip());
        } catch (RuntimeException e) {
            log.warn("Failed to send new device email userId={}", user.getId(), e);
        }
    }

    private void recordEvent(Long userId, AccountSecurityEvent.EventType eventType, String message, DeviceMetadata metadata) {
        if (userId == null || eventType == null || message == null || message.isBlank()) {
            return;
        }

        try {
            accountSecurityEventRepository.save(AccountSecurityEvent.builder()
                    .userId(userId)
                    .occurredAt(metadata == null ? LocalDateTime.now() : metadata.now())
                    .eventType(eventType)
                    .deviceLabel(metadata == null ? null : metadata.deviceLabel())
                    .deviceType(metadata == null ? null : metadata.deviceType())
                    .browser(metadata == null ? null : metadata.browser())
                    .os(metadata == null ? null : metadata.os())
                    .ip(metadata == null ? null : metadata.ip())
                    .message(message)
                    .build());
        } catch (RuntimeException e) {
            log.warn("Failed to persist account security event userId={}, type={}", userId, eventType, e);
        }
    }

    private String buildLoginMessage(DeviceMetadata metadata) {
        return String.format("%s에서 로그인되었습니다.", metadata.deviceLabel());
    }

    private String buildNewDeviceMessage(DeviceMetadata metadata) {
        return String.format("새 기기 로그인 감지: %s · %s / %s", metadata.deviceLabel(), metadata.browser(), metadata.os());
    }

    private DeviceMetadata resolveDeviceMetadata(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        String deviceType = resolveDeviceType(userAgent);
        String deviceLabel = resolveDeviceLabel(userAgent, deviceType);
        String browser = resolveBrowser(userAgent);
        String os = resolveOs(userAgent);
        String ip = clientIpResolver.resolveOrUnknown(request);
        LocalDateTime now = LocalDateTime.now();
        return new DeviceMetadata(
                fingerprintOf(deviceType, deviceLabel, browser, os),
                deviceType,
                deviceLabel,
                browser,
                os,
                ip,
                now);
    }

    private DeviceMetadata toMetadata(TrustedDevice trustedDevice, LocalDateTime now) {
        return new DeviceMetadata(
                fingerprintOf(
                        normalizeText(trustedDevice.getDeviceType(), "desktop"),
                        normalizeText(trustedDevice.getDeviceLabel(), "알 수 없는 기기"),
                        normalizeText(trustedDevice.getBrowser(), "Unknown"),
                        normalizeText(trustedDevice.getOs(), "Unknown")),
                normalizeText(trustedDevice.getDeviceType(), "desktop"),
                normalizeText(trustedDevice.getDeviceLabel(), "알 수 없는 기기"),
                normalizeText(trustedDevice.getBrowser(), "Unknown"),
                normalizeText(trustedDevice.getOs(), "Unknown"),
                normalizeText(trustedDevice.getLastIp(), "unknown"),
                now);
    }

    private String fingerprintOf(String deviceType, String deviceLabel, String browser, String os) {
        String source = String.join("|",
                normalizeFingerprintPart(deviceType),
                normalizeFingerprintPart(deviceLabel),
                normalizeFingerprintPart(browser),
                normalizeFingerprintPart(os));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Failed to create device fingerprint", e);
        }
    }

    private String normalizeFingerprintPart(String value) {
        return normalizeText(value, "unknown").trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeProviderLabel(String provider) {
        if (provider == null || provider.isBlank()) {
            return "소셜";
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "google" -> "Google";
            case "kakao" -> "Kakao";
            case "naver" -> "Naver";
            default -> normalized;
        };
    }

    private String resolveDeviceType(String userAgent) {
        if (userAgent == null) {
            return "desktop";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("ipad") || ua.contains("tablet")) {
            return "tablet";
        }
        if (ua.contains("mobile") || ua.contains("iphone") || ua.contains("android")) {
            return "mobile";
        }
        return "desktop";
    }

    private String resolveDeviceLabel(String userAgent, String deviceType) {
        if (userAgent == null || userAgent.isBlank()) {
            return switch (deviceType) {
                case "mobile" -> "모바일 기기";
                case "tablet" -> "태블릿";
                default -> "데스크톱";
            };
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone")) {
            return "iPhone";
        }
        if (ua.contains("ipad")) {
            return "iPad";
        }
        if (ua.contains("android")) {
            return deviceType.equals("tablet") ? "Android 태블릿" : "Android 기기";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "Mac";
        }
        if (ua.contains("windows")) {
            return "Windows PC";
        }
        if (ua.contains("linux")) {
            return "Linux PC";
        }
        return switch (deviceType) {
            case "mobile" -> "모바일 기기";
            case "tablet" -> "태블릿";
            default -> "데스크톱";
        };
    }

    private String resolveBrowser(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("edg/")) {
            return "Edge";
        }
        if (ua.contains("whale/")) {
            return "Whale";
        }
        if (ua.contains("chrome/") && !ua.contains("edg/")) {
            return "Chrome";
        }
        if (ua.contains("safari/") && !ua.contains("chrome/")) {
            return "Safari";
        }
        if (ua.contains("firefox/")) {
            return "Firefox";
        }
        return "Unknown";
    }

    private String resolveOs(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return "Unknown";
        }

        String ua = userAgent.toLowerCase(Locale.ROOT);
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ios")) {
            return "iOS";
        }
        if (ua.contains("android")) {
            return "Android";
        }
        if (ua.contains("windows")) {
            return "Windows";
        }
        if (ua.contains("mac os x") || ua.contains("macintosh")) {
            return "macOS";
        }
        if (ua.contains("linux")) {
            return "Linux";
        }
        return "Unknown";
    }

    private String normalizeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private record DeviceMetadata(
            String fingerprint,
            String deviceType,
            String deviceLabel,
            String browser,
            String os,
            String ip,
            LocalDateTime now) {
    }
}
