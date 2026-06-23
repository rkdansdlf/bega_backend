package com.example.mate.service;

import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.entity.CheckInRecord;
import com.example.mate.entity.Party;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
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
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.frontend.url:http://localhost:5176}")
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
        validateCheckInRequest(request);
        return checkInForUser(request, resolveUserId(principal));
    }

    @Transactional
    public CheckInRecordDTO.Response checkIn(CheckInRecordDTO.Request request, Long userId) {
        validateCheckInRequest(request);
        return checkInForUser(request, requireUserId(userId));
    }

    private CheckInRecordDTO.Response checkInForUser(CheckInRecordDTO.Request request, Long userId) {
        Party party = requireAccessibleParty(request.getPartyId(), userId);

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

        return toResponse(savedRecord);
    }

    @Transactional
    public CheckInRecordDTO.QrSessionResponse createQrSession(CheckInRecordDTO.QrSessionRequest request,
            Principal principal) {
        validateQrSessionRequest(request);
        return createQrSessionForUser(request, resolveUserId(principal));
    }

    @Transactional
    public CheckInRecordDTO.QrSessionResponse createQrSession(CheckInRecordDTO.QrSessionRequest request, Long userId) {
        validateQrSessionRequest(request);
        return createQrSessionForUser(request, requireUserId(userId));
    }

    private CheckInRecordDTO.QrSessionResponse createQrSessionForUser(
            CheckInRecordDTO.QrSessionRequest request,
            Long userId) {
        Party party = requireHostedParty(request.getPartyId(), userId);

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
            throw new BadRequestBusinessException("QR_SESSION_CREATE_FAILED", "QR 세션 생성에 실패했습니다.");
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
    public List<CheckInRecordDTO.Response> getCheckInsByPartyId(Long partyId, Principal principal) {
        return getCheckInsByPartyIdForUser(partyId, resolveUserId(principal));
    }

    @Transactional(readOnly = true)
    public List<CheckInRecordDTO.Response> getCheckInsByPartyId(Long partyId, Long userId) {
        return getCheckInsByPartyIdForUser(partyId, requireUserId(userId));
    }

    private List<CheckInRecordDTO.Response> getCheckInsByPartyIdForUser(Long partyId, Long requesterId) {
        Party party = partyRepository.findByIdAndHostId(partyId, requesterId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));

        return checkInRecordRepository.findByPartyId(party.getId()).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 사용자별 체크인 기록 조회
    @Transactional(readOnly = true)
    public List<CheckInRecordDTO.Response> getCheckInsByUserId(Long userId, Principal principal) {
        return getCheckInsByUserIdForRequester(userId, resolveUserId(principal));
    }

    @Transactional(readOnly = true)
    public List<CheckInRecordDTO.Response> getCheckInsByUserId(Long targetUserId, Long requesterUserId) {
        return getCheckInsByUserIdForRequester(targetUserId, requireUserId(requesterUserId));
    }

    private List<CheckInRecordDTO.Response> getCheckInsByUserIdForRequester(Long userId, Long requesterId) {
        if (!requesterId.equals(userId)) {
            throw new UnauthorizedAccessException("본인의 체크인 기록만 조회할 수 있습니다.");
        }

        return checkInRecordRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // 체크인 여부 확인
    @Transactional(readOnly = true)
    public boolean isCheckedIn(Long partyId, Principal principal) {
        if (principal == null)
            return false;
        Long userId = resolveUserId(principal);
        return checkInRecordRepository.findByPartyIdAndUserId(partyId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean isCheckedIn(Long partyId, Long userId) {
        if (userId == null) {
            return false;
        }
        return checkInRecordRepository.findByPartyIdAndUserId(partyId, userId).isPresent();
    }

    // 파티별 체크인 인원 수 조회
    @Transactional(readOnly = true)
    public long getCheckInCount(Long partyId, Principal principal) {
        return getCheckInCountForUser(partyId, resolveUserId(principal));
    }

    @Transactional(readOnly = true)
    public long getCheckInCount(Long partyId, Long userId) {
        return getCheckInCountForUser(partyId, requireUserId(userId));
    }

    private long getCheckInCountForUser(Long partyId, Long userId) {
        requireAccessibleParty(partyId, userId);

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

    private Party requireHostedParty(Long partyId, Long userId) {
        return partyRepository.findByIdAndHostId(java.util.Objects.requireNonNull(partyId), userId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private Party requireAccessibleParty(Long partyId, Long userId) {
        return partyRepository.findAccessibleByIdAndParticipantId(java.util.Objects.requireNonNull(partyId), userId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private CheckInRecordDTO.Response toResponse(CheckInRecord record) {
        com.example.auth.entity.UserEntity user = userRepository.findById(record.getUserId()).orElse(null);
        String userHandle = user != null ? user.getHandle() : null;
        String userName = user != null ? user.getName() : "Unknown";
        return CheckInRecordDTO.Response.from(record, userHandle, userName);
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AuthenticationRequiredException("로그인이 필요합니다.");
        }
        String principalName = principal.getName().trim();
        try {
            return Long.valueOf(principalName);
        } catch (NumberFormatException ignored) {
            return userService.getUserIdByEmail(principalName);
        }
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("로그인이 필요합니다.");
        }
        return userId;
    }

    private void validateCheckInRequest(CheckInRecordDTO.Request request) {
        if (request == null || request.getPartyId() == null) {
            throw new BadRequestBusinessException("INVALID_CHECK_IN_REQUEST", "partyId는 필수입니다.");
        }
    }

    private void validateQrSessionRequest(CheckInRecordDTO.QrSessionRequest request) {
        if (request == null || request.getPartyId() == null) {
            throw new BadRequestBusinessException("INVALID_CHECK_IN_REQUEST", "partyId는 필수입니다.");
        }
    }

    private CheckInCredentialMode resolveCredentialMode(Long partyId, String qrSessionId, String manualCode) {
        boolean hasQrSessionId = qrSessionId != null && !qrSessionId.isBlank();
        boolean hasManualCode = manualCode != null && !manualCode.isBlank();

        if (!hasQrSessionId && !hasManualCode) {
            log.warn("credential_missing: partyId={}", partyId);
            throw new BadRequestBusinessException(
                    "INVALID_CHECK_IN_CREDENTIAL",
                    "체크인 인증 정보(qrSessionId 또는 manualCode) 중 하나는 필수입니다.");
        }

        if (hasQrSessionId && hasManualCode) {
            log.warn("credential_conflict: partyId={}", partyId);
            throw new BadRequestBusinessException("INVALID_CHECK_IN_CREDENTIAL", "체크인 인증 정보는 하나만 제공해야 합니다.");
        }

        return hasManualCode ? CheckInCredentialMode.MANUAL_CODE : CheckInCredentialMode.QR_SESSION;
    }

    private void validateQrSession(Long partyId, String qrSessionId) {
        if (qrSessionId == null || qrSessionId.isBlank()) {
            throw new BadRequestBusinessException("INVALID_QR_SESSION", "QR 세션 ID가 필요합니다.");
        }

        String serializedPayload = redisTemplate.opsForValue().get(QR_SESSION_PREFIX + qrSessionId);
        if (serializedPayload == null || serializedPayload.isBlank()) {
            throw new BadRequestBusinessException("INVALID_QR_SESSION", "유효하지 않거나 만료된 QR 세션입니다.");
        }

        QrSessionPayload payload;
        try {
            payload = objectMapper.readValue(serializedPayload, QrSessionPayload.class);
        } catch (JsonProcessingException e) {
            log.error("QR 세션 역직렬화 실패: sessionId={}", qrSessionId, e);
            throw new BadRequestBusinessException("INVALID_QR_SESSION", "QR 세션 정보를 읽지 못했습니다.");
        }

        Instant expiresAt = payload.getExpiresAt();
        if (expiresAt == null || expiresAt.isBefore(Instant.now())) {
            redisTemplate.delete(QR_SESSION_PREFIX + qrSessionId);
            throw new BadRequestBusinessException("INVALID_QR_SESSION", "QR 세션이 만료되었습니다.");
        }

        if (!Objects.equals(payload.getPartyId(), partyId)) {
            throw new BadRequestBusinessException("INVALID_QR_SESSION", "QR 세션의 파티 정보가 일치하지 않습니다.");
        }
    }

    private void validateManualCode(Long partyId, String manualCode) {
        if (manualCode == null || manualCode.isBlank()) {
            throw new BadRequestBusinessException("INVALID_MANUAL_CHECK_IN_CODE", "수동 체크인 코드를 입력해주세요.");
        }
        String activeSessionId = redisTemplate.opsForValue().get(MANUAL_CODE_ACTIVE_SESSION_PREFIX + partyId);
        if (activeSessionId == null || activeSessionId.isBlank()) {
            log.warn("manual_code_invalid: reason=active_session_missing, partyId={}", partyId);
            throw new BadRequestBusinessException("INVALID_MANUAL_CHECK_IN_CODE", "유효하지 않거나 만료된 수동 체크인 코드입니다.");
        }

        try {
            validateQrSession(partyId, activeSessionId);
        } catch (BadRequestBusinessException e) {
            log.warn("manual_code_invalid: reason=session_invalid, partyId={}, sessionId={}", partyId, activeSessionId);
            throw new BadRequestBusinessException("INVALID_MANUAL_CHECK_IN_CODE", "유효하지 않거나 만료된 수동 체크인 코드입니다.");
        }

        String storedCode = redisTemplate.opsForValue().get(MANUAL_CODE_PREFIX + activeSessionId);
        if (storedCode == null || !storedCode.equals(manualCode)) {
            log.warn("manual_code_invalid: reason=code_mismatch, partyId={}, sessionId={}", partyId, activeSessionId);
            throw new BadRequestBusinessException("INVALID_MANUAL_CHECK_IN_CODE", "유효하지 않거나 만료된 수동 체크인 코드입니다.");
        }
    }

    private String buildCheckInUrl(Long partyId, String sessionId) {
        String baseUrl = (frontendBaseUrl == null || frontendBaseUrl.isBlank())
                ? "http://localhost:5176"
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
