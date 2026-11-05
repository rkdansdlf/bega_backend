package com.example.cheerboard.retention;

import com.example.common.dto.ApiResponse;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 신고 증거 보존 기록의 운영자 API. 자동 삭제가 꺼져 있어도 운영자는 여기서 검토 대상을 보고 연장이나 법적 보존을
 * 걸 수 있다. 모든 호출은 관리자 권한이 필요하고, 승인자는 로그인한 관리자 계정 번호로 기록된다.
 */
@RestController
@RequestMapping("/api/admin/report-evidence-retention")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ReportEvidenceRetentionAdminController {

    private static final List<ReportEvidenceMediaStatus> REVIEW_STATUSES = List.of(
            ReportEvidenceMediaStatus.URGENT_REVIEW,
            ReportEvidenceMediaStatus.REVIEW_REQUIRED,
            ReportEvidenceMediaStatus.DELETE_PENDING);
    private static final int MAX_PAGE_SIZE = 100;

    private final ReportEvidenceMediaRetentionService retentionService;
    private final ReportEvidenceMediaRetentionRepository retentionRepository;
    private final ReportEvidenceMediaRetentionEventRepository eventRepository;

    /** 운영자가 판단해야 하는 기록(긴급 검토, 검토 대기, 삭제 대기)을 오래된 순으로 돌려준다. 저장소 경로는 싣지 않는다. */
    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<ReviewPage>> listReviews(@RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        var found = retentionRepository.findByStatusIn(REVIEW_STATUSES,
                PageRequest.of(Math.max(page, 0), safeSize, Sort.by("id")));
        return ResponseEntity.ok(ApiResponse.success("검토 대상 신고 증거 보존 기록입니다.", new ReviewPage(
                found.getContent().stream().map(ReviewItem::from).toList(),
                found.getNumber(),
                found.getSize(),
                found.getTotalElements())));
    }

    /** 한 기록의 변경 이력. 추가만 되는 테이블에서 시간순으로 읽는다. */
    @GetMapping("/{id}/events")
    public ResponseEntity<ApiResponse<List<EventItem>>> listEvents(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("보존 기록 이력입니다.",
                eventRepository.findByRetentionIdOrderByIdAsc(id).stream().map(EventItem::from).toList()));
    }

    @PostMapping("/{id}/extension")
    public ResponseEntity<ApiResponse<Void>> approveExtension(@PathVariable Long id,
                                                              @RequestBody ExtensionRequest request) {
        retentionService.approveExtension(id, request.until(), request.caseRef());
        return ResponseEntity.ok(ApiResponse.success("보존 연장을 기록했습니다."));
    }

    @PostMapping("/{id}/legal-hold")
    public ResponseEntity<ApiResponse<Void>> placeLegalHold(@PathVariable Long id,
                                                            @RequestBody LegalHoldRequest request) {
        retentionService.placeLegalHold(id, request.basis(), request.reviewDueAt());
        return ResponseEntity.ok(ApiResponse.success("법적 보존 근거를 기록했습니다."));
    }

    /** 검토 대기 기록의 삭제를 승인한다. 사건 번호와 사유를 이력에 남긴다. */
    @PostMapping("/{id}/release")
    public ResponseEntity<ApiResponse<Void>> releaseForDeletion(@PathVariable Long id,
                                                                @RequestBody ReleaseRequest request) {
        retentionService.releaseForDeletion(id, request.caseRef(), request.note());
        return ResponseEntity.ok(ApiResponse.success("검토 대기 보존 기록의 삭제를 승인했습니다."));
    }

    /** 입력 오류는 서비스가 적어 준 메시지를 그대로 돌려준다. 메시지는 이 클래스가 직접 정한 문구다. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("BAD_REQUEST", ex.getMessage()));
    }

    /** 현재 상태에서는 결정할 수 없다는 뜻이다(예: 삭제 점유 중, 사건 종료). 전역 핸들러의 500 대신 409로 돌려준다. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("CONFLICT", ex.getMessage()));
    }

    public record ExtensionRequest(LocalDateTime until, String caseRef) {}

    public record LegalHoldRequest(String basis, LocalDateTime reviewDueAt) {}

    public record ReleaseRequest(String caseRef, String note) {}

    public record ReviewPage(List<ReviewItem> items, int page, int size, long totalElements) {}

    public record ReviewItem(Long id, Long postId, Long ownerUserId, ReportEvidenceMediaStatus status,
                             LocalDateTime retainUntil, LocalDateTime hardLimitAt, LocalDateTime extensionUntil,
                             LocalDateTime legalReviewDueAt, LocalDateTime urgentReviewDueAt,
                             ReportEvidenceDeleteReason deleteReason, int attemptCount,
                             LocalDateTime nextAttemptAt, String lastError) {

        static ReviewItem from(ReportEvidenceMediaRetention row) {
            return new ReviewItem(row.getId(), row.getPostId(), row.getOwnerUserId(), row.getStatus(),
                    row.getRetainUntil(), row.getHardLimitAt(), row.getExtensionUntil(),
                    row.getLegalReviewDueAt(), row.getUrgentReviewDueAt(), row.getDeleteReason(),
                    row.getAttemptCount(), row.getNextAttemptAt(), row.getLastError());
        }
    }

    public record EventItem(Long id, ReportEvidenceMediaRetentionEvent.EventType eventType,
                            ReportEvidenceDeleteReason reason, String actor, String caseRef,
                            String previousValue, String newValue, String basisText, String previousBasis,
                            String previousActor, LocalDateTime createdAt) {

        static EventItem from(ReportEvidenceMediaRetentionEvent event) {
            return new EventItem(event.getId(), event.getEventType(), event.getReason(), event.getActor(),
                    event.getCaseRef(), event.getPreviousValue(), event.getNewValue(), event.getBasisText(),
                    event.getPreviousBasis(), event.getPreviousActor(), event.getCreatedAt());
        }
    }
}
