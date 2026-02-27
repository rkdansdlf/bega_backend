package com.example.mate.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.UserService;
import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.entity.CheckInRecord;
import com.example.mate.entity.Party;
import com.example.mate.exception.UnauthorizedAccessException;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CheckInRecordService tests")
class CheckInRecordServiceTest {

    private static final String QR_SESSION_PREFIX = "mate:checkin:qr:";
    private static final String MANUAL_CODE_PREFIX = "mate:checkin:manual:";
    private static final String MANUAL_CODE_ACTIVE_SESSION_PREFIX = "mate:checkin:manual:active:";

    @Mock
    private CheckInRecordRepository checkInRecordRepository;

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartyApplicationRepository applicationRepository;

    @Mock
    private UserService userService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CheckInRecordService checkInRecordService;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
    }

    @Test
    @DisplayName("createQrSession stores QR payload, manual code, and active session pointer")
    void createQrSession_storesPayloadAndManualCode() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        doNothing().when(valueOperations).set(anyString(), anyString(), any(java.time.Duration.class));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));

        CheckInRecordDTO.QrSessionResponse response = checkInRecordService
                .createQrSession(CheckInRecordDTO.QrSessionRequest.builder().partyId(1L).build(), principal);

        assertThat(response.getPartyId()).isEqualTo(1L);
        assertThat(response.getSessionId()).isNotBlank();
        assertThat(response.getCheckinUrl()).contains("/mate/1/checkin?sessionId=");
        assertThat(response.getExpiresAt()).isAfter(Instant.now());
        assertThat(response.getManualCode()).matches("\\d{4}");
        verify(valueOperations).set(eq(QR_SESSION_PREFIX + response.getSessionId()), anyString(),
                eq(Duration.ofMinutes(30)));
        verify(valueOperations).set(eq(MANUAL_CODE_PREFIX + response.getSessionId()), eq(response.getManualCode()),
                eq(Duration.ofMinutes(30)));
        verify(valueOperations).set(eq(MANUAL_CODE_ACTIVE_SESSION_PREFIX + response.getPartyId()),
                eq(response.getSessionId()),
                eq(Duration.ofMinutes(30)));
    }

    @Test
    @DisplayName("createQrSession rejects non-host issuer")
    void createQrSession_rejectsNonHostIssuer() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 55L);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));

        assertThatThrownBy(() -> checkInRecordService.createQrSession(
                CheckInRecordDTO.QrSessionRequest.builder().partyId(1L).build(), principal))
                .isInstanceOf(UnauthorizedAccessException.class)
                .hasMessageContaining("호스트만 QR 코드를 생성할 수 있습니다.");
    }

    @Test
    @DisplayName("checkIn rejects request without credentials")
    void checkIn_rejectsMissingCredentials() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .build();

        assertThatThrownBy(() -> checkInRecordService.checkIn(request, principal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("중 하나는 필수");
    }

    @Test
    @DisplayName("checkIn rejects request with both qrSessionId and manualCode")
    void checkIn_rejectsCredentialConflict() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .qrSessionId("session-1")
                .manualCode("1234")
                .build();

        assertThatThrownBy(() -> checkInRecordService.checkIn(request, principal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("하나만 제공");
    }

    @Test
    @DisplayName("checkIn rejects mismatched qrSession party")
    void checkIn_rejectsMismatchedQrSessionParty() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        String sessionId = "session-1";
        String serializedPayload = "{\"partyId\":2,\"generatedByUserId\":10,"
                + "\"createdAt\":\"2026-02-20T00:00:00Z\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));
        when(valueOperations.get(QR_SESSION_PREFIX + sessionId)).thenReturn(serializedPayload);

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .qrSessionId(sessionId)
                .build();

        assertThatThrownBy(() -> checkInRecordService.checkIn(request, principal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("파티 정보가 일치하지 않습니다");
    }

    @Test
    @DisplayName("checkIn succeeds with active manual code bound to current session")
    void checkIn_succeedsWithManualCode() throws Exception {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        String sessionId = "active-session";
        String serializedPayload = objectMapper.writeValueAsString(java.util.Map.of(
                "partyId", 1L,
                "generatedByUserId", 10L,
                "createdAt", "2026-02-20T00:00:00Z",
                "expiresAt", "2099-01-01T00:00:00Z"));

        CheckInRecord savedRecord = CheckInRecord.builder()
                .id(100L)
                .partyId(1L)
                .userId(10L)
                .location("잠실야구장")
                .checkedInAt(LocalDateTime.now())
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));
        when(valueOperations.get(MANUAL_CODE_ACTIVE_SESSION_PREFIX + 1L)).thenReturn(sessionId);
        when(valueOperations.get(QR_SESSION_PREFIX + sessionId)).thenReturn(serializedPayload);
        when(valueOperations.get(MANUAL_CODE_PREFIX + sessionId)).thenReturn("1234");
        when(checkInRecordRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(checkInRecordRepository.save(any(CheckInRecord.class))).thenReturn(savedRecord);
        when(checkInRecordRepository.countByPartyId(1L)).thenReturn(1L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(UserEntity.builder().id(10L).name("호스트").build()));

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .manualCode("1234")
                .build();

        CheckInRecordDTO.Response response = checkInRecordService.checkIn(request, principal);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUserId()).isEqualTo(10L);
        assertThat(response.getUserName()).isEqualTo("호스트");
    }

    @Test
    @DisplayName("checkIn rejects invalid manual code")
    void checkIn_rejectsInvalidManualCode() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        String sessionId = "active-session";
        String serializedPayload = "{\"partyId\":1,\"generatedByUserId\":10,"
                + "\"createdAt\":\"2026-02-20T00:00:00Z\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));
        when(valueOperations.get(MANUAL_CODE_ACTIVE_SESSION_PREFIX + 1L)).thenReturn(sessionId);
        when(valueOperations.get(QR_SESSION_PREFIX + sessionId)).thenReturn(serializedPayload);
        when(valueOperations.get(MANUAL_CODE_PREFIX + sessionId)).thenReturn("9999");

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .manualCode("1234")
                .build();

        assertThatThrownBy(() -> checkInRecordService.checkIn(request, principal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("유효하지 않거나 만료된 수동 체크인 코드");
    }

    @Test
    @DisplayName("checkIn rejects manual code when active session is expired")
    void checkIn_rejectsExpiredManualSession() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        String sessionId = "expired-session";
        String serializedPayload = "{\"partyId\":1,\"generatedByUserId\":10,"
                + "\"createdAt\":\"2026-02-20T00:00:00Z\",\"expiresAt\":\"2020-01-01T00:00:00Z\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));
        when(valueOperations.get(MANUAL_CODE_ACTIVE_SESSION_PREFIX + 1L)).thenReturn(sessionId);
        when(valueOperations.get(QR_SESSION_PREFIX + sessionId)).thenReturn(serializedPayload);

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .manualCode("1234")
                .build();

        assertThatThrownBy(() -> checkInRecordService.checkIn(request, principal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("유효하지 않거나 만료된 수동 체크인 코드");
    }

    @Test
    @DisplayName("checkIn rejects when active manual session pointer is missing")
    void checkIn_rejectsWhenActiveManualSessionMissing() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));
        when(valueOperations.get(MANUAL_CODE_ACTIVE_SESSION_PREFIX + 1L)).thenReturn(null);

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .manualCode("1234")
                .build();

        assertThatThrownBy(() -> checkInRecordService.checkIn(request, principal))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("유효하지 않거나 만료된 수동 체크인 코드");
    }

    private Party createParty(Long partyId, Long hostId) {
        return Party.builder()
                .id(partyId)
                .hostId(hostId)
                .hostName("호스트")
                .hostBadge(Party.BadgeType.NEW)
                .hostRating(5.0)
                .teamId("LG")
                .gameDate(LocalDate.now().plusDays(1))
                .gameTime(LocalTime.of(18, 30))
                .stadium("잠실야구장")
                .homeTeam("LG")
                .awayTeam("KT")
                .section("1루석")
                .maxParticipants(4)
                .currentParticipants(2)
                .description("같이 직관하실 분 구해요")
                .ticketVerified(false)
                .status(Party.PartyStatus.MATCHED)
                .build();
    }
}
