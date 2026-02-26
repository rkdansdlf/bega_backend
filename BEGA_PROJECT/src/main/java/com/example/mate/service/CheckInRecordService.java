package com.example.mate.service;

import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.entity.CheckInRecord;
import com.example.mate.entity.Party;
import com.example.mate.exception.DuplicateCheckInException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.net.URLEncoder;
import com.example.auth.service.UserService;
import com.example.mate.repository.PartyApplicationRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class CheckInRecordService {

    private static final String QR_SESSION_PREFIX = "mate:checkin:qr:";
    private static final String MANUAL_CODE_PREFIX = "mate:checkin:manual:";
    private static final String MANUAL_CODE_ACTIVE_SESSION_PREFIX = "mate:checkin:manual:active:";
    private static final Duration QR_SESSION_TTL = Duration.ofMinutes(30);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CheckInRecordRepository checkInRecordRepository;
    private final PartyRepository partyRepository;
    private final com.example.auth.repository.UserRepository userRepository;
    private final PartyApplicationRepository applicationRepository;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendBaseUrl;

    @lombok.Data
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    @lombok.Builder
    private static class QrSessionPayload {
        private Long partyId;
        private Long generatedByUserId;
        private Instant createdAt;
        private Instant expiresAt;
    }

    private enum CheckInCredentialMode {
        QR_SESSION,
        MANUAL_CODE
    }

    // 체크인
    @Transactional
    public CheckInRecordDTO.Response checkIn(CheckInRecordDTO.Request request, Principal principal) {
        if (principal == null) {
            throw new UnauthorizedAccessException("로그인이 필요합니다.");
        }

        if (request == null || request.getPartyId() == null) {
            throw new RuntimeException("partyId는 필수입니다.");
        }

        Long userId = userService.getUserIdByEmail(principal.getName());

        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(request.getPartyId()));

        if (!isPartyMember(request.getPartyId(), userId, party)) {
            throw new UnauthorizedAccessException("파티 참여자만 체크인할 수 있습니다.");
        }

        String qrSessionId = request.getQrSessionId() == null ? null : request.getQrSessionId().trim();
        String manualCode = request.getManualCode() == null ? null : request.getManualCode().trim();
        CheckInCredentialMode credentialMode = resolveCredentialMode(request.getPartyId(), qrSessionId, manualCode);
        if (credentialMode == CheckInCredentialMode.MANUAL_CODE) {
            validateManualCode(request.getPartyId(), manualCode);
        } else {
            validateQrSession(request.getPartyId(), qrSessionId);
        }

        // 중복 체크인 확인
        checkInRecordRepository.findByPartyIdAndUserId(request.getPartyId(), userId)
                .ifPresent(record -> {
                    throw new DuplicateCheckInException(request.getPartyId(), userId);
                });

        String location = (request.getLocation() == null || request.getLocation().isBlank())
                ? party.getStadium()
                : request.getLocation();

        CheckInRecord record = CheckInRecord.builder()
                .partyId(request.getPartyId())
                .userId(userId)
                .location(location)
                .build();

        CheckInRecord savedRecord = checkInRecordRepository.save(record);

        // 모든 참여자가 체크인했는지 확인
        checkAndUpdatePartyStatus(request.getPartyId());

        String userName = userRepository.findById(userId)
                .map(com.example.auth.entity.UserEntity::getName)
                .orElse("Unknown");

        return CheckInRecordDTO.Response.from(savedRecord, userName);
    }

    @Transactional
    public CheckInRecordDTO.QrSessionResponse createQrSession(CheckInRecordDTO.QrSessionRequest request,
            Principal principal) {
        if (principal == null) {
            throw new UnauthorizedAccessException("로그인이 필요합니다.");
        }
        if (request == null || request.getPartyId() == null) {
            throw new RuntimeException("partyId는 필수입니다.");
        }

        Long userId = userService.getUserIdByEmail(principal.getName());
        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(request.getPartyId()));

        if (!Objects.equals(party.getHostId(), userId)) {
            log.warn("issuer_forbidden: partyId={}, requesterUserId={}, hostId={}",
                    request.getPartyId(), userId, party.getHostId());
            throw new UnauthorizedAccessException("호스트만 QR 코드를 생성할 수 있습니다.");
        }

        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(QR_SESSION_TTL);

        QrSessionPayload payload = QrSessionPayload.builder()
                .partyId(request.getPartyId())
                .generatedByUserId(userId)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();

        try {
            redisTemplate.opsForValue().set(
                    QR_SESSION_PREFIX + sessionId,
                    objectMapper.writeValueAsString(payload),
                    QR_SESSION_TTL);
        } catch (JsonProcessingException e) {
            log.error("QR 세션 직렬화 실패: partyId={}, userId={}", request.getPartyId(), userId, e);
            throw new RuntimeException("QR 세션 생성에 실패했습니다.");
        }

        String manualCode = String.format("%04d", SECURE_RANDOM.nextInt(10000));
        redisTemplate.opsForValue().set(
                MANUAL_CODE_PREFIX + sessionId,
                manualCode,
                QR_SESSION_TTL);
        redisTemplate.opsForValue().set(
                MANUAL_CODE_ACTIVE_SESSION_PREFIX + request.getPartyId(),
                sessionId,
                QR_SESSION_TTL);

        return CheckInRecordDTO.QrSessionResponse.builder()
                .sessionId(sessionId)
                .partyId(request.getPartyId())
                .expiresAt(expiresAt)
                .checkinUrl(buildCheckInUrl(request.getPartyId(), sessionId))
                .manualCode(manualCode)
                .build();
    }

    // 파티별 체크인 기록 조회
    @Transactional(readOnly = true)
    public List<CheckInRecordDTO.Response> getCheckInsByPartyId(Long partyId) {
        return checkInRecordRepository.findByPartyId(partyId).stream()
                .map(record -> {
                    String userName = userRepository.findById(record.getUserId())
                            .map(com.example.auth.entity.UserEntity::getName)
                            .orElse("Unknown");
                    return CheckInRecordDTO.Response.from(record, userName);
                })
                .collect(Collectors.toList());
    }

    // 사용자별 체크인 기록 조회
    @Transactional(readOnly = true)
    public List<CheckInRecordDTO.Response> getCheckInsByUserId(Long userId) {
        return checkInRecordRepository.findByUserId(userId).stream()
                .map(record -> {
                    String userName = userRepository.findById(record.getUserId())
                            .map(com.example.auth.entity.UserEntity::getName)
                            .orElse("Unknown");
                    return CheckInRecordDTO.Response.from(record, userName);
                })
                .collect(Collectors.toList());
    }

    // 체크인 여부 확인
    @Transactional(readOnly = true)
    public boolean isCheckedIn(Long partyId, Principal principal) {
        if (principal == null)
            return false;
        Long userId = userService.getUserIdByEmail(principal.getName());
        return checkInRecordRepository.findByPartyIdAndUserId(partyId, userId).isPresent();
    }

    // 파티별 체크인 인원 수 조회
    @Transactional(readOnly = true)
    public long getCheckInCount(Long partyId) {
        return checkInRecordRepository.countByPartyId(partyId);
    }

    // 모든 참여자가 체크인했는지 확인하고 파티 상태 업데이트
    @Transactional
    public void checkAndUpdatePartyStatus(Long partyId) {
        Party party = partyRepository.findById(java.util.Objects.requireNonNull(partyId))
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        long checkInCount = checkInRecordRepository.countByPartyId(partyId);

        // 모든 참여자가 체크인한 경우
        if (checkInCount == party.getCurrentParticipants()) {
            party.setStatus(Party.PartyStatus.CHECKED_IN);
            partyRepository.save(party);
        }
    }

    private boolean isPartyMember(Long partyId, Long userId, Party party) {
        return party.getHostId().equals(userId) ||
                applicationRepository.findByPartyIdAndApplicantId(partyId, userId)
                        .map(com.example.mate.entity.PartyApplication::getIsApproved)
                        .orElse(false);
    }

    private CheckInCredentialMode resolveCredentialMode(Long partyId, String qrSessionId, String manualCode) {
        boolean hasQrSessionId = qrSessionId != null && !qrSessionId.isBlank();
        boolean hasManualCode = manualCode != null && !manualCode.isBlank();

        if (!hasQrSessionId && !hasManualCode) {
            log.warn("credential_missing: partyId={}", partyId);
            throw new RuntimeException("체크인 인증 정보(qrSessionId 또는 manualCode) 중 하나는 필수입니다.");
        }

        if (hasQrSessionId && hasManualCode) {
            log.warn("credential_conflict: partyId={}", partyId);
            throw new RuntimeException("체크인 인증 정보는 하나만 제공해야 합니다.");
        }

        return hasManualCode ? CheckInCredentialMode.MANUAL_CODE : CheckInCredentialMode.QR_SESSION;
    }

    private void validateQrSession(Long partyId, String qrSessionId) {
        if (qrSessionId == null || qrSessionId.isBlank()) {
            throw new RuntimeException("QR 세션 ID가 필요합니다.");
        }

        String serializedPayload = redisTemplate.opsForValue().get(QR_SESSION_PREFIX + qrSessionId);
        if (serializedPayload == null || serializedPayload.isBlank()) {
            throw new RuntimeException("유효하지 않거나 만료된 QR 세션입니다.");
        }

        QrSessionPayload payload;
        try {
            payload = objectMapper.readValue(serializedPayload, QrSessionPayload.class);
        } catch (JsonProcessingException e) {
            log.error("QR 세션 역직렬화 실패: sessionId={}", qrSessionId, e);
            throw new RuntimeException("QR 세션 정보를 읽지 못했습니다.");
        }

        Instant expiresAt = payload.getExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            redisTemplate.delete(QR_SESSION_PREFIX + qrSessionId);
            throw new RuntimeException("QR 세션이 만료되었습니다.");
        }

        if (!Objects.equals(payload.getPartyId(), partyId)) {
            throw new RuntimeException("QR 세션의 파티 정보가 일치하지 않습니다.");
        }
    }

    private void validateManualCode(Long partyId, String manualCode) {
        if (manualCode == null || manualCode.isBlank()) {
            throw new RuntimeException("수동 체크인 코드를 입력해주세요.");
        }
        String activeSessionId = redisTemplate.opsForValue().get(MANUAL_CODE_ACTIVE_SESSION_PREFIX + partyId);
        if (activeSessionId == null || activeSessionId.isBlank()) {
            log.warn("manual_code_invalid: reason=active_session_missing, partyId={}", partyId);
            throw new RuntimeException("유효하지 않거나 만료된 수동 체크인 코드입니다.");
        }

        try {
            validateQrSession(partyId, activeSessionId);
        } catch (RuntimeException e) {
            log.warn("manual_code_invalid: reason=session_invalid, partyId={}, sessionId={}", partyId, activeSessionId);
            throw new RuntimeException("유효하지 않거나 만료된 수동 체크인 코드입니다.");
        }

        String storedCode = redisTemplate.opsForValue().get(MANUAL_CODE_PREFIX + activeSessionId);
        if (storedCode == null || !storedCode.equals(manualCode)) {
            log.warn("manual_code_invalid: reason=code_mismatch, partyId={}, sessionId={}", partyId, activeSessionId);
            throw new RuntimeException("유효하지 않거나 만료된 수동 체크인 코드입니다.");
        }
    }

    private String buildCheckInUrl(Long partyId, String sessionId) {
        String baseUrl = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
                ? "http://localhost:3000"
                : frontendBaseUrl.trim();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }

        return String.format("%s/mate/%d/checkin?sessionId=%s",
                baseUrl,
                partyId,
                URLEncoder.encode(sessionId, StandardCharsets.UTF_8));
    }
}
