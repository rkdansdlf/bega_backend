package com.example.mate.repository;

import com.example.mate.entity.ChatMessage;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ChatMessageUnreadAggregationRepositoryTest {

    private static final Long USER_ID = 77L;
    private static final List<Party.PartyStatus> ACTIVE_STATUSES = List.of(
            Party.PartyStatus.PENDING,
            Party.PartyStatus.MATCHED,
            Party.PartyStatus.SELLING,
            Party.PartyStatus.CHECKED_IN);

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyApplicationRepository applicationRepository;

    @Test
    void aggregateQueriesPreserveActiveMembershipReadBoundaryAndSenderExclusion() {
        Party hostedParty = partyRepository.saveAndFlush(party(USER_ID, Party.PartyStatus.PENDING));
        Party applicantParty = partyRepository.saveAndFlush(party(88L, Party.PartyStatus.MATCHED));
        Party inactiveHostedParty = partyRepository.saveAndFlush(party(USER_ID, Party.PartyStatus.FAILED));
        Party unapprovedParty = partyRepository.saveAndFlush(party(88L, Party.PartyStatus.SELLING));

        PartyApplication approvedApplication = applicationRepository.saveAndFlush(
                application(applicantParty.getId(), USER_ID, true));
        PartyApplication unapprovedApplication = applicationRepository.saveAndFlush(
                application(unapprovedParty.getId(), USER_ID, false));

        hostedParty.setHostLastReadChatAt(Instant.EPOCH);
        inactiveHostedParty.setHostLastReadChatAt(Instant.EPOCH);
        approvedApplication.setLastReadChatAt(Instant.EPOCH);
        unapprovedApplication.setLastReadChatAt(Instant.EPOCH);
        partyRepository.saveAllAndFlush(List.of(hostedParty, inactiveHostedParty));
        applicationRepository.saveAllAndFlush(List.of(approvedApplication, unapprovedApplication));

        chatMessageRepository.saveAllAndFlush(List.of(
                message(hostedParty.getId(), 88L),
                message(hostedParty.getId(), USER_ID),
                message(applicantParty.getId(), 88L),
                message(inactiveHostedParty.getId(), 88L),
                message(unapprovedParty.getId(), 88L)));

        assertThat(chatMessageRepository.countUnreadFromHostedParties(USER_ID, ACTIVE_STATUSES, Instant.EPOCH))
                .isEqualTo(1L);
        assertThat(chatMessageRepository.countUnreadFromApprovedApplications(USER_ID, ACTIVE_STATUSES, Instant.EPOCH))
                .isEqualTo(1L);
    }

    private Party party(Long hostId, Party.PartyStatus status) {
        return Party.builder()
                .hostId(hostId)
                .hostName("Unread test host")
                .hostBadge(Party.BadgeType.NEW)
                .teamId("TEST_TEAM")
                .gameDate(LocalDate.of(2000, 1, 1))
                .gameTime(LocalTime.MIDNIGHT)
                .stadium("TEST_STADIUM")
                .homeTeam("TEST_HOME")
                .awayTeam("TEST_AWAY")
                .section("응원석")
                .maxParticipants(4)
                .currentParticipants(1)
                .description("unread aggregation fixture")
                .ticketVerified(false)
                .status(status)
                .build();
    }

    private PartyApplication application(Long partyId, Long applicantId, boolean approved) {
        return PartyApplication.builder()
                .partyId(partyId)
                .applicantId(applicantId)
                .applicantName("Unread test applicant")
                .applicantBadge(Party.BadgeType.NEW)
                .message("unread aggregation fixture")
                .depositAmount(0)
                .isPaid(false)
                .isApproved(approved)
                .isRejected(false)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
    }

    private ChatMessage message(Long partyId, Long senderId) {
        return ChatMessage.builder()
                .partyId(partyId)
                .senderId(senderId)
                .senderName("Unread test sender")
                .message("unread aggregation fixture")
                .build();
    }
}
