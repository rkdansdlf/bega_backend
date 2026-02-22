package com.example.mate.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.UserService;
import com.example.mate.dto.CheckInRecordDTO;
import com.example.mate.entity.CheckInRecord;
import com.example.mate.entity.Party;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Test
    @DisplayName("createQrSession returns checkinUrl and stores redis payload")
    void createQrSession_storesPayloadAndReturnsUrl() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        objectMapper.findAndRegisterModules();
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
        verify(valueOperations).set(eq("mate:checkin:qr:" + response.getSessionId()), anyString(),
                any(java.time.Duration.class));
    }

    @Test
    @DisplayName("checkIn rejects mismatched qrSession party")
    void checkIn_rejectsMismatchedQrSessionParty() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);
        String sessionId = "session-1";
        objectMapper.findAndRegisterModules();
        String serializedPayload = "{\"partyId\":2,\"generatedByUserId\":10,"
                + "\"createdAt\":\"2026-02-20T00:00:00Z\",\"expiresAt\":\"2099-01-01T00:00:00Z\"}";

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));
        when(valueOperations.get("mate:checkin:qr:" + sessionId)).thenReturn(serializedPayload);

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
    @DisplayName("checkIn succeeds without qrSessionId for backward compatibility")
    void checkIn_succeedsWithoutQrSession() {
        Principal principal = () -> "host@test.com";
        Party party = createParty(1L, 10L);

        CheckInRecord savedRecord = CheckInRecord.builder()
                .id(100L)
                .partyId(1L)
                .userId(10L)
                .location("잠실야구장")
                .checkedInAt(LocalDateTime.now())
                .build();

        when(userService.getUserIdByEmail("host@test.com")).thenReturn(10L);
        when(partyRepository.findById(1L)).thenReturn(Optional.of(party));
        when(checkInRecordRepository.findByPartyIdAndUserId(1L, 10L)).thenReturn(Optional.empty());
        when(checkInRecordRepository.save(any(CheckInRecord.class))).thenReturn(savedRecord);
        when(checkInRecordRepository.countByPartyId(1L)).thenReturn(1L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(UserEntity.builder().id(10L).name("호스트").build()));

        CheckInRecordDTO.Request request = CheckInRecordDTO.Request.builder()
                .partyId(1L)
                .location("잠실야구장")
                .build();

        CheckInRecordDTO.Response response = checkInRecordService.checkIn(request, principal);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUserId()).isEqualTo(10L);
        assertThat(response.getUserName()).isEqualTo("호스트");
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
