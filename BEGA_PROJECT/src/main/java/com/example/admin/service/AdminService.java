package com.example.admin.service;

import com.example.admin.dto.AdminMateDto;
import com.example.admin.dto.AdminPostDto;
import com.example.admin.dto.AdminReportActionReq;
import com.example.admin.dto.AdminReportAppealReq;
import com.example.admin.dto.AdminReportDto;
import com.example.admin.dto.AdminStatsDto;
import com.example.admin.dto.AdminUserDto;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.ReportReason;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.auth.repository.RefreshRepository;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;
import com.example.mate.service.PartyService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 관리자 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final CheerPostRepo cheerPostRepository;
    private final CheerReportRepo cheerReportRepo;
    private final PartyRepository partyRepository;
    private final CheerCommentRepo commentRepository;
    private final CheerPostLikeRepo likeRepository;
    private final CacheManager cacheManager;
    private final AuditLogRepository auditLogRepository;
    private final PartyService partyService;
    private final RefreshRepository refreshRepository;

    /**
     * 대시보드 통계 조회
     */
    public AdminStatsDto getStats() {
        long totalUsers = userRepository.count();
        long totalPosts = cheerPostRepository.count();
        long totalMates = partyRepository.count();

        return Objects.requireNonNull(AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .totalPosts(totalPosts)
                .totalMates(totalMates)
                .build());
    }

    /**
     * 유저 목록 조회 (검색 기능 포함) - ID 순
     */
    public List<AdminUserDto> getUsers(String search) {
        List<UserEntity> users;

        if (search != null && !search.trim().isEmpty()) {
            // 이메일 또는 이름으로 검색
            users = userRepository.findByEmailContainingOrNameContainingOrderByIdAsc(
                    search.trim(),
                    search.trim());
        } else {
            // 🔥 전체 조회 (ID 순)
            users = userRepository.findAllByOrderByIdAsc();
        }

        return Objects.requireNonNull(users.stream()
                .map(this::convertToAdminUserDto)
                .collect(Collectors.toList()));
    }

    /**
     * 게시글 목록 조회 (최신순)
     */
    public List<AdminPostDto> getPosts() {
        // 🔥 createdAt 기준 내림차순 정렬
        List<CheerPost> posts = cheerPostRepository.findAllByOrderByCreatedAtDesc();

        return Objects.requireNonNull(posts.stream()
                .map(this::convertToAdminPostDto)
                .collect(Collectors.toList()));
    }

    /**
     * CheerPost → AdminPostDto 변환
     */
    private AdminPostDto convertToAdminPostDto(CheerPost post) {
        // 🔥 HOT 판단 로직: 좋아요 10개 이상 또는 조회수 100 이상
        boolean isHot = post.getLikeCount() >= 10 || post.getViews() >= 100;

        return Objects.requireNonNull(AdminPostDto.builder()
                .id(post.getId())
                .team(post.getTeamId())
                .content(post.getContent())
                .author(post.getAuthor().getName())
                .createdAt(post.getCreatedAt())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .views(post.getViews())
                .isHot(isHot)
                .build());
    }

    /**
     * 메이트 목록 조회 (최신순)
     */
    public List<AdminMateDto> getMates() {
        List<Party> parties = partyRepository.findAllByOrderByCreatedAtDesc();

        return Objects.requireNonNull(parties.stream()
                .map(this::convertToAdminMateDto)
                .collect(Collectors.toList()));
    }

    /**
     * Party → AdminMateDto 변환
     */
    private AdminMateDto convertToAdminMateDto(Party party) {
        return Objects.requireNonNull(AdminMateDto.builder()
                .id(party.getId())
                .teamId(party.getTeamId())
                .title(party.getDescription().length() > 30
                        ? party.getDescription().substring(0, 30) + "..."
                        : party.getDescription()) // 설명을 제목처럼 사용
                .stadium(party.getStadium())
                .gameDate(party.getGameDate())
                .currentMembers(party.getCurrentParticipants())
                .maxMembers(party.getMaxParticipants())
                .status(party.getStatus().name().toLowerCase()) // PENDING → pending
                .createdAt(party.getCreatedAt())
                .hostName(party.getHostName())
                .homeTeam(party.getHomeTeam())
                .awayTeam(party.getAwayTeam())
                .section(party.getSection())
                .build());
    }

    /**
     * 유저 삭제 (연관된 데이터 정리 후 계정 비활성화 처리)
     * 
     * @param userId  삭제할 유저 ID
     * @param adminId 삭제를 수행하는 관리자 ID (감사 로그용, nullable)
     */
    @Transactional
    public void deleteUser(Long userId, Long adminId) {
        Objects.requireNonNull(userId, "userId must not be null");

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        String userEmail = user.getEmail();

        // 좋아요 삭제
        List<CheerPostLike> userLikes = likeRepository.findByUser(user);
        if (!userLikes.isEmpty()) {
            likeRepository.deleteAll(userLikes);
        }

        // 댓글 삭제
        List<CheerComment> userComments = commentRepository.findByAuthor(user);
        if (!userComments.isEmpty()) {
            commentRepository.deleteAll(userComments);
        }

        // 게시글 삭제
        List<CheerPost> userPosts = cheerPostRepository.findByAuthor(user);
        if (!userPosts.isEmpty()) {
            cheerPostRepository.deleteAll(userPosts);
        }

        // 메이트 관련 데이터 정리 (파티 취소, 참여 신청 처리, 알림 발송)
        partyService.handleUserDeletion(userId);

        // 유저 비활성화 + 토큰 버전 증가로 접근 무효화
        int currentTokenVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        user.setEnabled(false);
        user.setTokenVersion(currentTokenVersion + 1);
        user.setLockExpiresAt(null);
        userRepository.save(Objects.requireNonNull(user));

        // 리프레시 토큰 정리
        refreshRepository.deleteByEmail(userEmail);

        // 감사 로그 기록
        if (adminId != null) {
            AuditLog auditLog = AuditLog.builder()
                    .adminId(adminId)
                    .targetUserId(userId)
                    .action(AuditLog.AuditAction.DELETE_USER)
                    .oldValue(userEmail)
                    .newValue(null)
                    .description("사용자 삭제")
                    .build();
            auditLogRepository.save(Objects.requireNonNull(auditLog));
            log.info("User {} deleted by admin {}. Email: {}", userId, adminId, userEmail);
        }
    }

    /**
     * 응원 게시글 삭제
     * 
     * @param postId  삭제할 게시글 ID
     * @param adminId 삭제를 수행하는 관리자 ID (감사 로그용, nullable)
     */
    @Transactional
    public void deletePost(Long postId, Long adminId) {
        Long id = Objects.requireNonNull(postId, "postId must not be null");

        CheerPost post = cheerPostRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다."));

        String postContent = post.getContent();
        Long authorId = post.getAuthor().getId();

        cheerPostRepository.deleteById(id);

        // 감사 로그 기록
        if (adminId != null) {
            AuditLog auditLog = AuditLog.builder()
                    .adminId(adminId)
                    .targetUserId(authorId)
                    .action(AuditLog.AuditAction.DELETE_POST)
                    .oldValue(postContent)
                    .newValue(null)
                    .description("게시글 삭제 (ID: " + postId + ")")
                    .build();
            auditLogRepository.save(Objects.requireNonNull(auditLog));
            log.info("Post {} deleted by admin {}. Content: {}", postId, adminId, postContent);
        }
    }

    /**
     * 메이트 모임 삭제
     * 
     * @param mateId  삭제할 메이트 모임 ID
     * @param adminId 삭제를 수행하는 관리자 ID (감사 로그용, nullable)
     */
    @Transactional
    public void deleteMate(Long mateId, Long adminId) {
        Long id = Objects.requireNonNull(mateId, "mateId must not be null");

        Party party = partyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("메이트 모임을 찾을 수 없습니다."));

        String partyDesc = party.getDescription();
        Long hostId = party.getHostId();

        partyRepository.deleteById(id);

        // 감사 로그 기록
        if (adminId != null) {
            AuditLog auditLog = AuditLog.builder()
                    .adminId(adminId)
                    .targetUserId(hostId)
                    .action(AuditLog.AuditAction.DELETE_MATE)
                    .oldValue(partyDesc)
                    .newValue(null)
                    .description("메이트 모임 삭제 (ID: " + mateId + ")")
                    .build();
            auditLogRepository.save(Objects.requireNonNull(auditLog));
            log.info("Mate {} deleted by admin {}. Description: {}", mateId, adminId, partyDesc);
        }
    }

    @Transactional(readOnly = true)
    public Page<AdminReportDto> getReports(
            String status,
            String reason,
            LocalDate fromDate,
            LocalDate toDate,
            int page,
            int size) {
        CheerPostReport.ReportStatus statusFilter = parseReportStatus(status);
        ReportReason reasonFilter = parseReportReason(reason);
        LocalDateTime fromAt = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toAt = toDate != null ? toDate.plusDays(1).atStartOfDay().minusNanos(1) : null;
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));

        return cheerReportRepo.findForAdmin(statusFilter, reasonFilter, fromAt, toAt, pageable)
                .map(this::convertToAdminReportDto);
    }

    @Transactional(readOnly = true)
    public AdminReportDto getReport(Long reportId) {
        CheerPostReport report = cheerReportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 케이스를 찾을 수 없습니다."));
        return Objects.requireNonNull(convertToAdminReportDto(report));
    }

    @Transactional
    public AdminReportDto handleReport(Long reportId, AdminReportActionReq req, Long adminId) {
        if (req == null || req.action() == null) {
            throw new IllegalArgumentException("조치(action)는 필수입니다.");
        }

        CheerPostReport report = cheerReportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 케이스를 찾을 수 없습니다."));

        report.setStatus(CheerPostReport.ReportStatus.RESOLVED);
        report.setAdminAction(req.action());
        report.setAdminMemo(req.adminMemo());
        report.setHandledBy(adminId);
        report.setHandledAt(LocalDateTime.now());

        if (req.action() == CheerPostReport.AdminAction.TAKE_DOWN) {
            report.getPost().setDeleted(true);
            cheerPostRepository.save(Objects.requireNonNull(report.getPost()));
        } else if (req.action() == CheerPostReport.AdminAction.RESTORE) {
            report.getPost().setDeleted(false);
            cheerPostRepository.save(Objects.requireNonNull(report.getPost()));
        }

        CheerPostReport saved = cheerReportRepo.save(Objects.requireNonNull(report));

        if (adminId != null) {
            AuditLog.AuditAction auditAction = toAuditAction(req.action());
            if (auditAction != null) {
                AuditLog auditLog = AuditLog.builder()
                        .adminId(adminId)
                        .targetUserId(saved.getPost().getAuthor().getId())
                        .action(auditAction)
                        .oldValue(saved.getReason().name())
                        .newValue(saved.getStatus().name())
                        .description("신고 케이스 처리 (ID: " + saved.getId() + ", action: " + req.action().name() + ")")
                        .build();
                auditLogRepository.save(Objects.requireNonNull(auditLog));
            }
        }

        return Objects.requireNonNull(convertToAdminReportDto(saved));
    }

    @Transactional
    public AdminReportDto requestAppeal(Long reportId, AdminReportAppealReq req, Long userId) {
        CheerPostReport report = cheerReportRepo.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("신고 케이스를 찾을 수 없습니다."));

        report.setAppealStatus(CheerPostReport.AppealStatus.REQUESTED);
        report.setAppealReason(req != null ? req.appealReason() : null);
        report.setAppealCount((report.getAppealCount() == null ? 0 : report.getAppealCount()) + 1);

        CheerPostReport saved = cheerReportRepo.save(Objects.requireNonNull(report));

        if (userId != null) {
            AuditLog auditLog = AuditLog.builder()
                    .adminId(userId)
                    .targetUserId(saved.getPost().getAuthor().getId())
                    .action(AuditLog.AuditAction.WARN_REPEATED_INFRACTION)
                    .oldValue(saved.getStatus().name())
                    .newValue(saved.getAppealStatus().name())
                    .description("신고 케이스 이의제기 등록 (ID: " + saved.getId() + ")")
                    .build();
            auditLogRepository.save(Objects.requireNonNull(auditLog));
        }

        return Objects.requireNonNull(convertToAdminReportDto(saved));
    }

    /**
     * UserEntity → AdminUserDto 변환
     */
    private AdminUserDto convertToAdminUserDto(UserEntity user) {
        Long userId = Objects.requireNonNull(user.getId(), "User ID must not be null");
        String email = Objects.requireNonNull(user.getEmail(), "User email must not be null");
        String name = Objects.requireNonNull(user.getName(), "User name must not be null");

        // 해당 유저의 게시글 수 조회
        long postCount = cheerPostRepository.countByUserId(userId);

        return Objects.requireNonNull(AdminUserDto.builder()
                .id(userId)
                .email(email)
                .name(name)
                .favoriteTeam(user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamId() : null)
                .createdAt(user.getCreatedAt())
                .postCount(postCount)
                .role(user.getRole())
                .build());
    }

    private AdminReportDto convertToAdminReportDto(CheerPostReport report) {
        String postContent = report.getPost() != null ? report.getPost().getContent() : null;
        String postPreview = postContent == null ? null
                : (postContent.length() > 120 ? postContent.substring(0, 120) + "..." : postContent);

        return Objects.requireNonNull(AdminReportDto.builder()
                .id(report.getId())
                .postId(report.getPost() != null ? report.getPost().getId() : null)
                .postPreview(postPreview)
                .reporterId(report.getReporter() != null ? report.getReporter().getId() : null)
                .reporterHandle(report.getReporter() != null ? report.getReporter().getHandle() : null)
                .reason(report.getReason() != null ? report.getReason().name() : null)
                .description(report.getDescription())
                .status(report.getStatus() != null ? report.getStatus().name() : null)
                .adminAction(report.getAdminAction() != null ? report.getAdminAction().name() : null)
                .adminMemo(report.getAdminMemo())
                .handledBy(report.getHandledBy())
                .handledAt(report.getHandledAt())
                .evidenceUrl(report.getEvidenceUrl())
                .requestedAction(report.getRequestedAction())
                .appealStatus(report.getAppealStatus() != null ? report.getAppealStatus().name() : null)
                .appealReason(report.getAppealReason())
                .appealCount(report.getAppealCount())
                .createdAt(report.getCreatedAt())
                .build());
    }

    private CheerPostReport.ReportStatus parseReportStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CheerPostReport.ReportStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("지원하지 않는 신고 상태입니다: " + status);
        }
    }

    private ReportReason parseReportReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        try {
            return ReportReason.valueOf(reason.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("지원하지 않는 신고 사유입니다: " + reason);
        }
    }

    private AuditLog.AuditAction toAuditAction(CheerPostReport.AdminAction action) {
        if (action == null) {
            return null;
        }
        return switch (action) {
            case TAKE_DOWN -> AuditLog.AuditAction.TAKE_DOWN_REPORT;
            case DISMISS -> AuditLog.AuditAction.DISMISS_REPORT;
            case RESTORE -> AuditLog.AuditAction.RESTORE_REPORT;
            case WARNING, REQUIRE_MODIFICATION -> AuditLog.AuditAction.WARN_REPEATED_INFRACTION;
        };
    }

    /**
     * 캐시 통계 조회 (관리자 전용)
     */
    public java.util.Map<String, Object> getCacheStats() {
        java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();

        var cacheNames = cacheManager.getCacheNames();
        for (String cacheName : cacheNames) {
            if (cacheName == null)
                continue;
            org.springframework.cache.Cache cache = cacheManager.getCache(Objects.requireNonNull(cacheName));
            if (cache != null) {
                Object nativeCache = cache.getNativeCache();
                if (nativeCache instanceof com.github.benmanes.caffeine.cache.Cache<?, ?> caffeineCache) {
                    com.github.benmanes.caffeine.cache.stats.CacheStats stats = caffeineCache.stats();
                    java.util.Map<String, Object> cacheInfo = new java.util.LinkedHashMap<>();
                    cacheInfo.put("size", caffeineCache.estimatedSize());
                    cacheInfo.put("hitCount", stats.hitCount());
                    cacheInfo.put("missCount", stats.missCount());
                    cacheInfo.put("hitRate", String.format("%.2f%%", stats.hitRate() * 100));
                    cacheInfo.put("evictionCount", stats.evictionCount());
                    result.put(Objects.requireNonNull(cacheName), cacheInfo);
                }
            }
        }

        return result;
    }
}
