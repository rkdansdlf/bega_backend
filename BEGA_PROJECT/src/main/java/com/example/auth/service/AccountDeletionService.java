package com.example.auth.service;

import com.example.auth.dto.AccountDeletionRecoveryInfoDto;
import com.example.auth.entity.AccountDeletionToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.InvalidCredentialsException;
import com.example.common.exception.UserNotFoundException;
import com.example.mate.service.PartyService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class AccountDeletionService {

    private static final int ACCOUNT_DELETION_GRACE_DAYS = 7;
    private static final String ACCOUNT_SETTINGS_REDIRECT_PATH = "/mypage?view=accountSettings";

    private final UserRepository userRepository;
    private final RefreshRepository refreshRepository;
    private final AccountDeletionTokenRepository accountDeletionTokenRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final EmailService emailService;
    private final AccountSecurityService accountSecurityService;
    private final PartyService partyService;

    public AccountDeletionService(
            UserRepository userRepository,
            RefreshRepository refreshRepository,
            AccountDeletionTokenRepository accountDeletionTokenRepository,
            BCryptPasswordEncoder bCryptPasswordEncoder,
            EmailService emailService,
            AccountSecurityService accountSecurityService,
            @Lazy PartyService partyService) {
        this.userRepository = userRepository;
        this.refreshRepository = refreshRepository;
        this.accountDeletionTokenRepository = accountDeletionTokenRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.emailService = emailService;
        this.accountSecurityService = accountSecurityService;
        this.partyService = partyService;
    }

    @Transactional
    public LocalDateTime scheduleAccountDeletion(Long userId, String password) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.isPendingDeletion() && user.getDeletionScheduledFor() != null
                && user.getDeletionScheduledFor().isAfter(LocalDateTime.now())) {
            throw new BadRequestBusinessException("ACCOUNT_DELETION_ALREADY_SCHEDULED", "이미 계정 삭제가 예약되어 있습니다.");
        }

        if (!user.isOAuth2User()) {
            if (password == null || password.isEmpty()) {
                throw new BadRequestBusinessException("PASSWORD_REQUIRED", "비밀번호를 입력해주세요.");
            }
            if (user.getPassword() == null) {
                throw new BadRequestBusinessException("PASSWORD_NOT_SET", "비밀번호가 설정되어 있지 않습니다.");
            }
            if (!bCryptPasswordEncoder.matches(password, user.getPassword())) {
                throw new InvalidCredentialsException("비밀번호가 일치하지 않습니다.");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime scheduledFor = now.plusDays(ACCOUNT_DELETION_GRACE_DAYS);
        String token = UUID.randomUUID().toString();

        user.setPendingDeletion(true);
        user.setDeletionRequestedAt(now);
        user.setDeletionScheduledFor(scheduledFor);
        user.setEnabled(false);
        user.setTokenVersion(Optional.ofNullable(user.getTokenVersion()).orElse(0) + 1);
        userRepository.save(user);

        refreshRepository.deleteByEmail(user.getEmail());
        accountDeletionTokenRepository.deleteByUser_Id(user.getId());
        accountDeletionTokenRepository.save(AccountDeletionToken.builder()
                .token(token)
                .user(user)
                .expiryDate(scheduledFor)
                .used(false)
                .build());

        emailService.sendAccountDeletionRecoveryEmail(
                user.getEmail(),
                token,
                scheduledFor,
                ACCOUNT_SETTINGS_REDIRECT_PATH);
        accountSecurityService.recordAccountDeletionScheduled(user.getId(), scheduledFor);

        return scheduledFor;
    }

    @Transactional(readOnly = true)
    public AccountDeletionRecoveryInfoDto getRecoveryInfo(String token) {
        AccountDeletionToken deletionToken = validateRecoveryToken(token);
        LocalDateTime scheduledFor = deletionToken.getUser().getDeletionScheduledFor();
        return AccountDeletionRecoveryInfoDto.builder()
                .scheduledFor(scheduledFor == null ? null : scheduledFor.toString())
                .build();
    }

    @Transactional
    public void recoverAccount(String token) {
        AccountDeletionToken deletionToken = validateRecoveryToken(token);
        UserEntity user = deletionToken.getUser();

        user.setPendingDeletion(false);
        user.setDeletionRequestedAt(null);
        user.setDeletionScheduledFor(null);
        user.setEnabled(true);
        user.setTokenVersion(Optional.ofNullable(user.getTokenVersion()).orElse(0) + 1);
        userRepository.save(user);

        accountDeletionTokenRepository.deleteByUser_Id(user.getId());
        accountSecurityService.recordAccountDeletionCancelled(user.getId());
    }

    @Transactional
    public void finalizeDueDeletions() {
        List<UserEntity> dueUsers = userRepository.findByPendingDeletionTrueAndDeletionScheduledForLessThanEqual(LocalDateTime.now());
        for (UserEntity user : dueUsers) {
            try {
                partyService.handleUserDeletion(user.getId());
                user.setDeletionScheduledFor(null);
                userRepository.save(user);
                accountDeletionTokenRepository.deleteByUser_Id(user.getId());
                log.info("Finalized pending account deletion for userId={}", user.getId());
            } catch (RuntimeException e) {
                log.error("Failed to finalize pending account deletion for userId={}", user.getId(), e);
            }
        }
    }

    private AccountDeletionToken validateRecoveryToken(String token) {
        AccountDeletionToken deletionToken = accountDeletionTokenRepository.findByToken(token)
                .orElseThrow(() -> new BadRequestBusinessException("INVALID_RECOVERY_LINK", "유효하지 않은 복구 링크입니다."));

        if (deletionToken.isUsed()) {
            throw new BadRequestBusinessException("RECOVERY_LINK_ALREADY_USED", "이미 사용된 복구 링크입니다.");
        }
        if (deletionToken.isExpired()) {
            throw new BadRequestBusinessException("RECOVERY_LINK_EXPIRED", "복구 링크가 만료되었습니다.");
        }
        UserEntity user = deletionToken.getUser();
        if (user == null || user.getId() == null || !user.isPendingDeletion() || user.getDeletionScheduledFor() == null) {
            throw new BadRequestBusinessException(
                    "RECOVERABLE_DELETION_NOT_FOUND",
                    "복구 가능한 계정 삭제 예약을 찾을 수 없습니다.");
        }
        return deletionToken;
    }
}
