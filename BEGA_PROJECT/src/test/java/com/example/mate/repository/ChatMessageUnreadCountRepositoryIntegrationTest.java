package com.example.mate.repository;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.mate.entity.ChatMessage;
import com.example.mate.entity.Party;
import com.example.mate.entity.Party.PartyStatus;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PartyApplication.PaymentType;
import com.example.mate.support.MateTestFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:chat_unread_count;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DisplayName("Chat message unread count repository tests")
class ChatMessageUnreadCountRepositoryIntegrationTest {

    private static final List<PartyStatus> ACTIVE_STATUSES = List.of(
            PartyStatus.PENDING,
            PartyStatus.MATCHED,
            PartyStatus.SELLING,
            PartyStatus.CHECKED_IN);

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private PartyApplicationRepository applicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void countUnreadMessagesByUserId_countsOnlyActiveHostedAndApprovedRoomsAfterEachReadTime() {
        UserEntity reader = userRepository.saveAndFlush(
                MateTestFixtureFactory.user("unread-reader@example.invalid", "Unread Reader"));
        UserEntity otherHost = userRepository.saveAndFlush(
                MateTestFixtureFactory.user("unread-other-host@example.invalid", "Other Host"));
        UserEntity sender = userRepository.saveAndFlush(
                MateTestFixtureFactory.user("unread-sender@example.invalid", "Sender"));

        Party hostedParty = saveParty(reader.getId(), "Hosted", PartyStatus.PENDING);
        Party participantParty = saveParty(otherHost.getId(), "Participant", PartyStatus.MATCHED);
        Party inactiveParty = saveParty(reader.getId(), "Inactive", PartyStatus.COMPLETED);
        Party unapprovedParty = saveParty(otherHost.getId(), "Unapproved", PartyStatus.PENDING);
        Party unrelatedParty = saveParty(otherHost.getId(), "Unrelated", PartyStatus.PENDING);

        PartyApplication approvedApplication = applicationRepository.saveAndFlush(
                MateTestFixtureFactory.application(
                        participantParty.getId(), reader.getId(), "Unread Reader", PaymentType.FULL,
                        false, true, false, null, null));
        PartyApplication hostApplication = applicationRepository.saveAndFlush(
                MateTestFixtureFactory.application(
                        hostedParty.getId(), reader.getId(), "Unread Reader", PaymentType.FULL,
                        false, true, false, null, null));
        applicationRepository.saveAndFlush(MateTestFixtureFactory.application(
                unapprovedParty.getId(), reader.getId(), "Unread Reader", PaymentType.FULL,
                false, false, false, null, null));

        hostedParty.setHostLastReadChatAt(Instant.EPOCH);
        partyRepository.saveAndFlush(hostedParty);
        approvedApplication.setLastReadChatAt(Instant.EPOCH);
        applicationRepository.saveAndFlush(approvedApplication);
        hostApplication.setLastReadChatAt(Instant.EPOCH);
        applicationRepository.saveAndFlush(hostApplication);

        saveMessages(hostedParty.getId(), sender.getId(), 2);
        saveMessages(hostedParty.getId(), reader.getId(), 1);
        saveMessages(participantParty.getId(), sender.getId(), 3);
        saveMessages(participantParty.getId(), reader.getId(), 1);
        saveMessages(inactiveParty.getId(), sender.getId(), 1);
        saveMessages(unapprovedParty.getId(), sender.getId(), 1);
        saveMessages(unrelatedParty.getId(), sender.getId(), 1);

        assertThat(chatMessageRepository.countUnreadMessagesForHostedParties(
                reader.getId(), ACTIVE_STATUSES, Instant.EPOCH)).isEqualTo(2L);
        assertThat(chatMessageRepository.countUnreadMessagesForApprovedApplications(
                reader.getId(), ACTIVE_STATUSES, Instant.EPOCH)).isEqualTo(5L);

        hostedParty.setHostLastReadChatAt(Instant.now().plusSeconds(60));
        partyRepository.saveAndFlush(hostedParty);
        assertThat(chatMessageRepository.countUnreadMessagesForHostedParties(
                reader.getId(), ACTIVE_STATUSES, Instant.EPOCH)).isZero();

        hostedParty.setHostLastReadChatAt(Instant.EPOCH);
        partyRepository.saveAndFlush(hostedParty);
        approvedApplication.setLastReadChatAt(Instant.now().plusSeconds(60));
        applicationRepository.saveAndFlush(approvedApplication);
        assertThat(chatMessageRepository.countUnreadMessagesForHostedParties(
                reader.getId(), ACTIVE_STATUSES, Instant.EPOCH)).isEqualTo(2L);
        assertThat(chatMessageRepository.countUnreadMessagesForApprovedApplications(
                reader.getId(), ACTIVE_STATUSES, Instant.EPOCH)).isEqualTo(2L);
    }

    private Party saveParty(Long hostId, String hostName, PartyStatus status) {
        return partyRepository.saveAndFlush(MateTestFixtureFactory.party(
                hostId,
                hostName,
                status,
                4,
                1,
                22000,
                null,
                java.time.LocalDate.now().plusDays(1),
                java.time.LocalTime.of(18, 30)));
    }

    private void saveMessages(Long partyId, Long senderId, int count) {
        for (int index = 0; index < count; index += 1) {
            chatMessageRepository.saveAndFlush(ChatMessage.builder()
                    .partyId(partyId)
                    .senderId(senderId)
                    .senderName("Test Sender")
                    .message("Unread message " + index)
                    .build());
        }
    }
}
