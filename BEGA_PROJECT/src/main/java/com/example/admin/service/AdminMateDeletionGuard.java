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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminMateDeletionGuard {

    private static final List<Notification.NotificationType> PARTY_NOTIFICATION_TYPES = Arrays.stream(
            Notification.NotificationType.values())
            .filter(Notification.NotificationType::referencesParty)
            .collect(Collectors.toUnmodifiableList());

    private final PartyApplicationRepository applicationRepository;
    private final PaymentIntentRepository paymentIntentRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final CheckInRecordRepository checkInRecordRepository;
    private final PartyReviewRepository partyReviewRepository;
    private final UserPartyFavoriteRepository userPartyFavoriteRepository;
    private final CheerPostRepo cheerPostRepository;
    private final NotificationRepository notificationRepository;

    public void ensureNoLinkedRecords(Long mateId) {
        boolean hasLinkedRecords = applicationRepository.existsByPartyId(mateId)
                || paymentIntentRepository.existsByPartyId(mateId)
                || paymentTransactionRepository.existsByPartyId(mateId)
                || chatMessageRepository.existsByPartyId(mateId)
                || checkInRecordRepository.existsByPartyId(mateId)
                || partyReviewRepository.existsByPartyId(mateId)
                || userPartyFavoriteRepository.existsByPartyId(mateId)
                || cheerPostRepository.existsByPartyId(mateId)
                || notificationRepository.existsByRelatedIdAndTypeIn(mateId, PARTY_NOTIFICATION_TYPES);

        if (hasLinkedRecords) {
            throw new MateDeletionBlockedException(mateId);
        }
    }
}
