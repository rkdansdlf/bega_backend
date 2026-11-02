package com.example.cheerboard.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.domain.ReportReason;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;

/**
 * Retention lifecycle of reported post images, run against in-memory H2 with a mocked object store.
 * Every time is passed explicitly, so no test depends on the wall clock, except the public overloads that
 * read the server clock, which are covered by rejection tests only.
 * Each test runs as an administrator (account number 7) unless it says otherwise.
 */
@DataJpaTest
@Import(ReportEvidenceMediaRetentionService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:report_evidence_retention;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
// Each test rolls back, so rows from one test never change the counts another test asserts.
@Transactional
class ReportEvidenceMediaRetentionServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 10, 9, 0);
    private static final String ADMIN_ACCOUNT = "7";

    @Autowired private ReportEvidenceMediaRetentionService service;
    @Autowired private ReportEvidenceMediaRetentionRepository retentionRepository;
    @Autowired private CheerPostRepo postRepository;
    @Autowired private CheerReportRepo reportRepository;
    @Autowired private PostImageRepository postImageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @MockitoBean private StorageStrategy storageStrategy;
    @MockitoBean private StorageConfig storageConfig;

    @BeforeEach
    void stubStorageAndSignIn() {
        when(storageConfig.getCheerBucket()).thenReturn("cheer-test");
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenReturn(Mono.empty());
        signIn(ADMIN_ACCOUNT, "ROLE_ADMIN");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void discoveredImageGetsDefaultAndHardLimitFromTheFirstRequest() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);

        service.discover(BASE.plusHours(1));

        ReportEvidenceMediaRetention row = rowFor(fixture);
        assertThat(row.getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        assertThat(row.getRetainUntil()).isEqualTo(BASE.plusDays(30));
        assertThat(row.getHardLimitAt()).isEqualTo(BASE.plusDays(90));
    }

    @Test
    void reviewRowsPastTheDefaultWindowAreCountedForOperatorAlert() {
        reportedImage(null, CheerPostReport.ReportStatus.PENDING, true, null);
        service.discover(BASE);

        assertThat(service.alertStaleReviewRows(BASE.plusDays(29))).isZero();
        assertThat(service.alertStaleReviewRows(BASE.plusDays(31))).isEqualTo(1);
    }

    @Test
    void legacyOwnerWithoutFirstRequestDateIsSentToReviewAndNeverDeleted() {
        Fixture fixture = reportedImage(null, CheerPostReport.ReportStatus.PENDING, true, null);

        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(200));
        service.deleteDueObjects(BASE.plusDays(200));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void openCasePastItsWindowGoesToUrgentReviewAndIsNotDeletedOnTheSpot() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);

        service.expireDueRows(BASE.plusDays(29));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);

        service.expireDueRows(BASE.plusDays(31));
        ReportEvidenceMediaRetention urgent = rowFor(fixture);
        assertThat(urgent.getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
        assertThat(urgent.getUrgentReviewStartedAt()).isEqualTo(BASE.plusDays(31));
        assertThat(urgent.getUrgentReviewDueAt()).isEqualTo(BASE.plusDays(31).plusHours(24));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void urgentGraceEndsAfterTwentyFourHoursWithoutDecisionAndThenDeletes() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        assertThat(service.expireUrgentRows(BASE.plusDays(31).plusHours(23))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);

        assertThat(service.expireUrgentRows(BASE.plusDays(31).plusHours(25))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);

        assertThat(service.deleteDueObjects(BASE.plusDays(31).plusHours(25))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void urgentGraceIsGrantedOnlyOncePerRow() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();

        // An approved extension returns the row to ACTIVE; when that window ends again the grace is already used.
        service.approveExtensionAt(retentionId, BASE.plusDays(60), caseRef(fixture), BASE.plusDays(32));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);

        service.expireDueRows(BASE.plusDays(61));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
    }

    @Test
    void urgentRowWhoseCaseClosesIsQueuedForDeletionWithoutWaiting() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        jdbcTemplate.update("UPDATE cheer_post_reports SET status = ? WHERE id = ?", "RESOLVED", fixture.reportId());
        service.expireUrgentRows(BASE.plusDays(31).plusHours(1));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
    }

    @Test
    void closedCaseIsQueuedForDeletionBeforeTheDefaultWindowEnds() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);

        service.expireDueRows(BASE.plusDays(1));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);

        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void approvedExtensionKeepsOpenCaseImageUntilTheApprovedDate() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        service.approveExtensionAt(retentionId, BASE.plusDays(80), caseRef(fixture), BASE.plusDays(10));

        ReportEvidenceMediaRetention extended = rowFor(fixture);
        assertThat(extended.getExtensionApprovedBy()).isEqualTo(ADMIN_ACCOUNT);
        assertThat(extended.getExtensionCaseRef()).isEqualTo(caseRef(fixture));

        service.expireDueRows(BASE.plusDays(31));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);

        service.expireDueRows(BASE.plusDays(81));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
    }

    @Test
    void extensionIsRecordedInTheAdministratorAuditLog() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);

        service.approveExtensionAt(rowFor(fixture).getId(), BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10));

        List<AuditLog> entries = auditLogRepository.findAll().stream()
                .filter(entry -> entry.getAction() == AuditLog.AuditAction.EVIDENCE_EXTENSION_APPROVED)
                .toList();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getAdminId()).isEqualTo(7L);
        assertThat(entries.get(0).getTargetUserId()).isEqualTo(fixture.ownerId());
    }

    @Test
    void extensionCaseReferenceIsTrimmedAndLengthChecked() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        service.approveExtensionAt(retentionId, BASE.plusDays(60), " " + caseRef(fixture) + " ", BASE.plusDays(10));
        assertThat(rowFor(fixture).getExtensionCaseRef()).isEqualTo(caseRef(fixture));

        assertThatThrownBy(() -> service.approveExtensionAt(
                retentionId, BASE.plusDays(60), "1".repeat(65), BASE.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publicExtensionReadsTheServerClockAndRejectsDatesAlreadyPast() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);

        // BASE is in the past for the server clock, so any until value is rejected.
        assertThatThrownBy(() -> service.approveExtension(rowFor(fixture).getId(), BASE.plusDays(60), caseRef(fixture)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(fixture).getExtensionUntil()).isNull();
    }

    @Test
    void extensionBeyondNinetyDaysIsRejected() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> service.approveExtensionAt(
                retentionId, BASE.plusDays(91), caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(fixture).getExtensionUntil()).isNull();
    }

    @Test
    void extensionWithoutAnOpenCaseOrCaseReferenceIsRejected() {
        Fixture closed = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        Long closedId = rowFor(closed).getId();

        assertThatThrownBy(() -> service.approveExtensionAt(
                closedId, BASE.plusDays(60), caseRef(closed), BASE.plusDays(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.approveExtensionAt(
                closedId, BASE.plusDays(60), " ", BASE.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(closed).getExtensionUntil()).isNull();
    }

    @Test
    void extensionCaseReferenceMustBeAnOpenReportOfThisPost() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> service.approveExtensionAt(
                retentionId, BASE.plusDays(60), "999999", BASE.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(fixture).getExtensionUntil()).isNull();
    }

    @Test
    void extensionByNonAdministratorOrAnonymousCallerIsRejected() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        signIn("8", "ROLE_USER");
        assertThatThrownBy(() -> service.approveExtensionAt(
                retentionId, BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(AccessDeniedException.class);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.approveExtensionAt(
                retentionId, BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rowFor(fixture).getExtensionUntil()).isNull();
        assertThat(rowFor(fixture).getExtensionApprovedBy()).isNull();
    }

    @Test
    void adminAccountNameThatIsNotNumericIsRejected() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        signIn("admin-one", "ROLE_ADMIN");

        assertThatThrownBy(() -> service.approveExtensionAt(
                rowFor(fixture).getId(), BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void legalHoldKeepsImageUntilReviewDueThenMovesItToReview() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> service.placeLegalHoldAt(retentionId, " ", BASE.plusDays(60), BASE.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        service.placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중", BASE.plusDays(60), BASE.plusDays(1));
        assertThat(rowFor(fixture).getLegalHoldApprovedBy()).isEqualTo(ADMIN_ACCOUNT);

        service.expireDueRows(BASE.plusDays(31));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);

        service.expireDueRows(BASE.plusDays(61));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void legalHoldReviewDueMoreThanOneYearAheadIsRejectedAndNonAdministratorIsRejected() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> service.placeLegalHoldAt(
                retentionId, "법령 보존 의무 검토 중", BASE.plusDays(1).plusDays(366), BASE.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        signIn("8", "ROLE_USER");
        assertThatThrownBy(() -> service.placeLegalHoldAt(
                retentionId, "법령 보존 의무 검토 중", BASE.plusDays(60), BASE.plusDays(1)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rowFor(fixture).getLegalBasis()).isNull();
    }

    @Test
    void storageFailureKeepsTheImageAndRetriesWithBackoff() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic object store outage")));
        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();

        ReportEvidenceMediaRetention failed = rowFor(fixture);
        assertThat(failed.getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getNextAttemptAt()).isEqualTo(BASE.plusDays(31).plusMinutes(10));
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();

        // Before the retry time nothing is attempted.
        assertThat(service.deleteDueObjects(BASE.plusDays(31).plusMinutes(5))).isZero();

        when(storageStrategy.deleteChecked(anyString(), anyString())).thenReturn(Mono.empty());
        assertThat(service.deleteDueObjects(BASE.plusDays(31).plusMinutes(10))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void storageDeleteRunsOffTheCallingThreadSoTheTimeoutCanInterruptIt() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));

        AtomicReference<String> thread = new AtomicReference<>();
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.<Void>fromRunnable(() -> thread.set(Thread.currentThread().getName())));

        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isEqualTo(1);
        assertThat(thread.get()).contains("boundedElastic");
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void retentionRunDoesNotTouchThePurgeStateOfTheReportedPost() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);

        service.runDaily(BASE.plusDays(31));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT purge_started_at FROM cheer_post WHERE id = ?", Object.class, fixture.postId())).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cheer_post WHERE id = ?", Integer.class, fixture.postId())).isOne();
    }

    @Test
    void recoveredOwnerIsNeverRegisteredOnTheOldRequest() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, false, null);

        assertThat(service.discover(BASE)).isZero();
        assertThat(retentionRepository.findAll()).isEmpty();
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void ownerWhoRecoversAfterRegistrationGetsRowClosedAsRestoredNotReview() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        jdbcTemplate.update("UPDATE users SET pending_deletion = ? WHERE id = ?", false, fixture.ownerId());

        service.runDaily(BASE.plusDays(1));

        ReportEvidenceMediaRetention closed = rowFor(fixture);
        assertThat(closed.getStatus()).isEqualTo(ReportEvidenceMediaStatus.RESOLVED_RESTORED);
        assertThat(closed.getResolvedAt()).isEqualTo(BASE.plusDays(1));
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void restoredRowDoesNotBlockANewRegistrationAfterAReRequest() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        jdbcTemplate.update("UPDATE users SET pending_deletion = ? WHERE id = ?", false, fixture.ownerId());
        service.runDaily(BASE.plusDays(1));

        LocalDateTime cycleStart = BASE.plusDays(100);
        jdbcTemplate.update("UPDATE users SET pending_deletion = ?, deletion_cycle_started_at = ? WHERE id = ?",
                true, cycleStart, fixture.ownerId());
        jdbcTemplate.update("UPDATE cheer_post_reports SET createdat = ? WHERE id = ?",
                cycleStart.plusDays(1), fixture.reportId());

        assertThat(service.discover(cycleStart.plusDays(2))).isEqualTo(1);

        List<ReportEvidenceMediaRetention> rows = rowsFor(fixture);
        assertThat(rows).hasSize(2);
        ReportEvidenceMediaRetention live = rows.stream()
                .filter(row -> row.getStatus() == ReportEvidenceMediaStatus.ACTIVE)
                .findFirst()
                .orElseThrow();
        assertThat(live.getBaseRequestedAt()).isEqualTo(cycleStart);
    }

    @Test
    void storedBaseOfRegisteredEvidenceDoesNotChangeWhenTheAccountStateLaterChanges() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);

        // Finalization and admin deletion clear or move the owner's dates; the stored evidence base must not move.
        jdbcTemplate.update("UPDATE users SET first_deletion_requested_at = ?, deletion_cycle_started_at = ?, "
                + "deletion_requested_at = NULL WHERE id = ?", BASE.plusDays(500), BASE.plusDays(500), fixture.ownerId());

        assertThat(service.discover(BASE.plusDays(501))).isZero();
        ReportEvidenceMediaRetention row = rowFor(fixture);
        assertThat(row.getBaseRequestedAt()).isEqualTo(BASE);
        assertThat(row.getCycleStartedAt()).isNull();
        assertThat(row.getRetainUntil()).isEqualTo(BASE.plusDays(30));
    }

    @Test
    void openCaseFromBeforeAnAdminDeletionIsNotDeletedOnTheFirstRun() {
        // Earlier cancelled request (first date BASE), then an admin deletion that starts a new cycle at BASE+200.
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        jdbcTemplate.update("UPDATE users SET deletion_cycle_started_at = ?, deletion_requested_at = NULL WHERE id = ?",
                BASE.plusDays(200), fixture.ownerId());
        jdbcTemplate.update("UPDATE cheer_post_reports SET createdat = ? WHERE id = ?",
                BASE.plusDays(10), fixture.reportId());

        service.runDaily(BASE.plusDays(200));

        ReportEvidenceMediaRetention row = rowFor(fixture);
        assertThat(row.getBaseRequestedAt()).isEqualTo(BASE);
        assertThat(row.getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void evidenceReportedBeforeRerequestKeepsTheFirstRequestBaseAndIsNotExtended() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        LocalDateTime cycleStart = BASE.plusDays(100);
        jdbcTemplate.update("UPDATE users SET deletion_cycle_started_at = ? WHERE id = ?", cycleStart, fixture.ownerId());
        jdbcTemplate.update("UPDATE cheer_post_reports SET createdat = ? WHERE id = ?",
                BASE.plusDays(10), fixture.reportId());

        service.discover(cycleStart.plusDays(2));

        ReportEvidenceMediaRetention row = rowFor(fixture);
        assertThat(row.getBaseRequestedAt()).isEqualTo(BASE);
        assertThat(row.getRetainUntil()).isEqualTo(BASE.plusDays(30));

        service.expireDueRows(cycleStart.plusDays(2));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
    }

    @Test
    void evidenceReportedAfterRerequestGetsTheCurrentCycleBase() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        LocalDateTime cycleStart = BASE.plusDays(100);
        jdbcTemplate.update("UPDATE users SET deletion_cycle_started_at = ? WHERE id = ?", cycleStart, fixture.ownerId());
        jdbcTemplate.update("UPDATE cheer_post_reports SET createdat = ? WHERE id = ?",
                cycleStart.plusDays(1), fixture.reportId());

        service.discover(cycleStart.plusDays(2));

        ReportEvidenceMediaRetention row = rowFor(fixture);
        assertThat(row.getBaseRequestedAt()).isEqualTo(cycleStart);
        assertThat(row.getCycleStartedAt()).isEqualTo(cycleStart);
        assertThat(row.getRetainUntil()).isEqualTo(cycleStart.plusDays(30));
        assertThat(row.getHardLimitAt()).isEqualTo(cycleStart.plusDays(90));
        assertThat(row.getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
    }

    @Test
    void otherImageOnAnotherSpellingOfTheSameKeyKeepsTheObject() {
        String sharedPath = "posts/shared/" + UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);
        reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, "cheer-test/" + sharedPath);

        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));

        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void sharedStorageObjectIsRegisteredOnceAndSentToReviewWhileAnotherImageStillPointsAtIt() {
        String sharedPath = "posts/shared/" + UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);
        reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);

        assertThat(service.discover(BASE)).isEqualTo(1);
        service.expireDueRows(BASE.plusDays(31));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void imageOfOwnerInGraceWindowIsKeptUntilFinalization() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        jdbcTemplate.update("UPDATE users SET deletion_scheduled_for = ? WHERE id = ?",
                BASE.plusDays(7), fixture.ownerId());
        service.discover(BASE);

        service.runDaily(BASE.plusDays(1));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());

        jdbcTemplate.update("UPDATE users SET deletion_scheduled_for = NULL WHERE id = ?", fixture.ownerId());
        service.runDaily(BASE.plusDays(31));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void assetLinkedToThisPostDoesNotBlockDeletion() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        linkAsset(fixture.ownerId(), fixture.storagePath(), fixture.postId());
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void assetLinkedToAnotherEntityStillBlocksObjectDeletion() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        linkAsset(fixture.ownerId(), fixture.storagePath(), 999_999L);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void legalHoldPlacedAfterDeletionWasQueuedReactivatesTheRowAndBlocksDeletion() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);

        service.placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중", BASE.plusDays(60), BASE.plusDays(31).plusMinutes(1));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        assertThat(service.deleteDueObjects(BASE.plusDays(31).plusMinutes(2))).isZero();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void appealReopenedAfterDeletionWasQueuedKeepsImageUntilTheDefaultWindowEnds() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);

        jdbcTemplate.update("UPDATE cheer_post_reports SET appeal_status = ? WHERE post_id = ?",
                "REQUESTED", fixture.postId());
        assertThat(service.deleteDueObjects(BASE.plusDays(2))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());

        service.expireDueRows(BASE.plusDays(31));
        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    /** Inserts a CHEER media asset on the given object key and links it to an entity. */
    private void linkAsset(Long ownerId, String objectKey, Long linkedEntityId) {
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update("INSERT INTO media_assets (owner_user_id, domain, status, declared_content_type, "
                        + "declared_bytes, declared_width, declared_height, staging_object_key, object_key, "
                        + "upload_expires_at, created_at, updated_at) "
                        + "VALUES (?, 'CHEER', 'READY', 'image/webp', 1, 1, 1, ?, ?, ?, ?, ?)",
                ownerId, "staging/" + UUID.randomUUID(), objectKey, now, now, now);
        Long assetId = jdbcTemplate.queryForObject(
                "SELECT id FROM media_assets WHERE object_key = ?", Long.class, objectKey);
        jdbcTemplate.update("INSERT INTO media_asset_links (asset_id, domain, entity_id, role, linked_at) "
                        + "VALUES (?, 'CHEER', ?, 'CHEER_IMAGE', ?)",
                assetId, linkedEntityId, now);
    }

    private static void signIn(String principal, String authority) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority(authority))));
    }

    private static String caseRef(Fixture fixture) {
        return String.valueOf(fixture.reportId());
    }

    private ReportEvidenceMediaRetention rowFor(Fixture fixture) {
        return rowsFor(fixture).stream()
                .filter(row -> row.getStatus() != ReportEvidenceMediaStatus.RESOLVED_RESTORED)
                .findFirst()
                .orElseGet(() -> rowsFor(fixture).get(0));
    }

    private List<ReportEvidenceMediaRetention> rowsFor(Fixture fixture) {
        return retentionRepository.findAll().stream()
                .filter(row -> row.getStoragePath().equals(fixture.storagePath()))
                .toList();
    }

    private Fixture reportedImage(LocalDateTime firstDeletionRequestedAt, CheerPostReport.ReportStatus status) {
        return reportedImage(firstDeletionRequestedAt, status, true, null);
    }

    private Fixture reportedImage(LocalDateTime firstDeletionRequestedAt, CheerPostReport.ReportStatus status,
                                  boolean pendingDeletion, String sharedPath) {
        return new TransactionTemplate(transactionManager).execute(tx -> {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                    .teamId("RT" + suffix)
                    .teamName("Retention Team")
                    .teamShortName("RT")
                    .city("Synthetic")
                    .build());
            UserEntity author = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("r" + suffix)
                    .name("Synthetic Author")
                    .email("retention-author-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .firstDeletionRequestedAt(firstDeletionRequestedAt)
                    .pendingDeletion(pendingDeletion)
                    .build());
            UserEntity reporter = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("s" + suffix)
                    .name("Synthetic Reporter")
                    .email("retention-reporter-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .build());
            CheerPost post = postRepository.saveAndFlush(CheerPost.builder()
                    .author(author)
                    .team(team)
                    .postType(PostType.NORMAL)
                    .content("synthetic reported content")
                    .build());
            String storagePath = sharedPath != null
                    ? sharedPath
                    : "posts/" + post.getId() + "/" + UUID.randomUUID() + ".webp";
            PostImage image = postImageRepository.saveAndFlush(PostImage.builder()
                    .post(post)
                    .storagePath(storagePath)
                    .mimeType("image/webp")
                    .bytes(1L)
                    .isThumbnail(false)
                    .build());
            CheerPostReport report = reportRepository.saveAndFlush(CheerPostReport.builder()
                    .post(post)
                    .reporter(reporter)
                    .reason(ReportReason.SPAM)
                    .status(status)
                    .appealStatus(CheerPostReport.AppealStatus.NONE)
                    .build());
            return new Fixture(post.getId(), image.getId(), storagePath, author.getId(), report.getId());
        });
    }

    private record Fixture(Long postId, Long postImageId, String storagePath, Long ownerId, Long reportId) {}
}
