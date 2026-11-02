package com.example.cheerboard.retention;

import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.cheerboard.storage.strategy.StorageObjectKey;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.scheduler.Schedulers;

/**
 * 신고된 게시글 이미지의 보관 기한을 관리한다. 계정 삭제 가드(purge 보류)와 독립적으로 동작하며,
 * 가드 쪽 상태를 읽거나 바꾸지 않는다.
 *
 * <p>각 행은 자기 트랜잭션에서 처리한다. 한 행의 실패가 다른 행이나 다른 단계를 막지 않도록 하기 위해서다.
 * 로그에는 저장소 경로, 사용자 식별자, 원본 예외 메시지를 남기지 않는다. 관리자 결정은 감사 기록에 남긴다.
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
    private static final int MAX_CASE_REF_LENGTH = 64;
    private static final int MAX_BASIS_LENGTH = 512;
    private static final int MAX_AUDIT_DESCRIPTION_LENGTH = 500;

    private final ReportEvidenceMediaRetentionRepository retentionRepository;
    private final PostImageRepository postImageRepository;
    private final SeatViewPhotoRepository seatViewPhotos;
    private final AuditLogRepository auditLogRepository;
    private final StorageStrategy storageStrategy;
    private final StorageConfig storageConfig;
    private final PlatformTransactionManager transactionManager;

    /** 매일 실행되는 전체 흐름. 단계마다 독립적으로 실행하므로 한 단계가 실패해도 나머지 단계는 계속 돈다. */
    public void runDaily(LocalDateTime now) {
        runPhase("discover", () -> discover(now));
        runPhase("expire", () -> expireDueRows(now));
        runPhase("urgent", () -> expireUrgentRows(now));
        runPhase("delete", () -> deleteDueObjects(now));
        runPhase("review-alert", () -> alertStaleReviewRows(now));
    }

    /**
     * 검토 대상으로 등록된 뒤 기본 보관 기한이 지나도록 처리되지 않은 기록을 센다. 삭제나 판정은 하지 않고,
     * 운영자가 처리해야 한다는 신호로만 로그를 남긴다.
     */
    public long alertStaleReviewRows(LocalDateTime now) {
        long stale = retentionRepository.countByStatusAndCreatedAtBefore(
                ReportEvidenceMediaStatus.REVIEW_REQUIRED, now.minus(ReportEvidenceRetentionPolicy.DEFAULT_RETENTION));
        if (stale > 0) {
            log.error("ALERT 검토 대기 신고 증거 미디어가 기본 보관 기한을 넘겼습니다: count={}", stale);
        }
        return stale;
    }

    /**
     * 신고 이미지 중 보관 기록이 없는 것을 등록한다. 계정 삭제 요청이 대기 중인 소유자의 이미지만 등록한다.
     * 같은 저장소 객체는 한 번만 등록한다. 기준일과 주기 시작일은 등록 시점에 행에 저장하고, 이후에는 바꾸지 않는다.
     * 한 행의 데이터가 잘못되어도 나머지 행의 등록은 계속한다.
     */
    public int discover(LocalDateTime now) {
        int created = 0;
        Set<String> queued = new HashSet<>();
        for (Object[] row : retentionRepository.findUnregisteredReportedImagesWithBase()) {
            try {
                if (registerWithBase(row, queued, now)) {
                    created++;
                }
            } catch (RuntimeException ex) {
                log.error("신고 증거 미디어 보관 등록 행 처리 실패: error={}", ex.getClass().getSimpleName());
            }
        }
        for (Object[] row : retentionRepository.findUnregisteredReportedImagesWithoutBase()) {
            try {
                if (registerWithoutBase(row, queued, now)) {
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
     *   <li>법적 보존 근거가 있으면 검토 기한 전까지 유지하고, 지나면 검토 대상으로 바꾼다.</li>
     *   <li>사건이 종료되었고 보존 근거가 없으면 기한 전이라도 삭제 대상으로 바꾼다.</li>
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
     * 긴급 검토 중인 기록을 처리한다. 사건이 닫히거나 유예가 끝나도록 결정이 없으면 삭제 대상으로 바꾼다.
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
     * 삭제 대상 객체를 지운다. 삭제 직전에 보존 근거, 소유자 상태, 다른 참조를 다시 확인한다.
     * 실패하면 상태를 유지하고 재시도 시각을 늦춘다.
     */
    public int deleteDueObjects(LocalDateTime now) {
        int deleted = 0;
        for (Long id : retentionRepository.findIdsByStatus(ReportEvidenceMediaStatus.DELETE_PENDING)) {
            try {
                if (Boolean.TRUE.equals(transaction().execute(status -> deleteOne(findRowForUpdate(id), now)))) {
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
     */
    void approveExtensionAt(Long retentionId, LocalDateTime until, String caseRef, LocalDateTime now) {
        String approver = requireAdministrator();
        String ref = requireText(caseRef, MAX_CASE_REF_LENGTH, "연장에는 사건 번호가 필요합니다.");
        ReportEvidenceMediaRetention row = findRowForUpdate(retentionId);
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
        row.approveExtension(until, ref, approver, now);
        if (row.getStatus() != ReportEvidenceMediaStatus.ACTIVE) {
            row.reactivate(now);
        }
        audit(approver, row, AuditLog.AuditAction.EVIDENCE_EXTENSION_APPROVED,
                "신고 증거 보존 연장 승인: retentionId=" + row.getId() + ", postId=" + row.getPostId());
    }

    /** 서버 시각으로 법적 보존 근거를 기록한다. 승인자는 현재 로그인한 관리자다. */
    @Transactional
    public void placeLegalHold(Long retentionId, String basis, LocalDateTime reviewDueAt) {
        placeLegalHoldAt(retentionId, basis, reviewDueAt, LocalDateTime.now());
    }

    /**
     * 법적 보존 근거와 검토 기한을 기록한다. 검토 기한은 현재 시각부터 1년을 넘을 수 없다.
     * 삭제 대기나 긴급 검토 중인 행에 걸면 활성 상태로 되돌려 삭제를 막는다. 근거의 적법성은 이 코드가 판단하지 않는다.
     */
    void placeLegalHoldAt(Long retentionId, String basis, LocalDateTime reviewDueAt, LocalDateTime now) {
        String approver = requireAdministrator();
        ReportEvidenceMediaRetention row = findRowForUpdate(retentionId);
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
        row.placeLegalHold(text, reviewDueAt, approver, now);
        if (row.getStatus() == ReportEvidenceMediaStatus.DELETE_PENDING
                || row.getStatus() == ReportEvidenceMediaStatus.URGENT_REVIEW) {
            row.reactivate(now);
        }
        audit(approver, row, AuditLog.AuditAction.EVIDENCE_LEGAL_HOLD_PLACED,
                "신고 증거 법적 보존 기록: retentionId=" + row.getId() + ", postId=" + row.getPostId());
    }

    private boolean registerWithBase(Object[] row, Set<String> queued, LocalDateTime now) {
        String path = String.valueOf(row[3]);
        if (!queued.add(path)) {
            return false;
        }
        LocalDateTime firstRequestedAt = ReportEvidenceRetentionPolicy.toLocalDateTime(row[4]);
        LocalDateTime cycleStartedAt = ReportEvidenceRetentionPolicy.toLocalDateTime(row[5]);
        LocalDateTime earliestReportAt = ReportEvidenceRetentionPolicy.toLocalDateTime(row[6]);
        LocalDateTime base = ReportEvidenceRetentionPolicy.evidenceBase(
                firstRequestedAt, cycleStartedAt, earliestReportAt);
        return register(() -> ReportEvidenceMediaRetention.activeWithBase(
                toLong(row[0]), toLong(row[1]), toLong(row[2]), path, base, cycleStartedAt, now));
    }

    private boolean registerWithoutBase(Object[] row, Set<String> queued, LocalDateTime now) {
        String path = String.valueOf(row[3]);
        if (!queued.add(path)) {
            return false;
        }
        return register(() -> ReportEvidenceMediaRetention.reviewRequired(
                toLong(row[0]), toLong(row[1]), toLong(row[2]), path, now));
    }

    private boolean judgeActive(ReportEvidenceMediaRetention row, LocalDateTime now) {
        String owner = ownerState(row);
        if (OWNER_GRACE.equals(owner)) {
            return false;
        }
        if (OWNER_RESTORED.equals(owner)) {
            row.markResolvedRestored(now);
            return true;
        }
        if (!OWNER_FINALIZED.equals(owner)) {
            row.markReviewRequired(now);
            return true;
        }
        if (row.getLegalBasis() != null) {
            if (row.getLegalReviewDueAt() == null || now.isBefore(row.getLegalReviewDueAt())) {
                return false;
            }
            row.markReviewRequired(now);
            return true;
        }
        if (!retentionRepository.hasOpenCase(row.getPostId())) {
            row.markDeletePending(now);
            return true;
        }
        LocalDateTime until = ReportEvidenceRetentionPolicy.effectiveUntil(
                row.getRetainUntil(), row.getHardLimitAt(), row.getExtensionUntil());
        if (now.isBefore(until)) {
            return false;
        }
        // The case is still open past its window. Never delete it on the spot: give one review grace, then decide.
        if (row.markUrgentReview(now)) {
            log.warn("신고 증거 미디어를 긴급 검토로 전환: retentionId={}, postId={}", row.getId(), row.getPostId());
            return true;
        }
        row.markDeletePending(now);
        return true;
    }

    private boolean judgeUrgent(ReportEvidenceMediaRetention row, LocalDateTime now) {
        String owner = ownerState(row);
        if (OWNER_GRACE.equals(owner)) {
            return false;
        }
        if (OWNER_RESTORED.equals(owner)) {
            row.markResolvedRestored(now);
            return true;
        }
        if (!OWNER_FINALIZED.equals(owner)) {
            row.markReviewRequired(now);
            return true;
        }
        if (!retentionRepository.hasOpenCase(row.getPostId())) {
            row.markDeletePending(now);
            return true;
        }
        if (now.isBefore(row.getUrgentReviewDueAt())) {
            return false;
        }
        // The grace ended with no decision. The row is never held indefinitely without an approved basis.
        log.error("ALERT 긴급 검토 유예가 결정 없이 끝나 삭제 대상으로 전환합니다: retentionId={}, postId={}",
                row.getId(), row.getPostId());
        row.markDeletePending(now);
        return true;
    }

    private boolean deleteOne(ReportEvidenceMediaRetention row, LocalDateTime now) {
        // Re-checked under the row lock: another run may have moved the row since it was listed.
        if (row.getStatus() != ReportEvidenceMediaStatus.DELETE_PENDING) {
            return false;
        }
        if (row.getNextAttemptAt() != null && now.isBefore(row.getNextAttemptAt())) {
            return false;
        }
        String owner = ownerState(row);
        if (OWNER_GRACE.equals(owner)) {
            // The owner can still recover, so nothing is deleted before finalization.
            return false;
        }
        if (OWNER_RESTORED.equals(owner)) {
            row.markResolvedRestored(now);
            return false;
        }
        if (!OWNER_FINALIZED.equals(owner)) {
            row.markReviewRequired(now);
            return false;
        }
        if (row.getLegalBasis() != null) {
            if (row.getLegalReviewDueAt() == null || !now.isBefore(row.getLegalReviewDueAt())) {
                row.markReviewRequired(now);
            } else {
                row.reactivate(now);
            }
            return false;
        }
        if (retentionRepository.hasOpenCase(row.getPostId()) && !isPastRetention(row, now)) {
            // A case reopened (for example by an appeal) inside the default window keeps the evidence.
            row.reactivate(now);
            return false;
        }
        if (isStillReferenced(row)) {
            row.markReviewRequired(now);
            return false;
        }
        try {
            // The call runs off the calling thread so that the timeout can interrupt it.
            storageStrategy.deleteChecked(storageConfig.getCheerBucket(), row.getStoragePath())
                    .subscribeOn(Schedulers.boundedElastic())
                    .block(STORAGE_CALL_TIMEOUT);
        } catch (RuntimeException ex) {
            row.recordDeleteFailure(ex.getClass().getSimpleName(), now);
            if (row.getAttemptCount() >= ReportEvidenceRetentionPolicy.ALERT_ATTEMPT_THRESHOLD) {
                log.error("ALERT 신고 증거 미디어 삭제가 반복 실패합니다: retentionId={}, postId={}, attempts={}, error={}",
                        row.getId(), row.getPostId(), row.getAttemptCount(), ex.getClass().getSimpleName());
            } else {
                log.warn("신고 증거 미디어 삭제 실패, 재시도 예약: retentionId={}, attempts={}, error={}",
                        row.getId(), row.getAttemptCount(), ex.getClass().getSimpleName());
            }
            return false;
        }
        postImageRepository.findById(row.getPostImageId()).ifPresent(postImageRepository::delete);
        row.markDeleted(now);
        return true;
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

    /**
     * 같은 저장소 객체를 다른 이미지, 이 게시글이 아닌 미디어 자산, 레거시 보류 기록, 좌석 사진이 아직 가리키면
     * 객체를 지우지 않는다. 객체 키는 저장소가 받아들이는 모든 표기로 확인한다.
     */
    private boolean isStillReferenced(ReportEvidenceMediaRetention row) {
        Set<String> keys = StorageObjectKey.aliases(storageConfig.getCheerBucket(), row.getStoragePath());
        Long references = retentionRepository.countOtherReferences(keys, row.getPostImageId(), row.getPostId());
        return (references != null && references > 0) || seatViewPhotos.existsByTrimmedStoragePathIn(keys);
    }

    private boolean register(Supplier<ReportEvidenceMediaRetention> factory) {
        try {
            ReportEvidenceMediaRetention entity = factory.get();
            transaction().executeWithoutResult(status -> retentionRepository.save(entity));
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

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    /** 판단부터 외부 저장소 호출, 커밋까지 행을 잠가서 그 사이에 보존 근거나 연장이 끼어들지 못하게 한다. */
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
            throw new AccessDeniedException("로그인한 관리자만 보존 기록을 변경할 수 있습니다.");
        }
        boolean administrator = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(ADMIN_AUTHORITIES::contains);
        String name = authentication.getName();
        if (!administrator || isBlank(name) || !name.matches("\\d{1,18}")) {
            throw new AccessDeniedException("로그인한 관리자만 보존 기록을 변경할 수 있습니다.");
        }
        return name;
    }

    private void audit(String approver, ReportEvidenceMediaRetention row, AuditLog.AuditAction action,
                       String description) {
        auditLogRepository.save(AuditLog.builder()
                .adminId(Long.valueOf(approver))
                .targetUserId(row.getOwnerUserId())
                .action(action)
                .description(truncate(description, MAX_AUDIT_DESCRIPTION_LENGTH))
                .build());
        log.info("신고 증거 보존 결정 감사 기록: action={}, retentionId={}", action, row.getId());
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
        return text.length() <= maxLength ? text : text.substring(0, maxLength);
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static Long toLong(Object value) {
        return ((Number) value).longValue();
    }
}
