package com.example.admin.service;

import com.example.admin.dto.AdminMateDto;
import com.example.admin.dto.AdminPostDto;
import com.example.admin.dto.AdminStatsDto;
import com.example.admin.dto.AdminUserDto;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    /**
     * 대시보드 통계 조회
     */
    public AdminStatsDto getStats() {
        long totalUsers = userRepository.count();
        long totalPosts = cheerPostRepository.count();
        long totalMates = partyRepository.count();

        log.info("📊 관리자 통계 - 유저: {}, 게시글: {}, 메이트: {}", 
            totalUsers, totalPosts, totalMates);

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
                search.trim()
            );
            log.info("🔍 유저 검색: '{}' - {}명 발견", search, users.size());
        } else {
            // 🔥 전체 조회 (ID 순)
            users = userRepository.findAllByOrderByIdAsc();
            log.info("👥 전체 유저 조회: {}명 (ID 순)", users.size());
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
        
        log.info("📝 전체 게시글 조회: {}개 (최신순)", posts.size());
        
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
            .title(post.getTitle())
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
        
        log.info("🎫 전체 메이트 조회: {}개 (최신순)", parties.size());
        
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
                : party.getDescription())  // 설명을 제목처럼 사용
            .stadium(party.getStadium())
            .gameDate(party.getGameDate())
            .currentMembers(party.getCurrentParticipants())
            .maxMembers(party.getMaxParticipants())
            .status(party.getStatus().name().toLowerCase())  // PENDING → pending
            .createdAt(party.getCreatedAt())
            .hostName(party.getHostName())
            .homeTeam(party.getHomeTeam())
            .awayTeam(party.getAwayTeam())
            .section(party.getSection())
            .build();
    }

    /**
     * 유저 삭제 (연관된 데이터도 함께 삭제)
     */
    @Transactional
    public void deleteUser(Long userId) {
        UserEntity user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("유저를 찾을 수 없습니다."));

        log.warn("🗑️ 유저 삭제 시작: userId={}, email={}", userId, user.getEmail());
        
        // 🔥 1. 좋아요 삭제 (가장 먼저!)
        List<CheerPostLike> userLikes = likeRepository.findByUser(user);
        if (!userLikes.isEmpty()) {
            log.info("❤️ 유저의 좋아요 {}개 삭제", userLikes.size());
            likeRepository.deleteAll(userLikes);
        }
        
        // 🔥 2. 댓글 삭제 (두 번째)
        List<CheerComment> userComments = commentRepository.findByAuthor(user);
        if (!userComments.isEmpty()) {
            log.info("💬 유저의 댓글 {}개 삭제", userComments.size());
            commentRepository.deleteAll(userComments);
        }
        
        // 🔥 3. 게시글 삭제 (세 번째)
        List<CheerPost> userPosts = cheerPostRepository.findByAuthor(user);
        if (!userPosts.isEmpty()) {
            log.info("📝 유저의 게시글 {}개 삭제", userPosts.size());
            cheerPostRepository.deleteAll(userPosts);
        }
        
        // 🔥 4. 메이트 모임 삭제 (네 번째)
        List<Party> userParties = partyRepository.findByHostId(userId);
        if (!userParties.isEmpty()) {
            log.info("🎫 유저의 메이트 모임 {}개 삭제", userParties.size());
            partyRepository.deleteAll(userParties);
        }
        
        // 🔥 5. 마지막으로 유저 삭제
        userRepository.delete(user);
        
        log.warn("✅ 유저 삭제 완료: userId={}", userId);
    }

    /**
     * 응원 게시글 삭제
     */
    @Transactional
    public void deletePost(Long postId) {
        if (!cheerPostRepository.existsById(postId)) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }

        log.warn("🗑️ 게시글 삭제: postId={}", postId);
        cheerPostRepository.deleteById(postId);
    }

    /**
     * 메이트 모임 삭제
     */
    @Transactional
    public void deleteMate(Long mateId) {
        if (!partyRepository.existsById(mateId)) {
            throw new IllegalArgumentException("메이트 모임을 찾을 수 없습니다.");
        }

        log.warn("🗑️ 메이트 삭제: mateId={}", mateId);
        partyRepository.deleteById(mateId);
    }

    /**
     * UserEntity → AdminUserDto 변환
     */
    private AdminUserDto convertToAdminUserDto(UserEntity user) {
        // 해당 유저의 게시글 수 조회
        long postCount = cheerPostRepository.countByUserId(user.getId());

        return AdminUserDto.builder()
            .id(user.getId())
            .email(user.getEmail())
            .name(user.getName())
            .favoriteTeam(user.getFavoriteTeam() != null ? user.getFavoriteTeam().getTeamId() : null)
            .createdAt(user.getCreatedAt())
            .postCount(postCount)
            .role(user.getRole())
            .build();
    }
}
