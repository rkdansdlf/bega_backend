package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.admin.repository.AuditLogRepository;
import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.auth.entity.AccountDeletionToken;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.common.exception.BadRequestBusinessException;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.TeamRepository;
import com.example.leaderboard.entity.ScoreEvent;
import com.example.leaderboard.repository.ScoreEventRepository;
import com.example.mate.service.PartyService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({AccountDeletionService.class, AccountDeletionFinalizationService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountDeletionFinalizationConcurrencyIntegrationTest {

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private AccountDeletionFinalizationService accountDeletionFinalizationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @Autowired
    private CheerPostLikeRepo likeRepository;

    @Autowired
    private CheerPostRepo cheerPostRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GameRepository gameRepository;

    @Autowired
    private BegaDiaryRepository diaryRepository;

    @Autowired
    private SeatViewPhotoRepository seatViewPhotoRepository;

    @Autowired
    private ScoreEventRepository scoreEventRepository;

    @MockitoBean
    private PartyService partyService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private AccountSecurityService accountSecurityService;

    @MockitoBean
    private AccountDeletionContentService contentService;

    @MockitoBean
    private AccountDeletionReviewService reviewService;

    @MockitoBean
    private AccountDeletionFinancialService financialService;

    @MockitoBean
    private AccountDeletionIdentityService identityService;

    @Test
    void hashedAndLegacyRecoveryTokensAreConsumedAndCannotBeReplayed() throws Exception {
        for (boolean hashed : new boolean[] {true, false}) {
            LocalDateTime expiry = LocalDateTime.now().plusDays(1).truncatedTo(ChronoUnit.MICROS);
            UserEntity user = savePendingUser(expiry);
            String raw = UUID.randomUUID().toString();
            String stored = hashed ? java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))) : raw;
            accountDeletionTokenRepository.saveAndFlush(AccountDeletionToken.builder()
                    .token(stored).user(user).expiryDate(expiry).build());

            if (hashed) {
                assertThatThrownBy(() -> accountDeletionService.recoverAccount(stored))
                        .isInstanceOf(BadRequestBusinessException.class);
            }
            accountDeletionService.recoverAccount(raw);
            assertThat(userRepository.findById(user.getId()).orElseThrow().isEnabled()).isTrue();
            assertThat(accountDeletionTokenRepository.findByToken(stored)).isEmpty();
            assertThatThrownBy(() -> accountDeletionService.recoverAccount(raw))
                    .isInstanceOf(BadRequestBusinessException.class);
        }
    }

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @Test
    void finalizerFailureRollsBackOnlyThatUserAndContinuesBatch() {
        // DB timestamps are microsecond precision; Linux clocks are nanosecond. Compare at the stored precision.
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        UserEntity failedUser = savePendingUser(dueAt);
        UserEntity successfulUser = savePendingUser(dueAt);
        String failedToken = saveRecoveryToken(failedUser, dueAt);
        String successfulToken = saveRecoveryToken(successfulUser, dueAt);
        doThrow(new IllegalStateException("synthetic user-scoped failure"))
                .when(partyService).handleUserDeletion(failedUser.getId());

        accountDeletionService.finalizeDueDeletions();

        UserEntity failedAfter = userRepository.findById(failedUser.getId()).orElseThrow();
        UserEntity successfulAfter = userRepository.findById(successfulUser.getId()).orElseThrow();
        assertThat(failedAfter.getDeletionScheduledFor()).isEqualTo(dueAt);
        assertThat(successfulAfter.getDeletionScheduledFor()).isNull();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(failedToken, failedUser.getId())).isPresent();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(successfulToken, successfulUser.getId())).isEmpty();
    }

    @Test
    void recoveryCannotReviveAnAccountAfterFinalizerWinsTheUserLock() throws Exception {
        // DB timestamps are microsecond precision; Linux clocks are nanosecond. Compare at the stored precision.
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        UserEntity user = savePendingUser(dueAt);
        String recoveryToken = saveRecoveryToken(user, dueAt);
        CountDownLatch finalizerHoldingUserLock = new CountDownLatch(1);
        CountDownLatch releaseFinalizer = new CountDownLatch(1);
        doAnswer(invocation -> {
            finalizerHoldingUserLock.countDown();
            if (!releaseFinalizer.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release synthetic finalizer");
            }
            return null;
        }).when(partyService).handleUserDeletion(user.getId());

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> finalization = workers.submit(() -> accountDeletionFinalizationService
                    .finalizeIfDue(user.getId(), LocalDateTime.now()));
            assertThat(finalizerHoldingUserLock.await(5, TimeUnit.SECONDS)).isTrue();

            Future<String> recovery = workers.submit(() -> {
                try {
                    accountDeletionService.recoverAccount(recoveryToken);
                    return "recovered";
                } catch (BadRequestBusinessException expected) {
                    return expected.getMessage();
                }
            });

            releaseFinalizer.countDown();
            assertThat(finalization.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(recovery.get(5, TimeUnit.SECONDS)).contains("유효하지 않은 복구 링크");
        } finally {
            releaseFinalizer.countDown();
            workers.shutdownNow();
        }

        UserEntity finalizedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(finalizedUser.isPendingDeletion()).isTrue();
        assertThat(finalizedUser.isEnabled()).isFalse();
        assertThat(finalizedUser.getDeletionScheduledFor()).isNull();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(recoveryToken, user.getId())).isEmpty();
    }

    @Test
    void failedAdminFinalizationKeepsDueMarkerAndScheduledRetryReplaysLikeCleanup() {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        UserEntity adminDeletedUser = saveAdminPendingUser(dueAt);
        String recoveryToken = saveRecoveryToken(adminDeletedUser, dueAt);
        String teamId = "SYNTH" + UUID.randomUUID().toString().substring(0, 8);
        TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                .teamId(teamId)
                .teamName("Synthetic team")
                .teamShortName("SYN")
                .city("Synthetic")
                .build());
        CheerPost post = cheerPostRepository.saveAndFlush(CheerPost.builder()
                .team(team)
                .author(adminDeletedUser)
                .content("synthetic account deletion retry post")
                .postType(PostType.NORMAL)
                .likeCount(1)
                .build());
        CheerPostLike like = new CheerPostLike();
        like.setId(new CheerPostLike.Id(post.getId(), adminDeletedUser.getId()));
        like.setPost(post);
        like.setUser(adminDeletedUser);
        likeRepository.saveAndFlush(like);
        doThrow(new IllegalStateException("synthetic content finalization failure"))
                .doNothing()
                .when(contentService).anonymize(adminDeletedUser.getId());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> accountDeletionFinalizationService
                .finalizeIfDue(adminDeletedUser.getId(), LocalDateTime.now()))
                .isInstanceOf(IllegalStateException.class);

        UserEntity afterFailure = userRepository.findById(adminDeletedUser.getId()).orElseThrow();
        assertThat(afterFailure.isEnabled()).isFalse();
        assertThat(afterFailure.isPendingDeletion()).isTrue();
        assertThat(afterFailure.getDeletionRequestedAt()).isNull();
        assertThat(afterFailure.getDeletionScheduledFor()).isEqualTo(dueAt);
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(
                recoveryToken, adminDeletedUser.getId())).isPresent();
        assertThat(likeRepository.countByPostId(post.getId())).isEqualTo(1L);
        assertThat(cheerPostRepository.findLikeCountById(post.getId())).isEqualTo(1);

        assertThat(accountDeletionFinalizationService.finalizeIfDue(
                adminDeletedUser.getId(), LocalDateTime.now())).isTrue();

        UserEntity afterRetry = userRepository.findById(adminDeletedUser.getId()).orElseThrow();
        assertThat(afterRetry.getDeletionScheduledFor()).isNull();
        assertThat(accountDeletionTokenRepository.findByTokenAndUser_Id(
                recoveryToken, adminDeletedUser.getId())).isEmpty();
        assertThat(likeRepository.countByPostId(post.getId())).isZero();
        assertThat(cheerPostRepository.findLikeCountById(post.getId())).isZero();
        verify(contentService, times(2)).anonymize(adminDeletedUser.getId());
    }

    @Test
    void adminFinalizationRetainsApprovedDiarySeatViewAndScoreHistory() {
        LocalDateTime dueAt = LocalDateTime.now().minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        UserEntity adminDeletedUser = saveAdminPendingUser(dueAt);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate syntheticDate = LocalDate.of(2099, 1, 1);
        String syntheticMemo = "synthetic retention regression";
        String syntheticPhotoPath = "synthetic/retention/seat-view.jpg";
        String syntheticScoreDescription = "synthetic retention regression";
        GameEntity syntheticGame = gameRepository.saveAndFlush(GameEntity.builder()
                .gameId("TEST-" + suffix)
                .gameDate(syntheticDate)
                .stadium("SYNTHETIC")
                .homeTeam("TEST-HOME")
                .awayTeam("TEST-AWAY")
                .isDummy(true)
                .build());
        BegaDiary diary = diaryRepository.saveAndFlush(BegaDiary.builder()
                .diaryDate(syntheticDate)
                .game(syntheticGame)
                .memo(syntheticMemo)
                .mood(BegaDiary.DiaryEmoji.HAPPY)
                .type(BegaDiary.DiaryType.SCHEDULED)
                .user(adminDeletedUser)
                .team("TEST-HOME")
                .stadium("SYNTHETIC")
                .build());
        SeatViewPhoto seatViewPhoto = seatViewPhotoRepository.saveAndFlush(SeatViewPhoto.builder()
                .diary(diary)
                .userId(adminDeletedUser.getId())
                .storagePath(syntheticPhotoPath)
                .sourceType(SeatViewPhoto.SourceType.DIARY_UPLOAD)
                .userSelected(false)
                .rewardGranted(false)
                .build());
        ScoreEvent scoreEvent = scoreEventRepository.saveAndFlush(ScoreEvent.builder()
                .userId(adminDeletedUser.getId())
                .eventType(ScoreEvent.EventType.POWER_UP_MULTIPLIER)
                .baseScore(0)
                .finalScore(0)
                .description(syntheticScoreDescription)
                .build());

        assertThat(accountDeletionFinalizationService.finalizeIfDue(
                adminDeletedUser.getId(), LocalDateTime.now())).isTrue();

        BegaDiary retainedDiary = diaryRepository.findById(diary.getId()).orElseThrow();
        assertThat(retainedDiary.getDiaryDate()).isEqualTo(syntheticDate);
        assertThat(retainedDiary.getMemo()).isEqualTo(syntheticMemo);
        assertThat(retainedDiary.getUser().getId()).isEqualTo(adminDeletedUser.getId());

        SeatViewPhoto retainedPhoto = seatViewPhotoRepository.findById(seatViewPhoto.getId()).orElseThrow();
        assertThat(retainedPhoto.getStoragePath()).isEqualTo(syntheticPhotoPath);
        assertThat(retainedPhoto.getUserId()).isEqualTo(adminDeletedUser.getId());
        assertThat(retainedPhoto.getDiary().getId()).isEqualTo(diary.getId());

        ScoreEvent retainedScoreEvent = scoreEventRepository.findById(scoreEvent.getId()).orElseThrow();
        assertThat(retainedScoreEvent.getUserId()).isEqualTo(adminDeletedUser.getId());
        assertThat(retainedScoreEvent.getEventType()).isEqualTo(ScoreEvent.EventType.POWER_UP_MULTIPLIER);
        assertThat(retainedScoreEvent.getFinalScore()).isZero();
        assertThat(retainedScoreEvent.getDescription()).isEqualTo(syntheticScoreDescription);
    }

    private UserEntity savePendingUser(LocalDateTime scheduledFor) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@del" + suffix)
                .name("Deletion test")
                .email("deletion-" + suffix + "@example.test")
                .role("ROLE_USER")
                .enabled(false)
                .pendingDeletion(true)
                .deletionRequestedAt(scheduledFor.minusDays(7))
                .deletionScheduledFor(scheduledFor)
                .tokenVersion(1)
                .build());
    }

    private UserEntity saveAdminPendingUser(LocalDateTime scheduledFor) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@ad" + suffix)
                .name("Synthetic admin-delete target")
                .email("admin-deletion-" + suffix + "@example.test")
                .role("ROLE_USER")
                .enabled(false)
                .pendingDeletion(true)
                .deletionRequestedAt(null)
                .deletionScheduledFor(scheduledFor)
                .tokenVersion(1)
                .build());
    }

    private String saveRecoveryToken(UserEntity user, LocalDateTime expiryDate) {
        String token = UUID.randomUUID().toString();
        accountDeletionTokenRepository.saveAndFlush(AccountDeletionToken.builder()
                .token(token)
                .user(user)
                .expiryDate(expiryDate)
                .used(false)
                .build());
        return token;
    }
}
