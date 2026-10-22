package com.example.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.admin.dto.AdminReportActionReq;
import com.example.admin.dto.AdminReportAppealDecision;
import com.example.admin.dto.AdminReportAppealDecisionReq;
import com.example.admin.dto.AdminReportAppealReq;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AdminNonCanonicalCleanupTrackerRepository;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountDeletionContentService;
import com.example.auth.service.AccountDeletionFinalizationService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.domain.ReportReason;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.service.CheerPostService;
import com.example.common.exception.ConflictBusinessException;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.media.entity.MediaDomain;
import com.example.media.service.MediaUploadService;
import com.example.mate.repository.PartyRepository;
import com.example.prediction.PredictionService;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import({AdminService.class, AccountDeletionContentService.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_appeal_decision;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminReportAppealDecisionIntegrationTest {

    private static final long TIMEOUT_SECONDS = 10;

    @Autowired private AdminService adminService;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private CheerPostRepo postRepository;
    @Autowired private CheerReportRepo reportRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    @MockitoBean private PartyRepository partyRepository;
    @MockitoBean private CacheManager cacheManager;
    @MockitoBean private AuditLogRepository auditLogRepository;
    @MockitoBean private AdminNonCanonicalCleanupTrackerRepository cleanupTrackerRepository;
    @MockitoBean private PredictionService predictionService;
    @MockitoBean private CheerPostService cheerPostService;
    @MockitoBean private AdminUserDeletionPreparationService deletionPreparationService;
    @MockitoBean private AccountDeletionFinalizationService deletionFinalizationService;
    @MockitoBean private AdminMateDeletionGuard mateDeletionGuard;
    @MockitoBean private MediaUploadService mediaUploadService;

    @Test
    void accountContentAndOwnedMediaRemainUntilTheLastRelatedCaseIsClosed() {
        Fixture fixture = createFixture(true);
        AdminReportAppealDecisionReq accepted = new AdminReportAppealDecisionReq(AdminReportAppealDecision.ACCEPTED);

        adminService.decideReportAppeal(fixture.firstReportId(), accepted, null);

        assertThat(readPostContent(fixture.postId())).isEqualTo("synthetic moderation evidence");
        assertThat(readPostContent(fixture.unrelatedPostId())).isEqualTo("synthetic unrelated content");
        verify(mediaUploadService, never()).deleteOwnedAssetsForAccount(fixture.ownerId(), java.util.Set.of());

        transaction().execute(status -> {
            CheerPostReport otherCase = reportRepository.findByIdForWrite(fixture.otherReportId()).orElseThrow();
            otherCase.setStatus(CheerPostReport.ReportStatus.CLOSED);
            reportRepository.saveAndFlush(otherCase);
            return null;
        });

        adminService.decideReportAppeal(fixture.firstReportId(), accepted, null);

        assertThat(readPostContent(fixture.postId())).isEqualTo("게시글이 비식별 처리되었습니다.");
        assertThat(readPostContent(fixture.unrelatedPostId())).isEqualTo("게시글이 비식별 처리되었습니다.");
        verify(mediaUploadService).unlinkEntityMedia(MediaDomain.CHEER, fixture.postId());
        verify(mediaUploadService).unlinkEntityMedia(MediaDomain.CHEER, fixture.unrelatedPostId());
        verify(mediaUploadService).deleteOwnedAssetsForAccount(fixture.ownerId(), java.util.Set.of());
    }

    @Test
    void resolvingTheLastOpenReportAnonymizesDeletedOwnerContentAndOwnedMedia() {
        Fixture fixture = createFixture(false);
        transaction().execute(status -> {
            CheerPostReport report = reportRepository.findByIdForWrite(fixture.firstReportId()).orElseThrow();
            report.setStatus(CheerPostReport.ReportStatus.PENDING);
            report.setAppealStatus(CheerPostReport.AppealStatus.NONE);
            reportRepository.saveAndFlush(report);
            return null;
        });

        adminService.handleReport(
                fixture.firstReportId(),
                new AdminReportActionReq(CheerPostReport.AdminAction.WARNING, "synthetic resolution", null),
                9001L);

        assertThat(readPostContent(fixture.postId())).isEqualTo("게시글이 비식별 처리되었습니다.");
        assertThat(readPostContent(fixture.unrelatedPostId())).isEqualTo("게시글이 비식별 처리되었습니다.");
        verify(mediaUploadService).unlinkEntityMedia(MediaDomain.CHEER, fixture.postId());
        verify(mediaUploadService).unlinkEntityMedia(MediaDomain.CHEER, fixture.unrelatedPostId());
        verify(mediaUploadService).deleteOwnedAssetsForAccount(fixture.ownerId(), java.util.Set.of());
    }

    @Test
    void resolvedReportStillRetainsContentWhileItsAppealIsOpen() {
        Fixture fixture = createFixture(false);

        adminService.handleReport(
                fixture.firstReportId(),
                new AdminReportActionReq(CheerPostReport.AdminAction.WARNING, "synthetic resolution", null),
                9001L);

        assertThat(readPostContent(fixture.postId())).isEqualTo("synthetic moderation evidence");
        assertThat(readPostContent(fixture.unrelatedPostId())).isEqualTo("synthetic unrelated content");
        verify(mediaUploadService, never()).deleteOwnedAssetsForAccount(fixture.ownerId(), java.util.Set.of());

        adminService.decideReportAppeal(
                fixture.firstReportId(),
                new AdminReportAppealDecisionReq(AdminReportAppealDecision.REJECTED),
                9001L);

        assertThat(readPostContent(fixture.postId())).isEqualTo("게시글이 비식별 처리되었습니다.");
        assertThat(readPostContent(fixture.unrelatedPostId())).isEqualTo("게시글이 비식별 처리되었습니다.");
        verify(mediaUploadService).unlinkEntityMedia(MediaDomain.CHEER, fixture.postId());
        verify(mediaUploadService).unlinkEntityMedia(MediaDomain.CHEER, fixture.unrelatedPostId());
        verify(mediaUploadService).deleteOwnedAssetsForAccount(fixture.ownerId(), java.util.Set.of());
    }

    @Test
    void appealDecisionDoesNotAnonymizeContentForAnActiveAccount() {
        Fixture fixture = createFixture(false);
        transaction().execute(status -> {
            UserEntity owner = userRepository.findByIdForWrite(fixture.ownerId()).orElseThrow();
            owner.setEnabled(true);
            owner.setPendingDeletion(false);
            owner.setLocked(false);
            userRepository.saveAndFlush(owner);
            return null;
        });

        adminService.decideReportAppeal(
                fixture.firstReportId(),
                new AdminReportAppealDecisionReq(AdminReportAppealDecision.ACCEPTED),
                9001L);

        assertThat(readPostContent(fixture.postId())).isEqualTo("synthetic moderation evidence");
        verify(mediaUploadService, never()).unlinkEntityMedia(MediaDomain.CHEER, fixture.postId());
        verify(mediaUploadService, never()).deleteOwnedAssetsForAccount(fixture.ownerId(), java.util.Set.of());
    }

    @Test
    void decisionsRequireAnAppealAndCannotReverseATerminalDecision() {
        Fixture missingAppeal = createFixture(false);
        transaction().execute(status -> {
            CheerPostReport report = reportRepository.findByIdForWrite(missingAppeal.firstReportId()).orElseThrow();
            report.setAppealStatus(CheerPostReport.AppealStatus.NONE);
            reportRepository.saveAndFlush(report);
            return null;
        });

        assertThatThrownBy(() -> adminService.decideReportAppeal(
                missingAppeal.firstReportId(),
                new AdminReportAppealDecisionReq(AdminReportAppealDecision.REVIEWING),
                9001L))
                .isInstanceOf(ConflictBusinessException.class);

        Fixture terminalAppeal = createFixture(false);
        adminService.decideReportAppeal(
                terminalAppeal.firstReportId(),
                new AdminReportAppealDecisionReq(AdminReportAppealDecision.ACCEPTED),
                9001L);

        assertThatThrownBy(() -> adminService.decideReportAppeal(
                terminalAppeal.firstReportId(),
                new AdminReportAppealDecisionReq(AdminReportAppealDecision.REJECTED),
                9001L))
                .isInstanceOf(ConflictBusinessException.class);

        CheerPostReport.AppealStatus persistedStatus = transaction().execute(status ->
                reportRepository.findById(terminalAppeal.firstReportId()).orElseThrow().getAppealStatus());
        assertThat(persistedStatus).isEqualTo(CheerPostReport.AppealStatus.ACCEPTED);
        verify(mediaUploadService, times(1))
                .deleteOwnedAssetsForAccount(terminalAppeal.ownerId(), java.util.Set.of());
    }

    @Test
    void appealDecisionWaitsForOwnerLockBeforeTakingReportLock() throws Exception {
        Fixture fixture = createFixture(false);
        CountDownLatch ownerLockHeld = new CountDownLatch(1);
        CountDownLatch releaseOwnerLock = new CountDownLatch(1);
        CountDownLatch decisionTransactionStarted = new CountDownLatch(1);
        AtomicLong ownerSessionId = new AtomicLong();
        AtomicLong decisionSessionId = new AtomicLong();
        ExecutorService workers = Executors.newFixedThreadPool(3);

        try {
            Future<Void> ownerLock = workers.submit(() -> transaction().execute(status -> {
                ownerSessionId.set(currentSessionId());
                userRepository.findByIdForWrite(fixture.ownerId()).orElseThrow();
                ownerLockHeld.countDown();
                await(releaseOwnerLock);
                return null;
            }));
            assertThat(ownerLockHeld.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> decision = workers.submit(() -> transaction().execute(status -> {
                decisionSessionId.set(currentSessionId());
                decisionTransactionStarted.countDown();
                return adminService.decideReportAppeal(
                        fixture.firstReportId(),
                        new AdminReportAppealDecisionReq(AdminReportAppealDecision.REVIEWING),
                        null);
            }));
            assertThat(decisionTransactionStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(decisionSessionId.get(), ownerSessionId.get());

            long acquiredReportId = workers.submit(() -> transaction().execute(status ->
                    reportRepository.findByIdForWrite(fixture.firstReportId()).orElseThrow().getId()))
                    .get(3, TimeUnit.SECONDS);
            assertThat(acquiredReportId).isEqualTo(fixture.firstReportId());

            releaseOwnerLock.countDown();
            ownerLock.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            decision.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            releaseOwnerLock.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void appealRequestWaitsForOwnerLockBeforeTakingReportLock() throws Exception {
        Fixture fixture = createFixture(false);
        CountDownLatch ownerLockHeld = new CountDownLatch(1);
        CountDownLatch releaseOwnerLock = new CountDownLatch(1);
        CountDownLatch requestTransactionStarted = new CountDownLatch(1);
        AtomicLong ownerSessionId = new AtomicLong();
        AtomicLong requestSessionId = new AtomicLong();
        ExecutorService workers = Executors.newFixedThreadPool(3);

        try {
            Future<Void> ownerLock = workers.submit(() -> transaction().execute(status -> {
                ownerSessionId.set(currentSessionId());
                userRepository.findByIdForWrite(fixture.ownerId()).orElseThrow();
                ownerLockHeld.countDown();
                await(releaseOwnerLock);
                return null;
            }));
            assertThat(ownerLockHeld.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> request = workers.submit(() -> transaction().execute(status -> {
                requestSessionId.set(currentSessionId());
                requestTransactionStarted.countDown();
                return adminService.requestAppeal(
                        fixture.firstReportId(), new AdminReportAppealReq("synthetic appeal retry"), null);
            }));
            assertThat(requestTransactionStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(requestSessionId.get(), ownerSessionId.get());

            long acquiredReportId = workers.submit(() -> transaction().execute(status ->
                    reportRepository.findByIdForWrite(fixture.firstReportId()).orElseThrow().getId()))
                    .get(3, TimeUnit.SECONDS);
            assertThat(acquiredReportId).isEqualTo(fixture.firstReportId());

            releaseOwnerLock.countDown();
            ownerLock.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            request.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            CheerPostReport.AppealStatus currentStatus = transaction().execute(status ->
                    reportRepository.findById(fixture.firstReportId()).orElseThrow().getAppealStatus());
            assertThat(currentStatus).isEqualTo(CheerPostReport.AppealStatus.REQUESTED);
        } finally {
            releaseOwnerLock.countDown();
            workers.shutdownNow();
            assertThat(workers.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Fixture createFixture(boolean addOtherOpenCase) {
        return transaction().execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                    .teamId("AR" + suffix)
                    .teamName("Appeal Test Team")
                    .teamShortName("AR")
                    .city("Synthetic")
                    .build());
            UserEntity owner = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("d" + suffix)
                    .name("탈퇴한 사용자")
                    .email("pending-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .enabled(false)
                    .pendingDeletion(true)
                    .locked(true)
                    .build());
            owner.setEmail("deleted-" + owner.getId() + "@deleted.invalid");
            userRepository.saveAndFlush(owner);

            UserEntity reporter = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("r" + suffix)
                    .name("Synthetic Reporter")
                    .email("reporter-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .build());
            CheerPost post = postRepository.saveAndFlush(CheerPost.builder()
                    .author(owner)
                    .team(team)
                    .postType(PostType.NORMAL)
                    .content("synthetic moderation evidence")
                    .sourceUrl("https://synthetic.invalid/source")
                    .sourceTitle("Synthetic Source")
                    .build());
        CheerPost unrelatedPost = postRepository.saveAndFlush(CheerPost.builder()
                .author(owner)
                .team(team)
                .postType(PostType.NORMAL)
                .content("synthetic unrelated content")
                .build());
            CheerPostReport appeal = reportRepository.saveAndFlush(CheerPostReport.builder()
                    .post(post)
                    .reporter(reporter)
                    .reason(ReportReason.SPAM)
                    .status(CheerPostReport.ReportStatus.RESOLVED)
                    .appealStatus(CheerPostReport.AppealStatus.REVIEWING)
                    .appealReason("synthetic appeal")
                    .build());
            Long otherReportId = null;
            if (addOtherOpenCase) {
                CheerPostReport other = reportRepository.saveAndFlush(CheerPostReport.builder()
                        .post(post)
                        .reporter(reporter)
                        .reason(ReportReason.OTHER)
                        .status(CheerPostReport.ReportStatus.PENDING)
                        .build());
                otherReportId = other.getId();
            }
            entityManager.flush();
            return new Fixture(owner.getId(), post.getId(), unrelatedPost.getId(), appeal.getId(), otherReportId);
        });
    }

    private String readPostContent(Long postId) {
        return transaction().execute(status -> postRepository.findById(postId).orElseThrow().getContent());
    }

    private long currentSessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()")
                .getSingleResult()).longValue();
    }

    private void awaitBlockedBy(long blockedSessionId, long expectedBlockerSessionId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        Long blockerSessionId = null;
        while (System.nanoTime() < deadline) {
            blockerSessionId = jdbcTemplate.query(
                    "SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?",
                    resultSet -> {
                        if (!resultSet.next()) {
                            return null;
                        }
                        Number blocker = (Number) resultSet.getObject("BLOCKER_ID");
                        return blocker == null ? null : blocker.longValue();
                    },
                    blockedSessionId);
            if (blockerSessionId != null && blockerSessionId == expectedBlockerSessionId) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(blockerSessionId).isEqualTo(expectedBlockerSessionId);
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating synthetic moderation transactions");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating moderation transactions", exception);
        }
    }

    private record Fixture(Long ownerId, Long postId, Long unrelatedPostId, Long firstReportId, Long otherReportId) {
    }
}
