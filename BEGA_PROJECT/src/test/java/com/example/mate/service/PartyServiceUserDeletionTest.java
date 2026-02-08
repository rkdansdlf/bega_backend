package com.example.mate.service;

import com.example.auth.repository.UserRepository;
import com.example.auth.service.UserService;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.notification.entity.Notification;
import com.example.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartyService - User Deletion Cascade Cleanup Tests")
class PartyServiceUserDeletionTest {

    @Mock
    private PartyRepository partyRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartyApplicationRepository applicationRepository;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PartyService partyService;

    private Long hostUserId;
    private Long participantUserId1;
    private Long participantUserId2;
    private Party pendingParty;
    private Party matchedParty;
    private PartyApplication application1;
    private PartyApplication application2;
    private PartyApplication participantApplication;

    @BeforeEach
    void setUp() {
        hostUserId = 1L;
        participantUserId1 = 2L;
        participantUserId2 = 3L;

        // PENDING 파티 생성
        pendingParty = Party.builder()
                .id(100L)
                .hostId(hostUserId)
                .hostName("호스트")
                .teamId("KIA")
                .gameDate(LocalDate.now().plusDays(5))
                .gameTime(LocalTime.of(18, 30))
                .stadium("광주-기아 챔피언스 필드")
                .homeTeam("KIA")
                .awayTeam("LG")
                .section("3루 응원석")
                .maxParticipants(4)
                .currentParticipants(2)
                .description("같이 응원해요")
                .ticketVerified(true)
                .status(Party.PartyStatus.PENDING)
                .hostBadge(Party.BadgeType.VERIFIED)
                .hostRating(5.0)
                .build();

        // MATCHED 파티 생성
        matchedParty = Party.builder()
                .id(101L)
                .hostId(hostUserId)
                .hostName("호스트")
                .teamId("SSG")
                .gameDate(LocalDate.now().plusDays(7))
                .gameTime(LocalTime.of(18, 30))
                .stadium("인천 SSG 랜더스필드")
                .homeTeam("SSG")
                .awayTeam("두산")
                .section("외야석")
                .maxParticipants(3)
                .currentParticipants(3)
                .description("매칭 성공")
                .ticketVerified(true)
                .status(Party.PartyStatus.MATCHED)
                .hostBadge(Party.BadgeType.TRUSTED)
                .hostRating(4.8)
                .build();

        // 승인된 신청들
        application1 = PartyApplication.builder()
                .id(200L)
                .partyId(100L)
                .applicantId(participantUserId1)
                .applicantName("참여자1")
                .message("참여하고 싶어요")
                .isApproved(true)
                .isRejected(false)
                .isPaid(false)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .depositAmount(10000)
                .applicantBadge(Party.BadgeType.NEW)
                .applicantRating(5.0)
                .responseDeadline(Instant.now().plusSeconds(48 * 3600))
                .approvedAt(Instant.now())
                .build();

        application2 = PartyApplication.builder()
                .id(201L)
                .partyId(101L)
                .applicantId(participantUserId2)
                .applicantName("참여자2")
                .message("같이 가요")
                .isApproved(true)
                .isRejected(false)
                .isPaid(true)
                .paymentType(PartyApplication.PaymentType.FULL)
                .depositAmount(0)
                .applicantBadge(Party.BadgeType.VERIFIED)
                .applicantRating(4.5)
                .responseDeadline(Instant.now().plusSeconds(48 * 3600))
                .approvedAt(Instant.now())
                .build();

        // 참여자로 승인된 신청
        participantApplication = PartyApplication.builder()
                .id(202L)
                .partyId(300L)
                .applicantId(participantUserId1)
                .applicantName("참여자1")
                .message("참여 신청")
                .isApproved(true)
                .isRejected(false)
                .isPaid(true)
                .paymentType(PartyApplication.PaymentType.FULL)
                .depositAmount(0)
                .applicantBadge(Party.BadgeType.NEW)
                .applicantRating(5.0)
                .responseDeadline(Instant.now().plusSeconds(48 * 3600))
                .approvedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("호스트 계정 삭제 시 PENDING 파티를 FAILED로 변경하고 참여자들에게 알림")
    void testHandleHostDeletion_PendingParty() {
        // given
        when(partyRepository.findByHostIdAndStatusIn(eq(hostUserId), anyList()))
                .thenReturn(List.of(pendingParty));
        when(applicationRepository.findByPartyIdAndIsApprovedTrue(100L))
                .thenReturn(List.of(application1));
        when(applicationRepository.findByApplicantIdAndIsApprovedTrueAndIsRejectedFalse(hostUserId))
                .thenReturn(List.of());

        // when
        partyService.handleUserDeletion(hostUserId);

        // then
        // 파티 상태가 FAILED로 변경되었는지 확인
        verify(partyRepository, times(1)).save(argThat(party ->
                party.getId().equals(100L) && party.getStatus() == Party.PartyStatus.FAILED
        ));

        // 참여자에게 알림이 발송되었는지 확인
        verify(notificationService, times(1)).createNotification(
                eq(participantUserId1),
                eq(Notification.NotificationType.PARTY_CANCELLED_HOST_DELETED),
                anyString(),
                anyString(),
                eq(100L)
        );
    }

    @Test
    @DisplayName("호스트 계정 삭제 시 MATCHED 파티를 FAILED로 변경하고 모든 참여자에게 알림")
    void testHandleHostDeletion_MatchedParty() {
        // given
        when(partyRepository.findByHostIdAndStatusIn(eq(hostUserId), anyList()))
                .thenReturn(List.of(matchedParty));
        when(applicationRepository.findByPartyIdAndIsApprovedTrue(101L))
                .thenReturn(List.of(application2));
        when(applicationRepository.findByApplicantIdAndIsApprovedTrueAndIsRejectedFalse(hostUserId))
                .thenReturn(List.of());

        // when
        partyService.handleUserDeletion(hostUserId);

        // then
        // 파티 상태가 FAILED로 변경되었는지 확인
        verify(partyRepository, times(1)).save(argThat(party ->
                party.getId().equals(101L) && party.getStatus() == Party.PartyStatus.FAILED
        ));

        // 참여자에게 알림이 발송되었는지 확인
        verify(notificationService, times(1)).createNotification(
                eq(participantUserId2),
                eq(Notification.NotificationType.PARTY_CANCELLED_HOST_DELETED),
                anyString(),
                anyString(),
                eq(101L)
        );
    }

    @Test
    @DisplayName("참여자 계정 삭제 시 승인된 신청 취소 및 currentParticipants 감소")
    void testHandleParticipantDeletion() {
        // given
        Party hostParty = Party.builder()
                .id(300L)
                .hostId(999L)
                .hostName("다른 호스트")
                .teamId("LG")
                .gameDate(LocalDate.now().plusDays(10))
                .gameTime(LocalTime.of(18, 30))
                .stadium("잠실 야구장")
                .homeTeam("LG")
                .awayTeam("KIA")
                .section("1루석")
                .maxParticipants(4)
                .currentParticipants(3)
                .description("같이 보러가요")
                .ticketVerified(true)
                .status(Party.PartyStatus.MATCHED)
                .hostBadge(Party.BadgeType.VERIFIED)
                .hostRating(4.7)
                .build();

        when(partyRepository.findByHostIdAndStatusIn(eq(participantUserId1), anyList()))
                .thenReturn(List.of());
        when(applicationRepository.findByApplicantIdAndIsApprovedTrueAndIsRejectedFalse(participantUserId1))
                .thenReturn(List.of(participantApplication));
        when(partyRepository.findById(300L))
                .thenReturn(Optional.of(hostParty));

        // when
        partyService.handleUserDeletion(participantUserId1);

        // then
        // currentParticipants가 감소했는지 확인
        verify(partyRepository, times(1)).save(argThat(party ->
                party.getId().equals(300L) &&
                party.getCurrentParticipants() == 2 &&
                party.getStatus() == Party.PartyStatus.PENDING
        ));

        // 호스트에게 알림이 발송되었는지 확인
        verify(notificationService, times(1)).createNotification(
                eq(999L),
                eq(Notification.NotificationType.PARTY_PARTICIPANT_LEFT),
                anyString(),
                anyString(),
                eq(300L)
        );

        // 신청이 삭제되었는지 확인
        verify(applicationRepository, times(1)).delete(participantApplication);
    }

    @Test
    @DisplayName("호스트이면서 참여자인 사용자 계정 삭제 시 모든 관련 처리")
    void testHandleUserDeletion_HostAndParticipant() {
        // given
        Party anotherHostParty = Party.builder()
                .id(400L)
                .hostId(999L)
                .hostName("다른 호스트")
                .teamId("NC")
                .gameDate(LocalDate.now().plusDays(3))
                .gameTime(LocalTime.of(18, 30))
                .stadium("창원 NC 파크")
                .homeTeam("NC")
                .awayTeam("삼성")
                .section("외야석")
                .maxParticipants(5)
                .currentParticipants(4)
                .description("NC 응원")
                .ticketVerified(true)
                .status(Party.PartyStatus.PENDING)
                .hostBadge(Party.BadgeType.VERIFIED)
                .hostRating(4.9)
                .build();

        PartyApplication asParticipant = PartyApplication.builder()
                .id(203L)
                .partyId(400L)
                .applicantId(hostUserId)
                .applicantName("호스트")
                .message("다른 파티에 참여")
                .isApproved(true)
                .isRejected(false)
                .isPaid(true)
                .paymentType(PartyApplication.PaymentType.FULL)
                .depositAmount(0)
                .applicantBadge(Party.BadgeType.VERIFIED)
                .applicantRating(5.0)
                .responseDeadline(Instant.now().plusSeconds(48 * 3600))
                .approvedAt(Instant.now())
                .build();

        when(partyRepository.findByHostIdAndStatusIn(eq(hostUserId), anyList()))
                .thenReturn(List.of(pendingParty, matchedParty));
        when(applicationRepository.findByPartyIdAndIsApprovedTrue(100L))
                .thenReturn(List.of(application1));
        when(applicationRepository.findByPartyIdAndIsApprovedTrue(101L))
                .thenReturn(List.of(application2));
        when(applicationRepository.findByApplicantIdAndIsApprovedTrueAndIsRejectedFalse(hostUserId))
                .thenReturn(List.of(asParticipant));
        when(partyRepository.findById(400L))
                .thenReturn(Optional.of(anotherHostParty));

        // when
        partyService.handleUserDeletion(hostUserId);

        // then
        // 호스트로 생성한 파티 2개가 FAILED로 변경
        verify(partyRepository, times(2)).save(argThat(party ->
                (party.getId().equals(100L) || party.getId().equals(101L)) &&
                party.getStatus() == Party.PartyStatus.FAILED
        ));

        // 참여자들에게 알림 발송 (2개 파티의 참여자들)
        verify(notificationService, times(2)).createNotification(
                anyLong(),
                eq(Notification.NotificationType.PARTY_CANCELLED_HOST_DELETED),
                anyString(),
                anyString(),
                anyLong()
        );

        // 참여자로서의 신청 취소 처리
        verify(partyRepository, times(1)).save(argThat(party ->
                party.getId().equals(400L) && party.getCurrentParticipants() == 3
        ));

        // 다른 호스트에게 알림 발송
        verify(notificationService, times(1)).createNotification(
                eq(999L),
                eq(Notification.NotificationType.PARTY_PARTICIPANT_LEFT),
                anyString(),
                anyString(),
                eq(400L)
        );

        // 참여자로서의 신청 삭제
        verify(applicationRepository, times(1)).delete(asParticipant);
    }

    @Test
    @DisplayName("관련 파티가 없는 사용자 삭제 시 정상 처리")
    void testHandleUserDeletion_NoRelatedParties() {
        // given
        Long userId = 999L;
        when(partyRepository.findByHostIdAndStatusIn(eq(userId), anyList()))
                .thenReturn(List.of());
        when(applicationRepository.findByApplicantIdAndIsApprovedTrueAndIsRejectedFalse(userId))
                .thenReturn(List.of());

        // when
        partyService.handleUserDeletion(userId);

        // then
        verify(partyRepository, never()).save(any(Party.class));
        verify(notificationService, never()).createNotification(anyLong(), any(), anyString(), anyString(), anyLong());
        verify(applicationRepository, never()).delete(any(PartyApplication.class));
    }
}
