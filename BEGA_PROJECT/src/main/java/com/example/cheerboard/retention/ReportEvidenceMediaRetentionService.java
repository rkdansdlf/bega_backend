package com.example.cheerboard.retention;

import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.strategy.StorageObjectKey;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.Exceptions;
import reactor.core.scheduler.Schedulers;

/**
 * 신고된 게시글 이미지의 보관 기한을 관리한다. 계정 삭제 가드(purge 보류)와 독립적으로 동작하며,
 * 가드 쪽 상태를 읽거나 바꾸지 않는다.
 *
 * <p>자동 삭제는 설정으로 켜야 동작한다({@code report-evidence.media-retention.auto-delete-enabled}, 기본 꺼짐).
 * 꺼져 있으면 판정과 알림은 계속하지만 객체는 지우지 않는다. 한 번의 실행이 지우는 객체 수에도 상한이 있다.
 *
 * <p>객체 삭제는 세 단계다. 짧은 트랜잭션에서 행을 점유하고 검사한 뒤, DB 행 잠금 없이 저장소를 호출하고,
 * 다시 짧은 트랜잭션에서 결과를 기록한다. 저장소 호출이 느려도 DB 잠금이 길게 잡히지 않는다.
 *
 * <p>로그에는 저장소 경로, 사용자 식별자, 원본 예외 메시지를 남기지 않는다. 결정과 상태 변화는 이력 테이블에 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportEvidenceMediaRetentionService {

    private static final Duration STORAGE_CALL_TIMEOUT = Duration.ofSeconds(30);
    private static final String OWNER_GRACE = "GRACE";
    private static final String OWNER_FINALIZED = "FINALIZED";
    private static final String OWNER_RESTORED = "NOT_PENDING";
    private static final Set<String> ADMIN_AUTHORITIES = Set.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    private static final List<ReportEvidenceMediaStatus> CLOSED_STATUSES =
            List.of(ReportEvidenceMediaStatus.DELETED, ReportEvidenceMediaStatus.RESOLVED_RESTORED);
    private static final String SYSTEM_ACTOR = "SYSTEM";
    private static final String DELETE_IN_PROGRESS =
            "삭제가 시작되어 보존을 보장할 수 없습니다. 관리자 검토가 필요합니다.";
    private static final int MAX_CASE_REF_LENGTH = 64;
    private static final int MAX_BASIS_LENGTH = 512;
    private static final int MAX_AUDIT_DESCRIPTION_LENGTH = 500;
    private static final int MAX_AUDIT_VALUE_LENGTH = 100;

    private final ReportEvidenceMediaRetentionRepository retentionRepository;
    private final ReportEvidenceMediaRetentionEventRepository eventRepository;
    private final PostImageRepository postImageRepository;
    private final SeatViewPhotoRepository seatViewPhotos;
    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final StorageStrategy storageStrategy;
    private final StorageConfig storageConfig;
    private final PlatformTransactionManager transactionManager;
    private java.util.function.LongSupplier monotonicNanos = System::nanoTime;
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    /** 자동 삭제 스위치. 기본값은 꺼짐이며, 설정이 없으면 꺼진 상태로 남는다. 켜는 것은 배포 설정으로만 한다. */
    @Value("${report-evidence.media-retention.auto-delete-enabled:false}")
    private boolean autoDeleteEnabled = false;

    /** 한 번의 일일 작업이 저장소 삭제를 시도할 수 있는 객체 수의 상한. 0 이하면 삭제하지 않는다. */
    @Value("${report-evidence.media-retention.max-deletes-per-run:50}")
    private int maxDeletesPerRun = 50;

    /** 매일 실행되는 전체 흐름. 단계마다 독립적으로 실행하므로 한 단계가 실패해도 나머지 단계는 계속 돈다. */
    public void runDaily(LocalDateTime now) {
        requireNoAmbientTransaction();
        long startedNanos = monotonicNanos.getAsLong();
        runPhase("discover", () -> discover(now));
        runPhase("expire", () -> expireDueRows(now));
        runPhase("urgent", () -> expireUrgentRows(now));
        runPhase("delete", () -> deleteDueObjectsFrom(now, startedNanos));
        runPhase("review-alert", () -> alertStaleReviewRows(elapsedTime(now, startedNanos)));
        runPhase("hold-alert", () -> alertLegalHoldsLapsingSoon(elapsedTime(now, startedNanos)));
    }

    /**
     * 검토 대상으로 등록된 뒤 기본 보관 기한이 지나도록 처리되지 않은 기록을 센다. 삭제나 판정은 하지 않고,
     * 운영자가 처리해야 한다는 신호로만 로그를 남긴다.
     */
    public long alertStaleReviewRows(LocalDateTime now) {
        long stale = retentionRepository.countByStatusAndUpdatedAtBefore(
                ReportEvidenceMediaStatus.REVIEW_REQUIRED, now.minus(ReportEvidenceRetentionPolicy.DEFAULT_RETENTION));
        if (stale > 0) {
            log.error("ALERT 검토 대기 신고 증거 미디어가 기본 보관 기한을 넘겼습니다: count={}", stale);
        }
        return stale;
    }

    /**
     * 법적 보존 검토 기한이 곧 끝나는 기록을 알린다. 기한이 끝나면 보존 근거가 사라지고 자동 삭제 대상이 되므로,
     * 운영자가 연장이나 재보존을 결정할 시간을 준다.
     */
    public long alertLegalHoldsLapsingSoon(LocalDateTime now) {
        long lapsing = retentionRepository.countLegalHoldsLapsingBetween(
                now, now.plus(ReportEvidenceRetentionPolicy.LEGAL_HOLD_ALERT_WINDOW), CLOSED_STATUSES);
        if (lapsing > 0) {
            log.error("ALERT 법적 보존 검토 기한이 14일 안에 끝나 자동 삭제 대상이 됩니다: count={}", lapsing);
        }
        return lapsing;
    }

    /**
     * 신고 이미지 중 보관 기록이 없는 것을 등록한다. 계정 삭제 요청이 대기 중인 소유자의 이미지만 등록한다.
     * 행은 게시글 이미지마다 하나다. 같은 저장소 객체를 여러 게시글이 가리켜도 각자의 기준일로 등록한다. 기준일과 주기
     * 시작일은 등록 시점에 행에 저장하고, 이후에는 바꾸지 않는다. 한 행의 데이터가 잘못되어도 나머지 행의 등록은 계속한다.
     */
    public int discover(LocalDateTime now) {
        int created = 0;
        for (Object[] row : retentionRepository.findUnregisteredReportedImagesWithBase()) {
            try {
                if (registerWithBase(row, now)) {
                    created++;
                }
            } catch (RuntimeException ex) {
                log.error("신고 증거 미디어 보관 등록 행 처리 실패: error={}", ex.getClass().getSimpleName());
            }
        }
        for (Object[] row : retentionRepository.findUnregisteredReportedImagesWithoutBase()) {
            try {
                if (registerWithoutBase(row, now)) {
                    created++;
                }
            } catch (RuntimeException ex) {
                log.error("신고 증거 미디어 검토 등록 행 처리 실패: error={}", ex.getClass().getSimpleName());
            }
        }
        return created;
    }

    /**
     * 활성 기록을 판정한다.
     * <ul>
     *   <li>소유자가 복구되었으면 보관 판단을 끝내고 RESOLVED_RESTORED로 닫는다. 기록은 감사를 위해 남긴다.</li>
     *   <li>법적 보존 근거가 있으면 검토 기한 전까지 유지한다. 기한이 지나면 보존 근거가 사라지고 일반 규칙을 따른다.</li>
     *   <li>사건이 종료되었으면 기한 전이라도 삭제 대기로 바꾼다.</li>
     *   <li>사건이 열려 있고 기본 보관 기한(연장 포함, 최대 90일)이 지났으면 자동 삭제하지 않고 긴급 검토로 보낸다.</li>
     * </ul>
     */
    public int expireDueRows(LocalDateTime now) {
        int changed = 0;
        for (Long id : retentionRepository.findIdsByStatus(ReportEvidenceMediaStatus.ACTIVE)) {
            try {
                if (Boolean.TRUE.equals(transaction().execute(status -> judgeActive(findRow(id), now)))) {
                    changed++;
                }
            } catch (RuntimeException ex) {
                log.error("신고 증거 미디어 만료 판정 실패: retentionId={}, error={}",
                        id, ex.getClass().getSimpleName());
            }
        }
        return changed;
    }

    /**
     * 긴급 검토 중인 기록을 처리한다. 사건이 닫히거나 유예가 끝나도록 결정이 없으면 삭제 대기로 바꾼다.
     * 유예는 행마다 한 번만 주므로 이 단계는 보관을 무기한으로 늘리지 않는다.
     */
    public int expireUrgentRows(LocalDateTime now) {
        int changed = 0;
        for (Long id : retentionRepository.findIdsByStatus(ReportEvidenceMediaStatus.URGENT_REVIEW)) {
            try {
                if (Boolean.TRUE.equals(transaction().execute(status -> judgeUrgent(findRowForUpdate(id), now)))) {
                    changed++;
                }
            } catch (RuntimeException ex) {
                log.error("신고 증거 미디어 긴급 검토 판정 실패: retentionId={}, error={}",
                        id, ex.getClass().getSimpleName());
            }
        }
        return changed;
    }

    /**
     * 삭제 대기 객체를 지운다. 자동 삭제가 꺼져 있으면 아무것도 지우지 않는다. 켜져 있으면 상한까지 한 객체씩
     * 점유, 저장소 호출, 결과 기록을 나눠서 처리한다. 저장소 호출 중에는 DB 행 잠금을 잡지 않는다.
     */
    public int deleteDueObjects(LocalDateTime now) {
        requireNoAmbientTransaction();
        return deleteDueObjectsFrom(now, monotonicNanos.getAsLong());
    }

    private int deleteDueObjectsFrom(LocalDateTime now, long startedNanos) {
        requireNoAmbientTransaction();
        if (!autoDeleteEnabled) {
            log.info("신고 증거 자동 삭제가 꺼져 있어 객체를 지우지 않습니다: pending={}",
                    retentionRepository.findIdsByStatus(ReportEvidenceMediaStatus.DELETE_PENDING).size());
            return 0;
        }
        int limit = Math.max(0, maxDeletesPerRun);
        int claimed = 0;
        int deleted = 0;
        for (Long id : retentionRepository.findIdsByStatus(ReportEvidenceMediaStatus.DELETE_PENDING)) {
            if (claimed >= limit) {
                log.warn("신고 증거 삭제 상한에 도달해 나머지는 다음 실행으로 넘깁니다: limit={}", limit);
                break;
            }
            try {
                LocalDateTime claimTime = elapsedTime(now, startedNanos);
                StorageDeletion job = transaction().execute(status -> claimForDelete(id, claimTime));
                if (job == null) {
                    continue;
                }
                claimed++;
                if (removeObject(job, now, startedNanos)) {
                    deleted++;
                }
            } catch (RuntimeException ex) {
                log.error("신고 증거 미디어 삭제 처리 실패: retentionId={}, error={}",
                        id, ex.getClass().getSimpleName());
            }
        }
        return deleted;
    }

    /** 서버 시각으로 연장을 승인한다. 승인자는 현재 로그인한 관리자다. */
    @Transactional
    public void approveExtension(Long retentionId, LocalDateTime until, String caseRef) {
        approveExtensionAt(retentionId, until, caseRef, LocalDateTime.now());
    }

    /**
     * 연장을 승인한다. 활성 사건이 있어야 하고, 사건 번호는 이 게시글의 미결 신고여야 하며, 기한은 90일 상한을 넘을 수 없다.
     * 승인 대기 중인 행(긴급 검토, 삭제 대기)도 연장할 수 있다. 연장하면 활성 상태로 되돌린다.
     * 저장소 삭제가 시작된 행은 재시도 점유가 끝난 뒤에도 거부한다.
     */
    void approveExtensionAt(Long retentionId, LocalDateTime until, String caseRef, LocalDateTime now) {
        String approver = requireAdministrator();
        if (until == null) {
            throw new IllegalArgumentException("연장 기한이 필요합니다.");
        }
        String ref = requireText(caseRef, MAX_CASE_REF_LENGTH, "연장에는 사건 번호가 필요합니다.");
        ReportEvidenceMediaRetention row = findRowForUpdate(retentionId);
        if (row.hasDeletionStarted()) {
            throw new IllegalStateException(DELETE_IN_PROGRESS);
        }
        if (row.getStatus() != ReportEvidenceMediaStatus.ACTIVE
                && row.getStatus() != ReportEvidenceMediaStatus.URGENT_REVIEW
                && row.getStatus() != ReportEvidenceMediaStatus.DELETE_PENDING) {
            throw new IllegalStateException("이 상태의 미디어는 연장할 수 없습니다.");
        }
        if (!retentionRepository.hasOpenCase(row.getPostId())) {
            throw new IllegalStateException("활성 사건이 없으면 연장할 수 없습니다.");
        }
        if (!isOpenReportOfPost(ref, row.getPostId())) {
            throw new IllegalArgumentException("사건 번호가 이 게시글의 미결 신고와 일치하지 않습니다.");
        }
        if (row.getHardLimitAt() == null) {
            throw new IllegalStateException("최초 삭제 요청일이 없는 미디어는 연장 대상이 아닙니다.");
        }
        if (!until.isAfter(now)) {
            throw new IllegalArgumentException("연장 기한은 현재 시각 이후여야 합니다.");
        }
        if (until.isAfter(row.getHardLimitAt())) {
            throw new IllegalArgumentException("연장 기한은 최초 삭제 요청일 기준 90일을 넘을 수 없습니다.");
        }
        String previous = row.getExtensionUntil() == null ? null : row.getExtensionUntil().toString();
        String previousApprover = row.getExtensionApprovedBy();
        row.approveExtension(until, ref, approver, now);
        if (row.getStatus() != ReportEvidenceMediaStatus.ACTIVE) {
            row.reactivate(now);
        }
        audit(approver, row, AuditLog.AuditAction.EVIDENCE_EXTENSION_APPROVED, previous, until.toString());
        recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.EXTENSION_APPROVED, null, approver, ref,
                previous, until.toString(), null, previousApprover, null, now);
    }

    /** 서버 시각으로 법적 보존 근거를 기록한다. 승인자는 현재 로그인한 관리자다. */
    @Transactional
    public void placeLegalHold(Long retentionId, String basis, LocalDateTime reviewDueAt) {
        placeLegalHoldAt(retentionId, basis, reviewDueAt, LocalDateTime.now());
    }

    /**
     * 법적 보존 근거와 검토 기한을 기록한다. 검토 기한은 현재 시각부터 1년을 넘을 수 없다.
     * 삭제 대기나 긴급 검토 중인 행에 걸면 활성 상태로 되돌려 삭제를 막는다. 근거의 적법성은 이 코드가 판단하지 않는다.
     * 기존 근거와 승인자는 이력에 남기므로 재보존해도 첫 기록이 사라지지 않는다.
     */
    void placeLegalHoldAt(Long retentionId, String basis, LocalDateTime reviewDueAt, LocalDateTime now) {
        String approver = requireAdministrator();
        ReportEvidenceMediaRetention row = findRowForUpdate(retentionId);
        if (row.hasDeletionStarted()) {
            throw new IllegalStateException(DELETE_IN_PROGRESS);
        }
        if (row.getStatus() == ReportEvidenceMediaStatus.DELETED
                || row.getStatus() == ReportEvidenceMediaStatus.RESOLVED_RESTORED) {
            throw new IllegalStateException("이 상태의 미디어에는 보존 근거를 기록할 수 없습니다.");
        }
        String text = requireText(basis, MAX_BASIS_LENGTH, "법적 보존에는 근거가 필요합니다.");
        if (reviewDueAt == null || !reviewDueAt.isAfter(now)) {
            throw new IllegalArgumentException("법적 보존에는 미래의 검토 기한이 필요합니다.");
        }
        if (reviewDueAt.isAfter(now.plus(ReportEvidenceRetentionPolicy.MAX_LEGAL_REVIEW))) {
            throw new IllegalArgumentException("법적 보존 검토 기한은 현재 시각부터 1년을 넘을 수 없습니다.");
        }
        String previous = row.getLegalReviewDueAt() == null ? null : row.getLegalReviewDueAt().toString();
        String previousBasis = row.getLegalBasis();
        String previousApprover = row.getLegalHoldApprovedBy();
        row.placeLegalHold(text, reviewDueAt, approver, now);
        if (row.getStatus() == ReportEvidenceMediaStatus.DELETE_PENDING
                || row.getStatus() == ReportEvidenceMediaStatus.URGENT_REVIEW
                || row.getStatus() == ReportEvidenceMediaStatus.REVIEW_REQUIRED) {
            row.reactivate(now);
        }
        audit(approver, row, AuditLog.AuditAction.EVIDENCE_LEGAL_HOLD_PLACED, previous, reviewDueAt.toString());
        recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.LEGAL_HOLD_PLACED, null, approver, null,
                previous, reviewDueAt.toString(), text, previousApprover, previousBasis, now);
    }

    private boolean registerWithBase(Object[] row, LocalDateTime now) {
        String path = String.valueOf(row[3]);
        LocalDateTime firstRequestedAt = ReportEvidenceRetentionPolicy.toLocalDateTime(row[4]);
        LocalDateTime cycleStartedAt = ReportEvidenceRetentionPolicy.toLocalDateTime(row[5]);
        LocalDateTime earliestReportAt = ReportEvidenceRetentionPolicy.toLocalDateTime(row[6]);
        LocalDateTime base = ReportEvidenceRetentionPolicy.evidenceBase(
                firstRequestedAt, cycleStartedAt, earliestReportAt);
        return register(() -> ReportEvidenceMediaRetention.activeWithBase(
                toLong(row[0]), toLong(row[1]), toLong(row[2]), path, base, cycleStartedAt, now), now);
    }

    private boolean registerWithoutBase(Object[] row, LocalDateTime now) {
        String path = String.valueOf(row[3]);
        return register(() -> ReportEvidenceMediaRetention.reviewRequired(
                toLong(row[0]), toLong(row[1]), toLong(row[2]), path, now), now);
    }

    /** 서버 시각으로 검토 대기 기록의 삭제를 승인한다. 승인자는 현재 로그인한 관리자다. */
    @Transactional
    public void releaseForDeletion(Long retentionId, String caseRef, String note) {
        releaseForDeletionAt(retentionId, caseRef, note, LocalDateTime.now());
    }

    /**
     * 운영자가 검토 대기 기록을 삭제 대기로 넘긴다. 사건 번호는 이 게시글의 미결 신고여야 하고, 사유는 필수다.
     * 승인은 승인한 사건이 이 게시글의 유일한 미결 사건이고 이의제기가 없는 동안에만 유효하다. 사건이 바뀌면
     * 삭제 시점에 다시 검토로 돌아간다.
     */
    void releaseForDeletionAt(Long retentionId, String caseRef, String note, LocalDateTime now) {
        String approver = requireAdministrator();
        ReportEvidenceMediaRetention row = findRowForUpdate(retentionId);
        if (row.isDeleteClaimed(now)) {
            throw new IllegalStateException(DELETE_IN_PROGRESS);
        }
        if (row.getStatus() != ReportEvidenceMediaStatus.REVIEW_REQUIRED) {
            throw new IllegalStateException("검토 대기 상태의 미디어만 삭제 승인할 수 있습니다.");
        }
        String ref = requireText(caseRef, MAX_CASE_REF_LENGTH, "삭제 승인에는 사건 번호가 필요합니다.");
        if (!isOpenReportOfPost(ref, row.getPostId())) {
            throw new IllegalArgumentException("사건 번호가 이 게시글의 미결 신고와 일치하지 않습니다.");
        }
        String text = requireText(note, MAX_BASIS_LENGTH, "삭제 승인에는 사유가 필요합니다.");
        row.releaseForDeletion(ref, now);
        audit(approver, row, AuditLog.AuditAction.EVIDENCE_RELEASED_FOR_DELETION, null, ref);
        recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.RELEASED_FOR_DELETION,
                ReportEvidenceDeleteReason.OPERATOR_RELEASED, approver, ref, null, null, text, null, null, now);
    }

    /**
     * 운영자 삭제 승인이 아직 유효한지. 승인한 사건이 이 게시글의 유일한 미결 사건이고 이의제기가 없어야 한다.
     */
    private boolean releasedForCurrentCase(ReportEvidenceMediaRetention row) {
        if (row.getDeleteReason() != ReportEvidenceDeleteReason.OPERATOR_RELEASED || row.getReleaseCaseRef() == null) {
            return false;
        }
        Long appeals = retentionRepository.countOpenAppeals(row.getPostId());
        if (appeals != null && appeals > 0) {
            return false;
        }
        List<Long> open = retentionRepository.findOpenReportIds(row.getPostId());
        return open.size() == 1 && String.valueOf(open.get(0)).equals(row.getReleaseCaseRef());
    }

    private boolean judgeActive(ReportEvidenceMediaRetention row, LocalDateTime now) {
        if (row.getStatus() != ReportEvidenceMediaStatus.ACTIVE) return false;
        if (row.hasDeletionStarted()) {
            markUncertainDeletionForReview(row, now);
            return true;
        }
        String owner = ownerState(row);
        if (OWNER_GRACE.equals(owner)) {
            return false;
        }
        if (OWNER_RESTORED.equals(owner)) {
            row.markResolvedRestored(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.RESOLVED_RESTORED, null, SYSTEM_ACTOR, now);
            return true;
        }
        if (!OWNER_FINALIZED.equals(owner)) {
            row.markReviewRequired(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED, null, SYSTEM_ACTOR, now);
            return true;
        }
        if (holdActive(row, now)) {
            return false;
        }
        // A lapsed hold no longer protects the evidence: the normal rules decide, so a closed case is deleted.
        if (!retentionRepository.hasOpenCase(row.getPostId())) {
            markDeletePending(row, closedReason(row), now);
            return true;
        }
        LocalDateTime until = ReportEvidenceRetentionPolicy.effectiveUntil(
                row.getRetainUntil(), row.getHardLimitAt(), row.getExtensionUntil());
        if (now.isBefore(until)) {
            return false;
        }
        // The case is still open past its window. Never delete it on the spot: give one review grace, then decide.
        if (row.getHardLimitAt() != null
                && !ReportEvidenceRetentionPolicy.graceFitsBeforeCap(now, row.getHardLimitAt())) {
            // Less than the minimum grace is left before the cap. Deleting now would break the grace, so an operator decides.
            row.markReviewRequired(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED,
                    ReportEvidenceDeleteReason.OVER_CAP_NO_GRACE, SYSTEM_ACTOR, now);
            log.error("ALERT 열린 사건의 신고 증거가 상한 여유 기간 안에 들어와 운영자 검토로 보냅니다: retentionId={}, postId={}",
                    row.getId(), row.getPostId());
            return true;
        }
        if (row.markUrgentReview(now)) {
            log.error("ALERT 열린 사건의 신고 증거가 기본 보관 기한을 넘겨 긴급 검토 대상이 되었습니다: retentionId={}, postId={}",
                    row.getId(), row.getPostId());
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.URGENT_REVIEW_STARTED, null, SYSTEM_ACTOR, now);
            return true;
        }
        markDeletePending(row, ReportEvidenceDeleteReason.URGENT_GRACE_EXPIRED, now);
        return true;
    }

    private boolean judgeUrgent(ReportEvidenceMediaRetention row, LocalDateTime now) {
        if (row.getStatus() != ReportEvidenceMediaStatus.URGENT_REVIEW) return false;
        if (row.hasDeletionStarted()) {
            markUncertainDeletionForReview(row, now);
            return true;
        }
        // A review lasts at least one run: a row classified in this run is decided by a later run, never by the same one.
        if (row.getUrgentReviewStartedAt() != null && !now.isAfter(row.getUrgentReviewStartedAt())) {
            return false;
        }
        String owner = ownerState(row);
        if (OWNER_GRACE.equals(owner)) {
            return false;
        }
        if (OWNER_RESTORED.equals(owner)) {
            row.markResolvedRestored(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.RESOLVED_RESTORED, null, SYSTEM_ACTOR, now);
            return true;
        }
        if (!OWNER_FINALIZED.equals(owner)) {
            row.markReviewRequired(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED, null, SYSTEM_ACTOR, now);
            return true;
        }
        if (!retentionRepository.hasOpenCase(row.getPostId())) {
            markDeletePending(row, closedReason(row), now);
            return true;
        }
        // A missing due time counts as due: the grace must never leave a row stuck.
        if (row.getUrgentReviewDueAt() != null && now.isBefore(row.getUrgentReviewDueAt())) {
            return false;
        }
        // The grace ended with no decision. The row is never held indefinitely without an approved basis.
        log.error("ALERT 긴급 검토 유예가 결정 없이 끝나 삭제 대상으로 전환합니다: retentionId={}, postId={}",
                row.getId(), row.getPostId());
        markDeletePending(row, ReportEvidenceDeleteReason.URGENT_GRACE_EXPIRED, now);
        return true;
    }

    /**
     * 1단계(짧은 트랜잭션, 행 잠금). 삭제 직전 조건을 다시 확인하고 통과하면 점유를 찍는다.
     * 점유하지 못하면 null을 돌려준다. 결정에 따라 상태를 바꾸는 경우도 여기서 함께 처리한다.
     */
    private StorageDeletion claimForDelete(Long retentionId, LocalDateTime now) {
        Long ownerId = retentionRepository.findOwnerId(retentionId)
                .orElseThrow(() -> new IllegalArgumentException("보관 기록을 찾을 수 없습니다: " + retentionId));
        // Account finalization and appeal handling lock the owner before the retention row.
        var lockedOwner = userRepository.findByIdForWrite(ownerId);
        ReportEvidenceMediaRetention row = findRowForUpdate(retentionId);
        entityManager.refresh(row, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (!java.util.Objects.equals(ownerId, row.getOwnerUserId())) {
            throw new IllegalStateException("Retention owner changed during claim");
        }
        if (row.getStatus() != ReportEvidenceMediaStatus.DELETE_PENDING || row.isDeleteClaimed(now)) {
            return null;
        }
        if (row.getNextAttemptAt() != null && now.isBefore(row.getNextAttemptAt())) {
            return null;
        }
        if (lockedOwner.isEmpty()) {
            row.markReviewRequired(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED, null, SYSTEM_ACTOR, now);
            return null;
        }
        String owner = ownerState(row);
        if (OWNER_GRACE.equals(owner)) {
            // The owner can still recover, so nothing is deleted before finalization.
            if (row.hasDeletionStarted()) markUncertainDeletionForReview(row, now);
            return null;
        }
        if (OWNER_RESTORED.equals(owner)) {
            if (row.hasDeletionStarted()) {
                markUncertainDeletionForReview(row, now);
                return null;
            }
            row.markResolvedRestored(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.RESOLVED_RESTORED, null, SYSTEM_ACTOR, now);
            return null;
        }
        if (!OWNER_FINALIZED.equals(owner)) {
            row.markReviewRequired(now);
            recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED, null, SYSTEM_ACTOR, now);
            return null;
        }
        if (holdActive(row, now)) {
            if (row.hasDeletionStarted()) markUncertainDeletionForReview(row, now);
            else row.reactivate(now);
            return null;
        }
        if (retentionRepository.hasOpenCase(row.getPostId()) && !releasedForCurrentCase(row)
                && (!isPastRetention(row, now) || row.getUrgentReviewStartedAt() == null)) {
            // A case reopened (for example by an appeal) keeps the evidence. Past the window, a reopened case that has
            // not used its urgent review yet goes back through judgeActive, so it gets that one grace first.
            if (row.hasDeletionStarted()) markUncertainDeletionForReview(row, now);
            else row.reactivate(now);
            return null;
        }
        switch (referenceOf(row)) {
            case LIVE_REFERENCE -> {
                // A post image, media asset, or legacy hold still uses the object: an operator decides, as before.
                row.markReviewRequired(now);
                recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED, null, SYSTEM_ACTOR, now);
                return null;
            }
            case RETENTION_ROW -> {
                // Another retention row still protects the object and will resolve itself. Defer, not a failure.
                row.deferDelete(now);
                return null;
            }
            default -> {
                // No other holder: the claim below is safe.
            }
        }
        row.claimForDelete(now);
        return new StorageDeletion(row.getId(), row.getPostImageId(), storageConfig.getCheerBucket(),
                row.getStoragePath(), row.getDeleteClaimToken());
    }

    private void markUncertainDeletionForReview(ReportEvidenceMediaRetention row, LocalDateTime now) {
        row.markReviewRequired(now);
        recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED, null, SYSTEM_ACTOR, now);
        log.error("ALERT 삭제가 시작된 신고 증거에 보존 조건이 발생하여 검토가 필요합니다: retentionId={}", row.getId());
    }

    /**
     * 2단계(트랜잭션 밖). 저장소 호출만 한다. 호출은 호출 스레드 밖에서 돌리고 제한 시간을 건다.
     * 호출이 끝나서 실패하면 점유를 풀고 재시도를 예약한다. 제한 시간을 넘겼다면 호출이 아직 돌고 있을 수 있으므로
     * 점유를 유효 시간이 지날 때까지 둔다.
     */
    private boolean removeObject(StorageDeletion job, LocalDateTime baseTime, long startedNanos) {
        try {
            storageStrategy.deleteChecked(job.bucket(), job.storagePath())
                    .subscribeOn(Schedulers.boundedElastic())
                    .timeout(STORAGE_CALL_TIMEOUT)
                    .block();
        } catch (RuntimeException ex) {
            boolean timedOut = Exceptions.unwrap(ex) instanceof TimeoutException;
            LocalDateTime failureTime = elapsedTime(baseTime, startedNanos);
            transaction().executeWithoutResult(status -> recordDeleteFailure(job, ex, timedOut, failureTime));
            return false;
        }
        LocalDateTime completedAt = elapsedTime(baseTime, startedNanos);
        return Boolean.TRUE.equals(transaction().execute(status -> completeDelete(job, completedAt)));
    }

    private LocalDateTime elapsedTime(LocalDateTime baseTime, long startedNanos) {
        long elapsed = monotonicNanos.getAsLong() - startedNanos;
        return baseTime.plusNanos(Math.max(0L, elapsed));
    }

    /** 3단계(짧은 트랜잭션, 행 잠금). 저장소 호출이 성공했으므로 게시글 이미지 행을 지우고 삭제를 기록한다. */
    private boolean completeDelete(StorageDeletion job, LocalDateTime now) {
        ReportEvidenceMediaRetention row = findRowForUpdate(job.retentionId());
        if (job.claimToken() == null || job.claimToken().isBlank() || row.getStatus() != ReportEvidenceMediaStatus.DELETE_PENDING
                || !java.util.Objects.equals(row.getDeleteClaimToken(), job.claimToken())) {
            return false;
        }
        postImageRepository.findById(job.postImageId()).ifPresent(postImageRepository::delete);
        if (referenceOf(row) == Reference.LIVE_REFERENCE) {
            // A reference committed during the storage call cannot be stopped. Make it visible to an operator.
            log.error("ALERT 삭제한 객체를 다른 사용처가 가리키고 있습니다. 삭제 호출 중에 생긴 참조일 수 있습니다: retentionId={}",
                    row.getId());
        }
        row.markDeleted(now);
        recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.DELETED, row.getDeleteReason(), SYSTEM_ACTOR,
                now);
        return true;
    }

    private void recordDeleteFailure(StorageDeletion job, RuntimeException ex, boolean timedOut, LocalDateTime now) {
        ReportEvidenceMediaRetention row = findRowForUpdate(job.retentionId());
        if (job.claimToken() == null || job.claimToken().isBlank() || row.getStatus() != ReportEvidenceMediaStatus.DELETE_PENDING
                || !java.util.Objects.equals(row.getDeleteClaimToken(), job.claimToken())) {
            return;
        }
        row.recordDeleteFailure(ex.getClass().getSimpleName(), now);
        if (!timedOut) {
            row.releaseDeleteClaim(now);
        }
        if (row.getAttemptCount() >= ReportEvidenceRetentionPolicy.ALERT_ATTEMPT_THRESHOLD) {
            log.error("ALERT 신고 증거 미디어 삭제가 반복 실패합니다: retentionId={}, postId={}, attempts={}, error={}",
                    row.getId(), row.getPostId(), row.getAttemptCount(), ex.getClass().getSimpleName());
        } else {
            log.warn("신고 증거 미디어 삭제 실패, 재시도 예약: retentionId={}, attempts={}, error={}",
                    row.getId(), row.getAttemptCount(), ex.getClass().getSimpleName());
        }
    }

    /** A legal hold keeps the evidence until its review date. After that date the hold lapses (see the lapse rules). */
    private static boolean holdActive(ReportEvidenceMediaRetention row, LocalDateTime now) {
        return row.getLegalBasis() != null
                && row.getLegalReviewDueAt() != null
                && now.isBefore(row.getLegalReviewDueAt());
    }

    /** 보존 근거가 법적 보존 하나뿐이고 그 기한이 지났으면 보존 해제, 아니면 사건 종결로 본다. */
    private static ReportEvidenceDeleteReason closedReason(ReportEvidenceMediaRetention row) {
        return row.getLegalBasis() != null ? ReportEvidenceDeleteReason.HOLD_LAPSED : ReportEvidenceDeleteReason.CASE_CLOSED;
    }

    private void markDeletePending(ReportEvidenceMediaRetention row, ReportEvidenceDeleteReason reason,
                                   LocalDateTime now) {
        row.markDeletePending(reason, now);
        recordEvent(row, ReportEvidenceMediaRetentionEvent.EventType.DELETE_PENDING, reason, SYSTEM_ACTOR, now);
    }

    /**
     * 소유자 계정 상태. GRACE는 복구 가능한 유예 기간, FINALIZED는 최종 처리 완료, NOT_PENDING은 복구된 상태,
     * MISSING은 계정이 없는 상태다.
     */
    private String ownerState(ReportEvidenceMediaRetention row) {
        List<String> states = retentionRepository.findOwnerDeletionState(row.getOwnerUserId());
        return states.isEmpty() ? "MISSING" : states.get(0);
    }

    private boolean isPastRetention(ReportEvidenceMediaRetention row, LocalDateTime now) {
        LocalDateTime until = ReportEvidenceRetentionPolicy.effectiveUntil(
                row.getRetainUntil(), row.getHardLimitAt(), row.getExtensionUntil());
        return !now.isBefore(until);
    }

    /** 객체를 아직 쓰는 주체가 있는지. 실제 사용처가 있으면 검토로, 다른 보존 기록만 있으면 기다린다. */
    private enum Reference { NONE, LIVE_REFERENCE, RETENTION_ROW }

    /**
     * 같은 저장소 객체를 다른 이미지, 이 게시글이 아닌 미디어 자산, 레거시 보류 기록, 좌석 사진이 가리키면 LIVE_REFERENCE,
     * 다른 보존 기록만 가리키면 RETENTION_ROW다. 객체 키는 저장소가 받아들이는 모든 표기로 확인한다.
     */
    private Reference referenceOf(ReportEvidenceMediaRetention row) {
        Set<String> keys = StorageObjectKey.aliases(storageConfig.getCheerBucket(), row.getStoragePath());
        // The same check the media deletion paths use: exact spellings, plus a substring match on the shortest key.
        String shortest = keys.stream().min(Comparator.comparingInt(String::length)).orElseThrow();
        String substring = shortest.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        Long references = retentionRepository.countOtherReferences(keys, row.getPostImageId(), row.getPostId(), substring);
        boolean live = (references != null && references > 0)
                || seatViewPhotos.existsByTrimmedStoragePathIn(keys)
                || seatViewPhotos.existsPossiblyReferencingStoragePath(shortest);
        if (live) {
            return Reference.LIVE_REFERENCE;
        }
        Long protectingRows = retentionRepository.countProtectingRetentionRows(keys, row.getId(), substring);
        return protectingRows != null && protectingRows > 0 ? Reference.RETENTION_ROW : Reference.NONE;
    }

    private boolean register(Supplier<ReportEvidenceMediaRetention> factory, LocalDateTime now) {
        try {
            ReportEvidenceMediaRetention entity = factory.get();
            transaction().executeWithoutResult(status -> {
                ReportEvidenceMediaRetention saved = retentionRepository.save(entity);
                ReportEvidenceMediaRetentionEvent.EventType type = saved.getStatus() == ReportEvidenceMediaStatus.REVIEW_REQUIRED
                        ? ReportEvidenceMediaRetentionEvent.EventType.REVIEW_REQUIRED
                        : ReportEvidenceMediaRetentionEvent.EventType.REGISTERED;
                recordEvent(saved, type, null, SYSTEM_ACTOR, now);
            });
            return true;
        } catch (RuntimeException ex) {
            log.error("신고 증거 미디어 보관 등록 실패: error={}", ex.getClass().getSimpleName());
            return false;
        }
    }

    private void runPhase(String phase, Runnable work) {
        try {
            work.run();
        } catch (RuntimeException ex) {
            log.error("신고 증거 미디어 보관 단계 실패: phase={}, error={}", phase, ex.getClass().getSimpleName());
        }
    }

    private static void requireNoAmbientTransaction() {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Evidence deletion must run outside an existing transaction");
        }
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    /** 짧은 트랜잭션 안에서 행을 잠근다. 외부 저장소 호출은 이 잠금 밖에서 한다. */
    private ReportEvidenceMediaRetention findRowForUpdate(Long retentionId) {
        return retentionRepository.findByIdForUpdate(retentionId)
                .orElseThrow(() -> new IllegalArgumentException("보관 기록을 찾을 수 없습니다: " + retentionId));
    }

    private ReportEvidenceMediaRetention findRow(Long retentionId) {
        return retentionRepository.findById(retentionId)
                .orElseThrow(() -> new IllegalArgumentException("보관 기록을 찾을 수 없습니다: " + retentionId));
    }

    /**
     * 연장과 법적 보존은 관리자만 기록할 수 있다. 권한이 없거나 로그인하지 않은 호출은 거부한다.
     * 기록되는 승인자는 현재 인증 주체의 이름이며, 관리자 계정 번호여야 한다.
     */
    private static String requireAdministrator() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn("보존 기록 변경 거부: 인증되지 않은 호출");
            throw new AccessDeniedException("로그인한 관리자만 보존 기록을 변경할 수 있습니다.");
        }
        boolean administrator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITIES::contains);
        String name = authentication.getName();
        if (!administrator || isBlank(name) || !name.matches("\\d{1,18}")) {
            log.warn("보존 기록 변경 거부: 관리자 권한 또는 계정 번호 없음");
            throw new AccessDeniedException("로그인한 관리자만 보존 기록을 변경할 수 있습니다.");
        }
        return name;
    }

    /**
     * 관리자 결정을 감사 기록으로 남긴다. 이전 값과 새 값을 함께 남겨서, 두 번째 결정이 첫 번째 기록을 지우지 않게 한다.
     * 기록에는 저장소 경로나 사용자 이름 대신 식별자와 시각만 넣는다.
     */
    private void audit(String approver, ReportEvidenceMediaRetention row, AuditLog.AuditAction action,
                       String oldValue, String newValue) {
        auditLogRepository.save(AuditLog.builder()
                .adminId(Long.valueOf(approver))
                .targetUserId(row.getOwnerUserId())
                .action(action)
                .oldValue(truncate(oldValue, MAX_AUDIT_VALUE_LENGTH))
                .newValue(truncate(newValue, MAX_AUDIT_VALUE_LENGTH))
                .description(truncate("retentionId=" + row.getId() + ", postId=" + row.getPostId(),
                        MAX_AUDIT_DESCRIPTION_LENGTH))
                .build());
        log.info("신고 증거 보존 결정 감사 기록: action={}, retentionId={}", action, row.getId());
    }

    /** 이력 한 줄을 추가한다. 시스템 전이는 reason을 넣고, 관리자 결정은 actor와 이전 값을 넣는다. */
    private void recordEvent(ReportEvidenceMediaRetention row, ReportEvidenceMediaRetentionEvent.EventType type,
                             ReportEvidenceDeleteReason reason, String actor, LocalDateTime now) {
        recordEvent(row, type, reason, actor, null, null, null, null, null, null, now);
    }

    private void recordEvent(ReportEvidenceMediaRetention row, ReportEvidenceMediaRetentionEvent.EventType type,
                             ReportEvidenceDeleteReason reason, String actor, String caseRef, String previousValue,
                             String newValue, String basisText, String previousActor, String previousBasis,
                             LocalDateTime now) {
        eventRepository.save(ReportEvidenceMediaRetentionEvent.builder()
                .retentionId(row.getId())
                .eventType(type)
                .reason(reason)
                .actor(actor)
                .caseRef(truncate(caseRef, 64))
                .previousValue(truncate(previousValue, 100))
                .newValue(truncate(newValue, 100))
                .basisText(truncate(basisText, MAX_BASIS_LENGTH))
                .previousBasis(truncate(previousBasis, MAX_BASIS_LENGTH))
                .previousActor(truncate(previousActor, 64))
                .createdAt(now)
                .build());
    }

    /** 사건 번호는 이 게시글에 아직 결정되지 않은 신고의 번호여야 한다. */
    private boolean isOpenReportOfPost(String caseRef, Long postId) {
        return retentionRepository.findOpenReportIds(postId).stream()
                .anyMatch(reportId -> String.valueOf(reportId).equals(caseRef));
    }

    private static String requireText(String value, int maxLength, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(message + " (최대 " + maxLength + "자)");
        }
        return trimmed;
    }

    private static String truncate(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static Long toLong(Object value) {
        return ((Number) value).longValue();
    }

    /** 1단계가 만든 삭제 작업. 저장소 호출과 결과 기록에 필요한 값만 담는다. */
    private record StorageDeletion(Long retentionId, Long postImageId, String bucket, String storagePath, String claimToken) {}
}
