package com.example.admin.service;

import com.example.admin.exception.MateDeletionBlockedException;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.repository.CheckInRecordRepository;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyReviewRepository;
import com.example.mate.repository.PaymentIntentRepository;
import com.example.mate.repository.PaymentTransactionRepository;
import com.example.mate.repository.UserPartyFavoriteRepository;
import com.example.notification.entity.Notification;
import com.example.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminMateDeletionGuardTest {

    @Mock private PartyApplicationRepository applicationRepository;
    @Mock private PaymentIntentRepository paymentIntentRepository;
    @Mock private PaymentTransactionRepository paymentTransactionRepository;
    @Mock private ChatMessageRepository chatMessageRepository;
    @Mock private CheckInRecordRepository checkInRecordRepository;
    @Mock private PartyReviewRepository partyReviewRepository;
    @Mock private UserPartyFavoriteRepository userPartyFavoriteRepository;
    @Mock private CheerPostRepo cheerPostRepository;
    @Mock private NotificationRepository notificationRepository;

    @InjectMocks
    private AdminMateDeletionGuard guard;

    @ParameterizedTest
    @EnumSource(LinkedPartyRecord.class)
    void ensureNoLinkedRecords_blocksEveryRetainedRecordType(LinkedPartyRecord recordType) {
        Long mateId = 91L;
        switch (recordType) {
            case APPLICATION -> when(applicationRepository.existsByPartyId(mateId)).thenReturn(true);
            case PAYMENT_INTENT -> when(paymentIntentRepository.existsByPartyId(mateId)).thenReturn(true);
            case PAYMENT_TRANSACTION -> when(paymentTransactionRepository.existsByPartyId(mateId)).thenReturn(true);
            case CHAT -> when(chatMessageRepository.existsByPartyId(mateId)).thenReturn(true);
            case CHECK_IN -> when(checkInRecordRepository.existsByPartyId(mateId)).thenReturn(true);
            case REVIEW -> when(partyReviewRepository.existsByPartyId(mateId)).thenReturn(true);
            case FAVORITE -> when(userPartyFavoriteRepository.existsByPartyId(mateId)).thenReturn(true);
            case CHEER_POST -> when(cheerPostRepository.existsByPartyId(mateId)).thenReturn(true);
            case NOTIFICATION -> when(notificationRepository.existsByRelatedIdAndTypeIn(
                    eq(mateId), anyCollection())).thenReturn(true);
        }

        assertThatThrownBy(() -> guard.ensureNoLinkedRecords(mateId))
                .isInstanceOf(MateDeletionBlockedException.class);
    }

    @Test
    void ensureNoLinkedRecords_allowsOnlyCompletelyUnreferencedMate() {
        assertDoesNotThrow(() -> guard.ensureNoLinkedRecords(92L));
    }

    private enum LinkedPartyRecord {
        APPLICATION,
        PAYMENT_INTENT,
        PAYMENT_TRANSACTION,
        CHAT,
        CHECK_IN,
        REVIEW,
        FAVORITE,
        CHEER_POST,
        NOTIFICATION
    }
}
