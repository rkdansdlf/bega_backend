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
        
        // 메이트 모임 삭제
        List<Party> userParties = partyRepository.findByHostId(userId);
        if (!userParties.isEmpty()) {
            partyRepository.deleteAll(userParties);
        }
        
        // 유저 삭제
        userRepository.delete(user);
    }

    /**
     * 응원 게시글 삭제
     */
    @Transactional
    public void deletePost(Long postId) {
        if (!cheerPostRepository.existsById(postId)) {
            throw new IllegalArgumentException("게시글을 찾을 수 없습니다.");
        }
        
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
