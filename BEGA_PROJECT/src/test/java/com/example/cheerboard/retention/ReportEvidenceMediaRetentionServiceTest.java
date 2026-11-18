package com.example.cheerboard.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
import java.util.concurrent.TimeoutException;
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
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
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
        "spring.jpa.show-sql=false",
        "report-evidence.media-retention.auto-delete-enabled=true"
})
@Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
class ReportEvidenceMediaRetentionServiceTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 10, 9, 0);
    private static final String ADMIN_ACCOUNT = "7";

    @Autowired private ReportEvidenceMediaRetentionService service;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private ReportEvidenceMediaRetentionRepository retentionRepository;
    @Autowired private CheerPostRepo postRepository;
    @Autowired private CheerReportRepo reportRepository;
    @Autowired private PostImageRepository postImageRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ReportEvidenceMediaRetentionEventRepository eventRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private com.example.BegaDiary.Repository.SeatViewPhotoRepository seatViewPhotos;

    @MockitoBean private StorageStrategy storageStrategy;
    @MockitoBean private StorageConfig storageConfig;

    @BeforeEach
    void stubStorageAndSignIn() {
        ReflectionTestUtils.setField(service, "monotonicNanos", (java.util.function.LongSupplier) () -> 0L);
        when(storageConfig.getCheerBucket()).thenReturn("cheer-test");
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenReturn(Mono.empty());
        signIn(ADMIN_ACCOUNT, "ROLE_ADMIN");
    }

    @AfterEach
    void signOut() {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                for (String table : List.of("report_evidence_media_retention_event", "report_evidence_media_retention",
                        "admin_audit_logs", "media_asset_links", "media_assets", "post_images", "cheer_post_reports",
                        "cheer_post", "users", "teams")) {
                    jdbcTemplate.update("DELETE FROM " + table);
                }
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
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
        assertThat(urgent.getUrgentReviewDueAt()).isEqualTo(BASE.plusDays(31).plusHours(23));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void urgentGraceEndsAfterTwentyThreeHoursWithoutDecisionAndThenDeletes() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        assertThat(service.expireUrgentRows(BASE.plusDays(31).plusHours(22))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);

        assertThat(service.expireUrgentRows(BASE.plusDays(32).plusMinutes(1))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);

        assertThat(service.deleteDueObjects(BASE.plusDays(32).plusMinutes(1))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void urgentRowIsNotDecidedInTheRunThatClassifiesIt() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        // Same run: the review has not lasted a run yet, so nothing is decided.
        assertThat(service.expireUrgentRows(BASE.plusDays(31))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
    }

    @Test
    void urgentDeadlineNeverPassesTheNinetyDayCapMinusTheMargin() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        // Window ends at day 87 plus 6h: a 12-hour grace still fits before the cap, so the 23-hour grace is cut to the deadline.
        approveExtensionAt(retentionId, BASE.plusDays(87).plusHours(6), caseRef(fixture), BASE.plusDays(10));
        service.expireDueRows(BASE.plusDays(87).plusHours(6));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
        assertThat(rowFor(fixture).getUrgentReviewDueAt()).isEqualTo(BASE.plusDays(88));
    }

    @Test
    void dailyRunKeepsOneRunUrgentReviewEvenWhenPhasesTakeTime() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();
        var elapsed = new java.util.concurrent.atomic.AtomicLong();
        org.mockito.Mockito.doAnswer(invocation -> {
            ReportEvidenceMediaStatus status = invocation.getArgument(0);
            if (status == ReportEvidenceMediaStatus.ACTIVE) {
                elapsed.set(java.util.concurrent.TimeUnit.SECONDS.toNanos(1));
                return List.of(retentionId);
            }
            if (status == ReportEvidenceMediaStatus.URGENT_REVIEW) return List.of(retentionId);
            return List.of();
        }).when(retentionRepository).findIdsByStatus(org.mockito.ArgumentMatchers.any());

        withField("monotonicNanos", (java.util.function.LongSupplier) elapsed::get,
                () -> service.runDaily(BASE.plusDays(31)));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void dailyRunIncludesEarlierPhaseDurationInDeletionClaimTime() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();
        var elapsed = new java.util.concurrent.atomic.AtomicLong();
        var claimedAt = new AtomicReference<LocalDateTime>();
        org.mockito.Mockito.doAnswer(invocation -> {
            elapsed.set(java.util.concurrent.TimeUnit.HOURS.toNanos(2));
            return List.of();
        }).when(retentionRepository).findUnregisteredReportedImagesWithBase();
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            claimedAt.set(retentionRepository.findById(retentionId).orElseThrow().getDeleteClaimedAt());
            return Mono.empty();
        });

        withField("monotonicNanos", (java.util.function.LongSupplier) elapsed::get,
                () -> service.runDaily(BASE.plusDays(1)));

        assertThat(claimedAt.get()).isEqualTo(BASE.plusDays(1).plusHours(2));
        assertThat(retentionRepository.findById(retentionId).orElseThrow().getStatus())
                .isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void laterClaimUsesElapsedTimeDuringLongDeletionRun() {
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        Fixture second = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        var elapsed = new java.util.concurrent.atomic.AtomicLong();
        var secondClaimedAt = new AtomicReference<LocalDateTime>();
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            if (second.storagePath().equals(invocation.getArgument(1))) {
                secondClaimedAt.set(rowFor(second).getDeleteClaimedAt());
            } else {
                elapsed.set(java.util.concurrent.TimeUnit.HOURS.toNanos(2));
            }
            return Mono.empty();
        });

        withField("monotonicNanos", (java.util.function.LongSupplier) elapsed::get, () ->
                assertThat(service.deleteDueObjects(BASE.plusDays(1))).isEqualTo(2));
        assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
        assertThat(rowFor(second).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
        assertThat(secondClaimedAt.get()).isEqualTo(BASE.plusDays(1).plusHours(2));
        verify(storageStrategy, times(2)).deleteChecked(anyString(), anyString());
    }

    @Test
    void previouslyUrgentUncertainDeletionDoesNotBecomeResolved() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic uncertain deletion")));
        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        Long retentionId = rowFor(fixture).getId();
        jdbcTemplate.update("UPDATE report_evidence_media_retention SET status = 'URGENT_REVIEW' WHERE id = ?", retentionId);
        jdbcTemplate.update("UPDATE users SET pending_deletion = ? WHERE id = ?", false, fixture.ownerId());

        assertThat(service.expireUrgentRows(BASE.plusDays(2))).isEqualTo(1);
        assertThat(retentionRepository.findById(retentionId).orElseThrow().getStatus())
                .isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, times(1)).deleteChecked(anyString(), anyString());
    }

    @Test
    void previouslyReactivatedUncertainDeletionDoesNotBecomeResolved() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic uncertain deletion")));
        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        Long retentionId = rowFor(fixture).getId();
        jdbcTemplate.update("UPDATE report_evidence_media_retention SET status = 'ACTIVE' WHERE id = ?", retentionId);
        jdbcTemplate.update("UPDATE users SET pending_deletion = ? WHERE id = ?", false, fixture.ownerId());

        assertThat(service.expireDueRows(BASE.plusDays(2))).isEqualTo(1);
        assertThat(retentionRepository.findById(retentionId).orElseThrow().getStatus())
                .isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, times(1)).deleteChecked(anyString(), anyString());
    }

    @Test
    void existingHoldAfterUncertainDeleteIsPreservedAndSentToReview() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic uncertain deletion")));
        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        Long retentionId = rowFor(fixture).getId();
        jdbcTemplate.update("UPDATE report_evidence_media_retention SET legal_basis = ?, legal_review_due_at = ? WHERE id = ?",
                "synthetic prior hold", BASE.plusDays(10), retentionId);

        assertThat(service.deleteDueObjects(BASE.plusDays(1).plusMinutes(10))).isZero();
        var row = retentionRepository.findById(retentionId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        assertThat(row.getLegalBasis()).isEqualTo("synthetic prior hold");
        verify(storageStrategy, times(1)).deleteChecked(anyString(), anyString());
    }

    @Test
    void recoveryGraceAfterUncertainDeleteGoesToReview() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic uncertain deletion")));
        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        jdbcTemplate.update("UPDATE users SET deletion_scheduled_for = ? WHERE id = ?",
                BASE.plusDays(10), fixture.ownerId());

        assertThat(service.deleteDueObjects(BASE.plusDays(1).plusMinutes(10))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, times(1)).deleteChecked(anyString(), anyString());
    }

    @Test
    void reopenedCaseAfterUncertainDeleteGoesToReviewInsteadOfActive() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic uncertain deletion")));
        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        jdbcTemplate.update("UPDATE cheer_post_reports SET appeal_status = ? WHERE post_id = ?",
                "REQUESTED", fixture.postId());

        assertThat(service.deleteDueObjects(BASE.plusDays(1).plusMinutes(10))).isZero();
        var row = rowFor(fixture);
        assertThat(row.getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        assertThat(row.getDeleteStartedAt()).isNotNull();
        assertThat(eventRepository.findByRetentionIdOrderByIdAsc(row.getId()))
                .extracting(ReportEvidenceMediaRetentionEvent::getEventType)
                .contains(ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED);
        verify(storageStrategy, times(1)).deleteChecked(anyString(), anyString());
    }

    @Test
    void ownerRestoredAfterUncertainDeleteGoesToReviewInsteadOfResolved() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic uncertain deletion")));
        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        jdbcTemplate.update("UPDATE users SET pending_deletion = ? WHERE id = ?", false, fixture.ownerId());

        assertThat(service.deleteDueObjects(BASE.plusDays(1).plusMinutes(10))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        assertThat(rowFor(fixture).getDeleteStartedAt()).isNotNull();
        verify(storageStrategy, times(1)).deleteChecked(anyString(), anyString());
    }

    @Test
    void appealAfterDeleteWasQueuedPastTheWindowReturnsTheRowToUrgentWithoutStorageCall() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);

        // Storage is unavailable, so the row waits in backoff; meanwhile an appeal reopens the case past the window.
        jdbcTemplate.update("UPDATE cheer_post_reports SET appeal_status = ? WHERE post_id = ?",
                "REQUESTED", fixture.postId());
        service.deleteDueObjects(BASE.plusDays(40));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());

        service.expireDueRows(BASE.plusDays(40));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void approvingAnExtensionWithoutADateIsRejectedBeforeTheRowIsLocked() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);

        assertThatThrownBy(() -> approveExtensionAt(
                rowFor(fixture).getId(), null, caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(fixture).getExtensionUntil()).isNull();
    }

    @Test
    void urgentGraceIsGrantedOnlyOncePerRow() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();

        // An approved extension returns the row to ACTIVE; when that window ends again the grace is already used.
        approveExtensionAt(retentionId, BASE.plusDays(60), caseRef(fixture), BASE.plusDays(32));
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

        approveExtensionAt(retentionId, BASE.plusDays(80), caseRef(fixture), BASE.plusDays(10));

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

        approveExtensionAt(rowFor(fixture).getId(), BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10));

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

        approveExtensionAt(retentionId, BASE.plusDays(60), " " + caseRef(fixture) + " ", BASE.plusDays(10));
        assertThat(rowFor(fixture).getExtensionCaseRef()).isEqualTo(caseRef(fixture));

        assertThatThrownBy(() -> approveExtensionAt(
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

        assertThatThrownBy(() -> approveExtensionAt(
                retentionId, BASE.plusDays(91), caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(fixture).getExtensionUntil()).isNull();
    }

    @Test
    void extensionWithoutAnOpenCaseOrCaseReferenceIsRejected() {
        Fixture closed = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        Long closedId = rowFor(closed).getId();

        assertThatThrownBy(() -> approveExtensionAt(
                closedId, BASE.plusDays(60), caseRef(closed), BASE.plusDays(10)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approveExtensionAt(
                closedId, BASE.plusDays(60), " ", BASE.plusDays(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(closed).getExtensionUntil()).isNull();
    }

    @Test
    void extensionCaseReferenceMustBeAnOpenReportOfThisPost() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> approveExtensionAt(
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
        assertThatThrownBy(() -> approveExtensionAt(
                retentionId, BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(AccessDeniedException.class);

        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> approveExtensionAt(
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

        assertThatThrownBy(() -> approveExtensionAt(
                rowFor(fixture).getId(), BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10)))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void legalHoldLapsesAndTheClosedCaseEvidenceIsDeletedUnderTheNormalRules() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> placeLegalHoldAt(retentionId, " ", BASE.plusDays(60), BASE.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중", BASE.plusDays(60), BASE.plusDays(1));
        assertThat(rowFor(fixture).getLegalHoldApprovedBy()).isEqualTo(ADMIN_ACCOUNT);

        service.expireDueRows(BASE.plusDays(31));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);

        // The hold lapsed on its review date. The case is closed, so the evidence is deleted automatically.
        service.expireDueRows(BASE.plusDays(61));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
        assertThat(service.deleteDueObjects(BASE.plusDays(61))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
        verify(storageStrategy).deleteChecked("cheer-test", fixture.storagePath());
    }

    @Test
    void lapsedLegalHoldOnAnOpenCaseGoesThroughTheUrgentReviewGrace() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        placeLegalHoldAt(rowFor(fixture).getId(), "법령 보존 의무 검토 중", BASE.plusDays(60), BASE.plusDays(1));

        service.expireDueRows(BASE.plusDays(59));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);

        // The hold lapsed, the case is still open past its window: one urgent review grace, never a straight delete.
        service.expireDueRows(BASE.plusDays(61));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void legalHoldReviewDueMoreThanOneYearAheadIsRejectedAndNonAdministratorIsRejected() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> placeLegalHoldAt(
                retentionId, "법령 보존 의무 검토 중", BASE.plusDays(1).plusDays(366), BASE.plusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);

        signIn("8", "ROLE_USER");
        assertThatThrownBy(() -> placeLegalHoldAt(
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
        // Past the cap there is no room for a minimum grace, so the case goes to operator review.
        assertThat(row.getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
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
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
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
    void wildcardCharactersInKeyDoNotMatchDifferentImagePath() {
        String suffix = UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true,
                "posts/shared/a%_!" + suffix);
        reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, false,
                "posts/shared/aXY!" + suffix);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));

        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isEqualTo(1);
        assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
        verify(storageStrategy, times(1)).deleteChecked(anyString(), anyString());
    }

    @Test
    void unicodePaddedSeatViewWithBackslashKeyKeepsTheObject() {
        String sharedPath = "posts/shared/a\\b-" + UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);
        String seatPath = "\u2003cheer-test/" + sharedPath + "\u2003";
        jdbcTemplate.update("INSERT INTO seat_view_photo (user_id, storage_path, source_type, user_selected, reward_granted, createdat, updatedat) VALUES (?, ?, 'SEATMAP_UPLOAD', ?, ?, ?, ?)",
                first.ownerId(), seatPath, false, false, BASE, BASE);
        try {
            service.discover(BASE);
            service.expireDueRows(BASE.plusDays(1));
            assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
            assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
            verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
        } finally {
            jdbcTemplate.update("DELETE FROM seat_view_photo WHERE storage_path = ?", seatPath);
        }
    }

    @Test
    void paddedAliasInLegacyHoldKeepsTheObject() {
        String sharedPath = "posts/shared/" + UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);
        long heldImageId = Long.MAX_VALUE - first.postImageId();
        jdbcTemplate.update("INSERT INTO legacy_post_image_holds (post_image_id, owner_user_id, post_id, storage_path, logical_bucket, report_protected, status, created_at) VALUES (?, ?, ?, ?, ?, ?, 'UNRESOLVED', ?)",
                heldImageId, first.ownerId(), first.postId() + 1000, "  cheer-test/" + sharedPath + "  ",
                "cheer-test", true, BASE);
        try {
            service.discover(BASE);
            service.expireDueRows(BASE.plusDays(1));
            assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
            assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
            verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
        } finally {
            jdbcTemplate.update("DELETE FROM legacy_post_image_holds WHERE post_image_id = ?", heldImageId);
        }
    }

    @Test
    void paddedAliasOnUnrelatedMediaAssetKeepsTheObject() {
        String sharedPath = "posts/shared/" + UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);
        linkAsset(first.ownerId(), "  cheer-test/" + sharedPath + "  ", first.postId() + 1000);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));

        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void paddedAliasInOtherRetentionRowKeepsTheObjectAfterImageWasRemoved() {
        String sharedPath = "posts/shared/" + UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);
        Fixture other = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING, true, "  cheer-test/" + sharedPath + "  ");
        service.discover(BASE);
        jdbcTemplate.update("DELETE FROM post_images WHERE id = ?", other.postImageId());
        service.expireDueRows(BASE.plusDays(1));

        assertThat(service.deleteDueObjects(BASE.plusDays(1))).isZero();
        assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
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
    void sharedStorageObjectGetsOneRowPerImageAndStaysUnderReviewWhileAnotherImageStillPointsAtIt() {
        String sharedPath = "posts/shared/" + UUID.randomUUID() + ".webp";
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);
        reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, true, sharedPath);

        assertThat(service.discover(BASE)).isEqualTo(2);
        service.expireDueRows(BASE.plusDays(31));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(rowFor(first).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    /**
     * A live reference committed before the deletion check keeps the object. A reference that appears only while the
     * storage call is running cannot be stopped: the call is outside any row lock by design, and the service logs an
     * ALERT for it instead. That residual window is documented in ADR 0006.
     */
    @Test
    void referenceCommittedBeforeTheDeletionCheckKeepsTheObject() {
        Fixture first = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        Fixture other = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED, false, null);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        jdbcTemplate.update("INSERT INTO post_images (post_id, storage_path, mime_type, bytes, is_thumbnail, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                other.postId(), first.storagePath(), "image/webp", 1L, false, java.time.Instant.now());

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

        placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중", BASE.plusDays(60), BASE.plusDays(31).plusMinutes(1));

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

        // Reopened case past the window: the row goes through one urgent review grace before deletion.
        service.expireDueRows(BASE.plusDays(31));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.URGENT_REVIEW);
        service.expireUrgentRows(BASE.plusDays(32));
        assertThat(service.deleteDueObjects(BASE.plusDays(32))).isEqualTo(1);
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

    @Test
    void autoDeleteSwitchOffClassifiesButNeverRemovesAnObject() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);

        withField("autoDeleteEnabled", false, () -> service.runDaily(BASE.plusDays(31)));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void oneRunRemovesNoMoreObjectsThanTheConfiguredCap() {
        reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));

        withField("maxDeletesPerRun", 2, () ->
                assertThat(service.deleteDueObjects(BASE.plusDays(31))).isEqualTo(2));

        assertThat(retentionRepository.findIdsByStatus(ReportEvidenceMediaStatus.DELETE_PENDING)).hasSize(1);
        verify(storageStrategy, times(2)).deleteChecked(anyString(), anyString());
    }

    @Test
    void legalHoldIsRefusedWhileTheObjectIsBeingDeleted() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        AtomicReference<Throwable> refusal = new AtomicReference<>();
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            // The claim is committed before the storage call, so a hold placed now must be refused.
            try {
                placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중", BASE.plusDays(60), BASE.plusDays(31));
            } catch (Throwable ex) {
                refusal.set(ex);
            }
            return Mono.empty();
        });

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isEqualTo(1);
        assertThat(refusal.get()).isInstanceOf(IllegalStateException.class);
    }

    /**
     * A timed-out storage call may still be executing after its retry lease expires.
     */
    @Test
    void ambientTransactionCannotStartStorageDeletionBeforeClaimCommits() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            try {
                service.deleteDueObjects(BASE.plusDays(31));
            } finally {
                status.setRollbackOnly();
            }
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("transaction");
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
        assertThat(retentionRepository.findById(retentionId).orElseThrow().getDeleteStartedAt()).isNull();
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
    }

    @Test
    void ambientTransactionRejectsBothOrchestratorsEvenWhenAutoDeleteIsDisabled() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        withField("autoDeleteEnabled", false, () -> {
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    service.runDaily(BASE.plusDays(31))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("transaction");
            assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    service.deleteDueObjects(BASE.plusDays(31))))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("transaction");
        });
        assertThat(retentionRepository.findAll()).isEmpty();
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void storageCallSeesCommittedClaimInIndependentTransaction() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        AtomicReference<String> observedToken = new AtomicReference<>();
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var row = retentionRepository.findByIdForUpdate(retentionId).orElseThrow();
                assertThat(row.getDeleteStartedAt()).isNotNull();
                assertThat(row.getDeleteClaimToken()).isNotBlank();
                observedToken.set(row.getDeleteClaimToken());
            });
            return Mono.empty();
        });
        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isEqualTo(1);
        assertThat(observedToken.get()).isNotBlank();
        assertThat(retentionRepository.findById(retentionId).orElseThrow().getStatus())
                .isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void deletionClaimMovesMissingOwnerToReviewWithoutStorageCall() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            var row = retentionRepository.findByIdForUpdate(retentionId).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(row, "ownerUserId", Long.MAX_VALUE);
            retentionRepository.flush();
        });

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(retentionRepository.findById(retentionId).orElseThrow().getStatus())
                .isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        assertThat(eventRepository.findByRetentionIdOrderByIdAsc(retentionId))
                .extracting(ReportEvidenceMediaRetentionEvent::getEventType)
                .contains(ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void deletionClaimWaitsForOwnerBeforeLockingRetentionRow() throws Exception {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        assertThat(service.expireDueRows(BASE.plusDays(31))).isEqualTo(1);
        Long retentionId = rowFor(fixture).getId();
        var ownerLocked = new java.util.concurrent.CountDownLatch(1);
        var requestRetention = new java.util.concurrent.CountDownLatch(1);
        var sessionId = new java.util.concurrent.atomic.AtomicLong();
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var finalization = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                sessionId.set(jdbcTemplate.queryForObject("SELECT SESSION_ID()", Long.class));
                userRepository.findByIdForWrite(fixture.ownerId()).orElseThrow();
                ownerLocked.countDown();
                try {
                    if (!requestRetention.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                retentionRepository.findByIdForUpdate(retentionId).orElseThrow();
                return null;
            }));
            assertThat(ownerLocked.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var cleanup = executor.submit(() -> service.deleteDueObjects(BASE.plusDays(31)));
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
            while (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = ?", Integer.class, sessionId.get()) == 0
                    && System.nanoTime() < deadline) Thread.sleep(10);
            assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID = ?", Integer.class, sessionId.get())).isPositive();
            requestRetention.countDown();
            finalization.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var result = cleanup.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(result).as("retention status %s", rowFor(fixture).getStatus()).isEqualTo(1);
        } finally {
            requestRetention.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void committedNewClaimFencesEarlierWorkerCompletion() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        LocalDateTime newerClaim = BASE.plusDays(31).plusHours(2);
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    retentionRepository.findByIdForUpdate(retentionId).orElseThrow().claimForDelete(newerClaim));
            return Mono.empty();
        });

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        var row = retentionRepository.findById(retentionId).orElseThrow();
        assertThat(row.getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
        assertThat(row.getDeleteClaimedAt()).isEqualTo(newerClaim);
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
    }

    @Test
    void committedNewClaimFencesEarlierWorkerFailure() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        LocalDateTime newerClaim = BASE.plusDays(31).plusHours(2);
        AtomicReference<String> token = new AtomicReference<>();
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var row = retentionRepository.findByIdForUpdate(retentionId).orElseThrow();
                row.claimForDelete(newerClaim);
                token.set(row.getDeleteClaimToken());
            });
            return Mono.error(new RuntimeException("synthetic late failure"));
        });

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        var row = retentionRepository.findById(retentionId).orElseThrow();
        assertThat(token.get()).isNotBlank();
        assertThat(row.getDeleteClaimToken()).isEqualTo(token.get());
        assertThat(row.getDeleteClaimedAt()).isEqualTo(newerClaim);
        assertThat(row.getAttemptCount()).isZero();
        assertThat(row.getNextAttemptAt()).isNull();
    }

    @Test
    void staleSuccessfulDeletionCannotCompleteNewerClaim() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        LocalDateTime newerClaim = BASE.plusDays(31).plusHours(2);
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    retentionRepository.findByIdForUpdate(rowFor(fixture).getId()).orElseThrow().claimForDelete(newerClaim));
            return Mono.empty();
        });

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
        assertThat(rowFor(fixture).getDeleteClaimedAt()).isEqualTo(newerClaim);
        assertThat(postImageRepository.findById(fixture.postImageId())).isPresent();
    }

    @Test
    void staleFailureCannotReleaseOrChangeNewerClaim() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        LocalDateTime newerClaim = BASE.plusDays(31).plusHours(2);
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenAnswer(invocation -> {
            new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    retentionRepository.findByIdForUpdate(rowFor(fixture).getId()).orElseThrow().claimForDelete(newerClaim));
            return Mono.error(new RuntimeException("synthetic late failure"));
        });

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(rowFor(fixture).getDeleteClaimedAt()).isEqualTo(newerClaim);
        assertThat(rowFor(fixture).getAttemptCount()).isZero();
        assertThat(rowFor(fixture).getNextAttemptAt()).isNull();
    }

    @Test
    void timedOutDeletionRefusesHoldEvenAfterTheLeaseExpires() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new TimeoutException("synthetic storage timeout")));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();

        assertThatThrownBy(() -> placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중",
                BASE.plusDays(60), BASE.plusDays(31).plusMinutes(20)))
                .isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중",
                BASE.plusDays(60), BASE.plusDays(31).plusHours(2)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> approveExtensionAt(retentionId, BASE.plusDays(40), caseRef(fixture),
                BASE.plusDays(31).plusHours(2)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("삭제가 시작");
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
    }

    @Test
    void failedDeletionRefusesHoldWhileRetryRemainsAvailable() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        Long retentionId = rowFor(fixture).getId();
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("synthetic failure")));
        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThatThrownBy(() -> placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중",
                BASE.plusDays(60), BASE.plusDays(31).plusMinutes(1)))
                .isInstanceOf(IllegalStateException.class);
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenReturn(Mono.empty());
        assertThat(service.deleteDueObjects(BASE.plusDays(31).plusMinutes(10))).isEqualTo(1);
    }

    @Test
    void timedOutStorageCallKeepsTheClaimUntilTheLeaseEnds() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(31));
        when(storageStrategy.deleteChecked(anyString(), anyString()))
                .thenReturn(Mono.error(new TimeoutException("synthetic storage timeout")));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();
        assertThat(rowFor(fixture).getDeleteClaimedAt()).isNotNull();

        // The retry time has passed, but the call may still be running, so the claim blocks a second attempt.
        when(storageStrategy.deleteChecked(anyString(), anyString())).thenReturn(Mono.empty());
        assertThat(service.deleteDueObjects(BASE.plusDays(31).plusMinutes(20))).isZero();

        assertThat(service.deleteDueObjects(BASE.plusDays(31).plusHours(2))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
    }

    @Test
    void dueRowIsDeferredWhileAnotherRetentionRowStillProtectsTheObject() {
        Fixture due = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        Fixture open = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        // A second row on another spelling of the due object, kept alive by an open case of its own.
        retentionRepository.saveAndFlush(ReportEvidenceMediaRetention.activeWithBase(
                open.postImageId(), open.postId(), open.ownerId(), "cheer-test/" + due.storagePath(),
                BASE, BASE, BASE));
        service.expireDueRows(BASE.plusDays(31));

        assertThat(service.deleteDueObjects(BASE.plusDays(31))).isZero();

        ReportEvidenceMediaRetention deferred = rowFor(due);
        assertThat(deferred.getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETE_PENDING);
        assertThat(deferred.getNextAttemptAt()).isEqualTo(BASE.plusDays(32));
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void legalHoldsEndingWithinFourteenDaysAreAlertedBeforeTheyLapse() {
        Fixture soon = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        Fixture later = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        placeLegalHoldAt(rowFor(soon).getId(), "법령 보존 의무 검토 중", BASE.plusDays(10), BASE);
        placeLegalHoldAt(rowFor(later).getId(), "법령 보존 의무 검토 중", BASE.plusDays(20), BASE);

        assertThat(service.alertLegalHoldsLapsingSoon(BASE)).isEqualTo(1);
    }

    @Test
    void aLapsedHoldIsDeletedWithItsReasonAndEveryDecisionStaysInTheTrail() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.RESOLVED);
        service.discover(BASE);
        service.expireDueRows(BASE.plusDays(1));
        Long retentionId = rowFor(fixture).getId();
        placeLegalHoldAt(retentionId, "법령 보존 의무 검토 중", BASE.plusDays(40), BASE.plusDays(2));
        placeLegalHoldAt(retentionId, "두 번째 검토 근거", BASE.plusDays(45), BASE.plusDays(3));

        service.expireDueRows(BASE.plusDays(46));
        assertThat(service.deleteDueObjects(BASE.plusDays(46))).isEqualTo(1);

        List<ReportEvidenceMediaRetentionEvent> trail = eventRepository.findByRetentionIdOrderByIdAsc(retentionId);
        List<ReportEvidenceMediaRetentionEvent> holds = trail.stream()
                .filter(event -> event.getEventType() == ReportEvidenceMediaRetentionEvent.EventType.LEGAL_HOLD_PLACED)
                .toList();
        assertThat(holds).hasSize(2);
        assertThat(holds.get(1).getPreviousBasis()).isEqualTo("법령 보존 의무 검토 중");
        assertThat(holds.get(1).getPreviousValue()).isEqualTo(BASE.plusDays(40).toString());
        assertThat(holds.get(1).getPreviousActor()).isEqualTo(ADMIN_ACCOUNT);
        assertThat(holds.get(1).getBasisText()).isEqualTo("두 번째 검토 근거");
        assertThat(trail).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(ReportEvidenceMediaRetentionEvent.EventType.DELETE_PENDING);
            assertThat(event.getReason()).isEqualTo(ReportEvidenceDeleteReason.CASE_CLOSED);
        });
        ReportEvidenceMediaRetentionEvent deleted = trail.get(trail.size() - 1);
        assertThat(deleted.getEventType()).isEqualTo(ReportEvidenceMediaRetentionEvent.EventType.DELETED);
        assertThat(deleted.getReason()).isEqualTo(ReportEvidenceDeleteReason.HOLD_LAPSED);
    }

    @Test
    void everyExtensionIsKeptWithThePreviousApproval() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        approveExtensionAt(retentionId, BASE.plusDays(60), caseRef(fixture), BASE.plusDays(10));
        approveExtensionAt(retentionId, BASE.plusDays(70), caseRef(fixture), BASE.plusDays(11));

        List<ReportEvidenceMediaRetentionEvent> extensions = eventRepository.findByRetentionIdOrderByIdAsc(retentionId)
                .stream()
                .filter(event -> event.getEventType() == ReportEvidenceMediaRetentionEvent.EventType.EXTENSION_APPROVED)
                .toList();
        assertThat(extensions).hasSize(2);
        assertThat(extensions.get(1).getPreviousValue()).isEqualTo(BASE.plusDays(60).toString());
        assertThat(extensions.get(1).getNewValue()).isEqualTo(BASE.plusDays(70).toString());
        assertThat(extensions.get(1).getPreviousActor()).isEqualTo(ADMIN_ACCOUNT);
    }

    private void approveExtensionAt(Long retentionId, LocalDateTime until, String caseRef, LocalDateTime now) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.approveExtensionAt(retentionId, until, caseRef, now));
    }

    private void placeLegalHoldAt(Long retentionId, String basis, LocalDateTime reviewDueAt, LocalDateTime now) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.placeLegalHoldAt(retentionId, basis, reviewDueAt, now));
    }

    /**
     * An open case whose window ends at the 90-day cap leaves no room for the minimum grace before the cap. It goes to
     * operator review instead of being deleted on the spot.
     */
    @Test
    void openCaseWithoutRoomForTheMinimumGraceGoesToOperatorReviewNotDeletion() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();
        approveExtensionAt(retentionId, BASE.plusDays(90), caseRef(fixture), BASE.plusDays(10));

        service.expireDueRows(BASE.plusDays(90));
        assertThat(service.deleteDueObjects(BASE.plusDays(90))).isZero();

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        assertThat(eventRepository.findByRetentionIdOrderByIdAsc(retentionId)).anySatisfy(event -> {
            assertThat(event.getEventType()).isEqualTo(ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED);
            assertThat(event.getReason()).isEqualTo(ReportEvidenceDeleteReason.OVER_CAP_NO_GRACE);
        });
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void operatorReleaseDeletesAnOverCapCaseWhileThatCaseIsTheOnlyOpenOne() {
        Fixture fixture = overCapOpenCaseInReview();
        Long retentionId = rowFor(fixture).getId();

        releaseAt(retentionId, caseRef(fixture), "운영자 검토 완료", BASE.plusDays(91));

        assertThat(service.deleteDueObjects(BASE.plusDays(91))).isEqualTo(1);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.DELETED);
        verify(storageStrategy).deleteChecked(anyString(), anyString());
    }

    @Test
    void anAppealOnTheReleasedCaseRevokesTheReleaseAndKeepsTheObject() {
        Fixture fixture = overCapOpenCaseInReview();
        releaseAt(rowFor(fixture).getId(), caseRef(fixture), "운영자 검토 완료", BASE.plusDays(91));
        jdbcTemplate.update("UPDATE cheer_post_reports SET appeal_status = 'REQUESTED' WHERE id = ?", fixture.reportId());

        assertThat(service.deleteDueObjects(BASE.plusDays(91))).isZero();

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        verify(storageStrategy, never()).deleteChecked(anyString(), anyString());
    }

    @Test
    void releaseNeedsAReviewRowAnOpenCaseOfThisPostAndAReason() {
        Fixture fixture = overCapOpenCaseInReview();
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> releaseAt(retentionId, "999999", "운영자 검토 완료", BASE.plusDays(91)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> releaseAt(retentionId, caseRef(fixture), "  ", BASE.plusDays(91)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
    }

    @Test
    void releaseIsRefusedForARowThatIsNotInReview() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        Long retentionId = rowFor(fixture).getId();

        assertThatThrownBy(() -> releaseAt(retentionId, caseRef(fixture), "운영자 검토 완료", BASE.plusDays(1)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
    }

    @Test
    void releaseByNonAdministratorIsRejectedAndLeavesTheRowInReview() {
        Fixture fixture = overCapOpenCaseInReview();
        Long retentionId = rowFor(fixture).getId();
        signIn("9", "ROLE_USER");

        assertThatThrownBy(() -> releaseAt(retentionId, caseRef(fixture), "운영자 검토 완료", BASE.plusDays(91)))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
    }

    @Test
    void legalHoldLiftsAReviewRowBackIntoTheWindow() {
        Fixture fixture = overCapOpenCaseInReview();

        placeLegalHoldAt(rowFor(fixture).getId(), "법령 보존 의무 검토 중", BASE.plusDays(95), BASE.plusDays(91));

        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        assertThat(rowFor(fixture).getLegalBasis()).isEqualTo("법령 보존 의무 검토 중");
    }

    /** An open case whose window ends at the 90-day cap, already moved to operator review. */
    private Fixture overCapOpenCaseInReview() {
        Fixture fixture = reportedImage(BASE, CheerPostReport.ReportStatus.PENDING);
        service.discover(BASE);
        approveExtensionAt(rowFor(fixture).getId(), BASE.plusDays(90), caseRef(fixture), BASE.plusDays(10));
        service.expireDueRows(BASE.plusDays(90));
        assertThat(rowFor(fixture).getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        return fixture;
    }

    private void releaseAt(Long retentionId, String caseRef, String note, LocalDateTime now) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                service.releaseForDeletionAt(retentionId, caseRef, note, now));
    }

    /** Sets a private setting on the real service bean (not the proxy) and restores it afterwards. */
    private void withField(String name, Object value, Runnable action) {
        Object target = AopTestUtils.getTargetObject(service);
        Object previous = ReflectionTestUtils.getField(target, name);
        ReflectionTestUtils.setField(target, name, value);
        try {
            action.run();
        } finally {
            ReflectionTestUtils.setField(target, name, previous);
        }
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
