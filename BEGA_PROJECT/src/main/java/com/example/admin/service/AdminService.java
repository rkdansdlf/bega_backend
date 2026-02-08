package com.example.admin.service;

import com.example.admin.dto.AdminMateDto;
import com.example.admin.dto.AdminPostDto;
import com.example.admin.dto.AdminStatsDto;
import com.example.admin.dto.AdminUserDto;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;
import com.example.mate.service.PartyService;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final PartyRepository partyRepository;
    private final CheerCommentRepo commentRepository;
    private final CheerPostLikeRepo likeRepository;
    private final CacheManager cacheManager;
    private final AuditLogRepository auditLogRepository;
    private final PartyService partyService;

    /**
     * 대시보드 통계 조회
     */
    public AdminStatsDto getStats() {
        long totalUsers = userRepository.count();
        long totalPosts = cheerPostRepository.count();
        long totalMates = partyRepository.count();

        return AdminStatsDto.builder()
                .totalUsers(totalUsers)
                .totalPosts(totalPosts)
                .totalMates(totalMates)
                .build();
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

        return users.stream()
                .map(this::convertToAdminUserDto)
                .collect(Collectors.toList());
    }

    /**
     * 게시글 목록 조회 (최신순)
     */
    public List<AdminPostDto> getPosts() {
        // 🔥 createdAt 기준 내림차순 정렬
        List<CheerPost> posts = cheerPostRepository.findAllByOrderByCreatedAtDesc();

        return posts.stream()
                .map(this::convertToAdminPostDto)
                .collect(Collectors.toList());
    }

    /**
     * CheerPost → AdminPostDto 변환
     */
    private AdminPostDto convertToAdminPostDto(CheerPost post) {
        // 🔥 HOT 판단 로직: 좋아요 10개 이상 또는 조회수 100 이상
        boolean isHot = post.getLikeCount() >= 10 || post.getViews() >= 100;

        return AdminPostDto.builder()
                .id(post.getId())
                .team(post.getTeamId())
                .content(post.getContent())
                .author(post.getAuthor().getName())
                .createdAt(post.getCreatedAt())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .views(post.getViews())
                .isHot(isHot)
                .build();
    }

    /**
     * 메이트 목록 조회 (최신순)
     */
    public List<AdminMateDto> getMates() {
        List<Party> parties = partyRepository.findAllByOrderByCreatedAtDesc();

        return parties.stream()
                .map(this::convertToAdminMateDto)
                .collect(Collectors.toList());
    }

    /**
     * Party → AdminMateDto 변환
     */
    private AdminMateDto convertToAdminMateDto(Party party) {
        return AdminMateDto.builder()
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
                .build();
    }

    /**
     * 유저 삭제 (연관된 데이터도 함께 삭제)
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

        // 유저 삭제
        userRepository.delete(Objects.requireNonNull(user));

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

    /**
     * UserEntity → AdminUserDto 변환
     */
    private AdminUserDto convertToAdminUserDto(UserEntity user) {
        Long userId = Objects.requireNonNull(user.getId(), "User ID must not be null");
        String email = Objects.requireNonNull(user.getEmail(), "User email must not be null");
        String name = Objects.requireNonNull(user.getName(), "User name must not be null");

        // 해당 유저의 게시글 수 조회
        long postCount = cheerPostRepository.countByUserId(userId);

        return AdminUserDto.builder()
                .id(userId)
                .email(email)
                .name(name)
                .favoriteTeam(user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamId() : null)
                .createdAt(user.getCreatedAt())
                .postCount(postCount)
                .role(user.getRole())
                .build();
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
