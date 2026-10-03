package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.mate.service.PartyService;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountDeletionFinalizationService {

    private final UserRepository userRepository;
    private final AccountDeletionTokenRepository accountDeletionTokenRepository;
    private final PartyService partyService;

    public AccountDeletionFinalizationService(
            UserRepository userRepository,
            AccountDeletionTokenRepository accountDeletionTokenRepository,
            @Lazy PartyService partyService) {
        this.userRepository = userRepository;
        this.accountDeletionTokenRepository = accountDeletionTokenRepository;
        this.partyService = partyService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean finalizeIfDue(Long userId, LocalDateTime cutoff) {
        UserEntity user = userRepository.findByIdForWrite(userId).orElse(null);
        if (user == null
                || !user.isPendingDeletion()
                || user.getDeletionScheduledFor() == null
                || user.getDeletionScheduledFor().isAfter(cutoff)) {
            return false;
        }

        partyService.handleUserDeletion(userId);
        user.setDeletionScheduledFor(null);
        userRepository.save(user);
        accountDeletionTokenRepository.deleteByUser_Id(userId);
        log.info("Finalized pending account deletion for userId={}", userId);
        return true;
    }
}
