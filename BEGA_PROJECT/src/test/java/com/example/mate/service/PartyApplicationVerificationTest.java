package com.example.mate.service;

import com.example.auth.dto.UserDto;
import com.example.auth.service.UserService;
import com.example.kbo.dto.TicketInfo;
import com.example.kbo.service.TicketVerificationTokenStore;
import com.example.mate.dto.PartyApplicationDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.exception.DuplicateApplicationException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.notification.service.NotificationService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PartyApplicationVerificationTest {

        @Mock
        private PartyApplicationRepository applicationRepository;
        @Mock
        private PartyRepository partyRepository;
        @Mock
        private PartyService partyService;
        @Mock
        private NotificationService notificationService;
        @Mock
        private UserService userService;
        @Mock
        private TicketVerificationTokenStore ticketVerificationTokenStore;
        @Mock
        private PaymentTransactionService paymentTransactionService;

        @InjectMocks
        private PartyApplicationService service;

        private Party testParty;
        private Principal testPrincipal;

        @BeforeEach
        void setUp() {
                testParty = Party.builder()
                                .id(1L)
                                .hostId(100L)
                                .hostName("Host")
                                .hostBadge(Party.BadgeType.NEW)
                                .hostRating(5.0)
                                .teamId("LG")
                                .gameDate(LocalDate.of(2024, 5, 5))
                                .gameTime(LocalTime.of(18, 30))
                                .stadium("잠실")
                                .homeTeam("LG")
                                .awayTeam("OB")
                                .section("A")
                                .maxParticipants(4)
                                .currentParticipants(1)
                                .description("Test party")
                                .ticketVerified(false)
                                .status(Party.PartyStatus.PENDING)
                                .build();

                testPrincipal = () -> "test@example.com";
        }

        private void setupCommonMocks(Long applicantId) {
                given(userService.getUserIdByEmail("test@example.com")).willReturn(applicantId);
                given(userService.findUserByEmail("test@example.com")).willReturn(
                                UserDto.builder().name("TestUser").build());
                given(userService.isSocialVerified(applicantId)).willReturn(true);
                given(applicationRepository.findByPartyIdAndApplicantId(1L, applicantId))
                                .willReturn(Optional.empty());
                given(applicationRepository.existsByPartyIdAndApplicantIdAndIsRejectedTrue(1L, applicantId))
                                .willReturn(false);
                given(applicationRepository.countByPartyIdAndIsApprovedFalseAndIsRejectedFalse(1L))
                                .willReturn(0L);
                given(partyRepository.findById(1L)).willReturn(Optional.of(testParty));
                given(applicationRepository.save(any(PartyApplication.class)))
                                .willAnswer(inv -> {
                                        PartyApplication app = inv.getArgument(0);
                                        app.setId(99L);
                                        return app;
                                });
        }

        @Nested
        @DisplayName("P1: applicantId Principal 기반 파생")
        class ApplicantIdFromPrincipal {

                @Test
                @DisplayName("Principal에서 applicantId를 올바르게 파생해야 한다")
                void shouldResolveApplicantIdFromPrincipal() {
                        // given
                        Long realUserId = 42L;
                        setupCommonMocks(realUserId);
                        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(realUserId)).willReturn(0L);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .applicantId(999L) // 위조된 ID — 무시되어야 함
                                        .applicantName("Spoofed")
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        // when
                        PartyApplicationDTO.Response response = service.createApplication(request, testPrincipal);

                        // then — 저장된 신청의 applicantId는 42L이어야 하고, name은 "TestUser"
                        verify(applicationRepository).save(argThat(app -> app.getApplicantId().equals(42L) &&
                                        app.getApplicantName().equals("TestUser")));
                }

                @Test
                @DisplayName("내 신청 목록 조회 시 Principal 기반으로 조회해야 한다")
                void shouldReturnMyApplications() {
                        // given
                        Long userId = 101L;
                        given(userService.getUserIdByEmail("test@example.com")).willReturn(userId);

                        PartyApplication app = PartyApplication.builder()
                                        .id(1L)
                                        .partyId(1L)
                                        .applicantId(userId)
                                        .build();

                        given(applicationRepository.findByApplicantId(userId)).willReturn(java.util.List.of(app));

                        // when
                        var result = service.getMyApplications(testPrincipal);

                        // then
                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getApplicantId()).isEqualTo(userId);
                }
        }

        @Nested
        @DisplayName("P1: 서버 사이드 배지 결정")
        class ServerSideBadgeResolution {

                @Test
                @DisplayName("유효한 토큰 + 매칭 성공 → VERIFIED 배지")
                void shouldSetVerifiedBadgeWithValidToken() {
                        Long userId = 42L;
                        setupCommonMocks(userId);

                        TicketInfo ticketInfo = TicketInfo.builder()
                                        .date("2024-05-05")
                                        .homeTeam("LG")
                                        .awayTeam("OB")
                                        .build();

                        given(ticketVerificationTokenStore.consumeToken("valid-token")).willReturn(ticketInfo);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .verificationToken("valid-token")
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        service.createApplication(request, testPrincipal);

                        verify(applicationRepository)
                                        .save(argThat(app -> app.getApplicantBadge() == Party.BadgeType.VERIFIED &&
                                                        app.getTicketVerified()));
                }

                @Test
                @DisplayName("클라이언트가 VERIFIED 배지를 보내도 서버가 무시 → NEW")
                void shouldIgnoreClientVerifiedBadge() {
                        Long userId = 42L;
                        setupCommonMocks(userId);
                        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(userId)).willReturn(0L);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .applicantBadge(Party.BadgeType.VERIFIED) // 위조 시도
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        service.createApplication(request, testPrincipal);

                        verify(applicationRepository)
                                        .save(argThat(app -> app.getApplicantBadge() == Party.BadgeType.NEW));
                }

                @Test
                @DisplayName("클라이언트가 TRUSTED 배지를 보내도 서버가 무시 — 실적 3건 미만이면 NEW")
                void shouldIgnoreClientTrustedBadgeWhenNotEligible() {
                        Long userId = 42L;
                        setupCommonMocks(userId);
                        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(userId)).willReturn(2L);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .applicantBadge(Party.BadgeType.TRUSTED) // 위조 시도
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        service.createApplication(request, testPrincipal);

                        verify(applicationRepository)
                                        .save(argThat(app -> app.getApplicantBadge() == Party.BadgeType.NEW));
                }

                @Test
                @DisplayName("승인된 신청 3건 이상이면 TRUSTED 배지 자동 부여")
                void shouldAutoAssignTrustedBadge() {
                        Long userId = 42L;
                        setupCommonMocks(userId);
                        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(userId)).willReturn(5L);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        service.createApplication(request, testPrincipal);

                        verify(applicationRepository)
                                        .save(argThat(app -> app.getApplicantBadge() == Party.BadgeType.TRUSTED));
                }
        }

        @Nested
        @DisplayName("P2: 팀 정규화 기반 매칭")
        class TeamNormalizedMatching {

                @Test
                @DisplayName("한글 팀명 OCR → 표준 teamId 매칭 성공")
                void shouldMatchKoreanTeamNames() {
                        Long userId = 42L;
                        setupCommonMocks(userId);

                        TicketInfo ticketInfo = TicketInfo.builder()
                                        .date("2024-05-05")
                                        .homeTeam("LG 트윈스") // 한글 → "LG"
                                        .awayTeam("두산 베어스") // 한글 → "OB"
                                        .build();

                        given(ticketVerificationTokenStore.consumeToken("kr-token")).willReturn(ticketInfo);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .verificationToken("kr-token")
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        service.createApplication(request, testPrincipal);

                        verify(applicationRepository)
                                        .save(argThat(app -> app.getApplicantBadge() == Party.BadgeType.VERIFIED &&
                                                        app.getTicketVerified()));
                }

                @Test
                @DisplayName("날짜 불일치 → 인증 실패, NEW 배지")
                void shouldFailOnDateMismatch() {
                        Long userId = 42L;
                        setupCommonMocks(userId);
                        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(userId)).willReturn(0L);

                        TicketInfo ticketInfo = TicketInfo.builder()
                                        .date("2024-06-01") // 다른 날짜
                                        .homeTeam("LG")
                                        .awayTeam("OB")
                                        .build();

                        given(ticketVerificationTokenStore.consumeToken("wrong-date")).willReturn(ticketInfo);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .verificationToken("wrong-date")
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        service.createApplication(request, testPrincipal);

                        verify(applicationRepository)
                                        .save(argThat(app -> app.getApplicantBadge() == Party.BadgeType.NEW &&
                                                        !app.getTicketVerified()));
                }

                @Test
                @DisplayName("무효/만료 토큰 → 인증 실패, NEW 배지")
                void shouldFailOnInvalidToken() {
                        Long userId = 42L;
                        setupCommonMocks(userId);
                        given(applicationRepository.countByApplicantIdAndIsApprovedTrue(userId)).willReturn(0L);
                        given(ticketVerificationTokenStore.consumeToken("invalid-token")).willReturn(null);

                        PartyApplicationDTO.Request request = PartyApplicationDTO.Request.builder()
                                        .partyId(1L)
                                        .verificationToken("invalid-token")
                                        .depositAmount(10000)
                                        .paymentType(PartyApplication.PaymentType.DEPOSIT)
                                        .build();

                        service.createApplication(request, testPrincipal);

                        verify(applicationRepository)
                                        .save(argThat(app -> app.getApplicantBadge() == Party.BadgeType.NEW &&
                                                        !app.getTicketVerified()));
                }
        }

        @Nested
        @DisplayName("신청 조회 권한 및 단건 조회")
        class ApplicationReadAccess {

                @Test
                @DisplayName("호스트는 파티 신청 목록을 조회할 수 있다")
                void hostCanReadApplicationsByParty() {
                        // given
                        Principal hostPrincipal = () -> "host@example.com";
                        PartyApplication application = PartyApplication.builder()
                                        .id(11L)
                                        .partyId(1L)
                                        .applicantId(42L)
                                        .applicantName("Applicant")
                                        .build();

                        given(userService.getUserIdByEmail("host@example.com")).willReturn(100L);
                        given(partyRepository.findById(1L)).willReturn(Optional.of(testParty));
                        given(applicationRepository.findByPartyId(1L)).willReturn(java.util.List.of(application));

                        // when
                        var result = service.getApplicationsByPartyId(1L, hostPrincipal);

                        // then
                        assertThat(result).hasSize(1);
                        assertThat(result.get(0).getPartyId()).isEqualTo(1L);
                }

                @Test
                @DisplayName("비호스트는 파티 신청 목록 조회 시 예외가 발생한다")
                void nonHostCannotReadApplicationsByParty() {
                        // given
                        Principal nonHostPrincipal = () -> "nonhost@example.com";
                        given(userService.getUserIdByEmail("nonhost@example.com")).willReturn(777L);
                        given(partyRepository.findById(1L)).willReturn(Optional.of(testParty));

                        // when / then
                        assertThatThrownBy(() -> service.getApplicationsByPartyId(1L, nonHostPrincipal))
                                        .isInstanceOf(com.example.mate.exception.UnauthorizedAccessException.class)
                                        .hasMessageContaining("호스트");
                }

                @Test
                @DisplayName("내 신청 단건 조회는 본인 파티 신청을 반환하고 없으면 null을 반환한다")
                void shouldReturnMyApplicationByPartyOrNull() {
                        // given
                        Principal principal = () -> "applicant@example.com";
                        PartyApplication application = PartyApplication.builder()
                                        .id(22L)
                                        .partyId(1L)
                                        .applicantId(55L)
                                        .applicantName("Applicant")
                                        .build();

                        given(userService.getUserIdByEmail("applicant@example.com")).willReturn(55L);
                        given(applicationRepository.findByPartyIdAndApplicantId(1L, 55L))
                                        .willReturn(Optional.of(application));
                        given(applicationRepository.findByPartyIdAndApplicantId(2L, 55L))
                                        .willReturn(Optional.empty());

                        // when
                        var found = service.getMyApplicationByPartyId(1L, principal);
                        var notFound = service.getMyApplicationByPartyId(2L, principal);

                        // then
                        assertThat(found).isNotNull();
                        assertThat(found.getPartyId()).isEqualTo(1L);
                        assertThat(notFound).isNull();
                }
        }
}
