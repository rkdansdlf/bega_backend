package com.example.cheerboard.service;

import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerCommentLike;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.repo.CheerCommentLikeRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerTeamRepository;
import com.example.demo.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.example.cheerboard.service.CheerServiceConstants.*;

@Service
@RequiredArgsConstructor
public class CheerService {

    private final CheerPostRepo postRepo;
    private final CheerCommentRepo commentRepo;
    private final CheerPostLikeRepo likeRepo;
    private final CheerCommentLikeRepo commentLikeRepo;
    private final CheerTeamRepository teamRepo;
    private final CurrentUser current;
    
    // 리팩토링된 컴포넌트들
    private final PermissionValidator permissionValidator;
    private final PostDtoMapper postDtoMapper;
    private final HotPostChecker hotPostChecker;

    public Page<PostSummaryRes> list(String teamId, Pageable pageable) {
        if (teamId != null && !teamId.isBlank()) {
            UserEntity me = current.getOrNull();
            if (me == null) {
                throw new AuthenticationCredentialsNotFoundException("로그인 후 마이팀 게시판을 이용할 수 있습니다.");
            }
            permissionValidator.validateTeamAccess(me, teamId, "게시글 조회");
        }

        Page<CheerPost> page = postRepo.findAllOrderByPostTypeAndCreatedAt(teamId, pageable);
        
        return page.map(postDtoMapper::toPostSummaryRes);
    }

    @Transactional
    public PostDetailRes get(Long id) {
        UserEntity me = current.getOrNull();
        CheerPost post = findPostById(id);

        if (me != null) {
            permissionValidator.validateTeamAccess(me, post.getTeamId(), "게시글 상세보기");
        }

        increaseViewCount(post, me);

        boolean liked = me != null && isPostLikedByUser(id, me.getId());
        boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());

        return postDtoMapper.toPostDetailRes(post, liked, isOwner);
    }
    
    /**
     * 게시글 조회수 증가 (작성자가 아닌 경우에만)
     */
    private void increaseViewCount(CheerPost post, UserEntity user) {
        if (user == null || !post.getAuthor().getId().equals(user.getId())) {
            post.setViews(post.getViews() + 1);
            postRepo.save(post);
        }
    }
    
    /**
     * 게시글 ID로 게시글 조회
     */
    private CheerPost findPostById(Long postId) {
        return postRepo.findById(postId)
            .orElseThrow(() -> new java.util.NoSuchElementException("게시글을 찾을 수 없습니다: " + postId));
    }
    
    /**
     * 사용자가 게시글에 좋아요를 눌렀는지 확인
     */
    private boolean isPostLikedByUser(Long postId, Long userId) {
        return likeRepo.existsById(new CheerPostLike.Id(postId, userId));
    }

    @Transactional
    public PostDetailRes createPost(CreatePostReq req) {
        UserEntity me = current.get();
        permissionValidator.validateTeamAccess(me, req.teamId(), "게시글 작성");
        
        PostType postType = determinePostType(req, me);
        CheerPost post = buildNewPost(req, me, postType);
        CheerPost savedPost = postRepo.save(post);

        return postDtoMapper.toNewPostDetailRes(savedPost, me);
    }
    
    /**
     * 게시글 타입 결정 (공지사항 권한 체크 포함)
     */
    private PostType determinePostType(CreatePostReq req, UserEntity user) {
        if (req.postType() != null && NOTICE_POST_TYPE.equals(req.postType())) {
            permissionValidator.validateNoticePermission(user);
            return PostType.NOTICE;
        }
        return PostType.NORMAL;
    }
    
    /**
     * 새 게시글 엔티티 생성
     */
    private CheerPost buildNewPost(CreatePostReq req, UserEntity author, PostType postType) {
        System.out.println("🔍 buildNewPost - teamId: " + req.teamId());
        var team = teamRepo.findById(req.teamId())
            .orElseThrow(() -> new java.util.NoSuchElementException("팀을 찾을 수 없습니다: " + req.teamId()));
        System.out.println("✅ buildNewPost - Team found: " + team.getId());

        CheerPost post = CheerPost.builder()
            .author(author)
            .team(team)
            .title(req.title())
            .content(req.content())
            .imageUrls(req.images() != null ? new java.util.ArrayList<>(req.images()) : new java.util.ArrayList<>())
            .postType(postType)
            .build();

        System.out.println("🔍 buildNewPost - Post team: " + (post.getTeam() != null ? post.getTeam().getId() : "NULL"));
        return post;
    }

    @Transactional
    public PostDetailRes updatePost(Long id, UpdatePostReq req) {
        UserEntity me = current.get();
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "게시글 수정");

        updatePostContent(post, req);

        boolean liked = isPostLikedByUser(id, me.getId());
        return postDtoMapper.toPostDetailRes(post, liked, true);
    }
    
    /**
     * 게시글 내용 업데이트
     */
    private void updatePostContent(CheerPost post, UpdatePostReq req) {
        post.setTitle(req.title());
        post.setContent(req.content());
    }

    @Transactional
    public void deletePost(Long id) {
        UserEntity me = current.get();
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "게시글 삭제");
        
        // JPA cascade 옵션으로 관련 데이터 자동 삭제
        postRepo.delete(post);
    }

    @Transactional
    public LikeToggleResponse toggleLike(Long postId) {
        UserEntity me = current.get();
        CheerPost post = findPostById(postId);

        permissionValidator.validateTeamAccess(me, post.getTeamId(), "좋아요");

        CheerPostLike.Id likeId = new CheerPostLike.Id(post.getId(), me.getId());

        boolean liked;
        int likes;

        if (likeRepo.existsById(likeId)) {
            // 좋아요 취소
            likeRepo.deleteById(likeId);
            likes = Math.max(0, post.getLikeCount() - 1);
            post.setLikeCount(likes);
            liked = false;
        } else {
            // 좋아요 추가
            CheerPostLike like = new CheerPostLike();
            like.setId(likeId);
            like.setPost(post);
            like.setUser(me);
            likeRepo.save(like);
            likes = post.getLikeCount() + 1;
            post.setLikeCount(likes);
            liked = true;
        }

        postRepo.save(post);
        return new LikeToggleResponse(liked, likes);
    }

    public Page<CommentRes> listComments(Long postId, Pageable pageable) {
        // 최상위 댓글만 조회 (대댓글은 각 댓글의 replies에 포함됨)
        return commentRepo.findByPostIdAndParentCommentIsNullOrderByCreatedAtDesc(postId, pageable)
            .map(this::toCommentRes);
    }

    @Transactional
    public CommentRes addComment(Long postId, CreateCommentReq req) {
        UserEntity me = current.get();
        CheerPost post = findPostById(postId);
        permissionValidator.validateTeamAccess(me, post.getTeamId(), "댓글 작성");

        CheerComment comment = saveNewComment(post, me, req);
        incrementCommentCount(post);

        return toCommentRes(comment);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        UserEntity me = current.get();
        CheerComment comment = findCommentById(commentId);
        permissionValidator.validateOwnerOrAdmin(me, comment.getAuthor(), "댓글 삭제");

        CheerPost post = comment.getPost();
        commentRepo.delete(comment);
        decrementCommentCount(post);
    }
    
    /**
     * 댓글 ID로 댓글 조회
     */
    private CheerComment findCommentById(Long commentId) {
        return commentRepo.findById(commentId)
            .orElseThrow(() -> new java.util.NoSuchElementException("댓글을 찾을 수 없습니다: " + commentId));
    }
    
    /**
     * 새 댓글 저장
     */
    private CheerComment saveNewComment(CheerPost post, UserEntity author, CreateCommentReq req) {
        return commentRepo.save(CheerComment.builder()
            .post(post)
            .author(author)
            .content(req.content())
            .build());
    }
    
    /**
     * 게시글 댓글 수 증가
     */
    private void incrementCommentCount(CheerPost post) {
        post.setCommentCount(post.getCommentCount() + 1);
    }
    
    /**
     * 게시글 댓글 수 감소
     */
    private void decrementCommentCount(CheerPost post) {
        post.setCommentCount(Math.max(0, post.getCommentCount() - 1));
    }
    
    /**
     * CheerComment를 CommentRes로 변환
     */
    private CommentRes toCommentRes(CheerComment comment) {
        UserEntity me = current.getOrNull();
        boolean likedByMe = me != null && isCommentLikedByUser(comment.getId(), me.getId());

        // 대댓글 변환 (재귀적으로 처리)
        List<CommentRes> replies = comment.getReplies().stream()
            .map(this::toCommentRes)
            .collect(Collectors.toList());

        return new CommentRes(
            comment.getId(),
            resolveDisplayName(comment.getAuthor()),
            comment.getAuthor().getEmail(),
            comment.getAuthor().getFavoriteTeamId(),
            comment.getContent(),
            comment.getCreatedAt(),
            comment.getLikeCount(),
            likedByMe,
            replies
        );
    }

    /**
     * 사용자가 댓글에 좋아요를 눌렀는지 확인
     */
    private boolean isCommentLikedByUser(Long commentId, Long userId) {
        return commentLikeRepo.existsById(new CheerCommentLike.Id(commentId, userId));
    }

    private String resolveDisplayName(UserEntity user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return user.getEmail();
    }

    /**
     * 댓글 좋아요 토글
     */
    @Transactional
    public LikeToggleResponse toggleCommentLike(Long commentId) {
        UserEntity me = current.get();
        CheerComment comment = findCommentById(commentId);

        // 댓글이 속한 게시글의 팀 권한 확인
        permissionValidator.validateTeamAccess(me, comment.getPost().getTeamId(), "댓글 좋아요");

        CheerCommentLike.Id likeId = new CheerCommentLike.Id(comment.getId(), me.getId());

        boolean liked;
        int likes;

        if (commentLikeRepo.existsById(likeId)) {
            // 좋아요 취소
            commentLikeRepo.deleteById(likeId);
            likes = Math.max(0, comment.getLikeCount() - 1);
            comment.setLikeCount(likes);
            liked = false;
        } else {
            // 좋아요 추가
            CheerCommentLike like = new CheerCommentLike();
            like.setId(likeId);
            like.setComment(comment);
            like.setUser(me);
            commentLikeRepo.save(like);
            likes = comment.getLikeCount() + 1;
            comment.setLikeCount(likes);
            liked = true;
        }

        commentRepo.save(comment);
        return new LikeToggleResponse(liked, likes);
    }

    /**
     * 대댓글 작성
     */
    @Transactional
    public CommentRes addReply(Long postId, Long parentCommentId, CreateCommentReq req) {
        UserEntity me = current.get();
        CheerPost post = findPostById(postId);
        CheerComment parentComment = findCommentById(parentCommentId);

        // 부모 댓글이 해당 게시글에 속하는지 확인
        if (!parentComment.getPost().getId().equals(postId)) {
            throw new IllegalArgumentException("부모 댓글이 해당 게시글에 속하지 않습니다.");
        }

        permissionValidator.validateTeamAccess(me, post.getTeamId(), "대댓글 작성");

        CheerComment reply = saveNewReply(post, parentComment, me, req);
        incrementCommentCount(post);

        return toCommentRes(reply);
    }

    /**
     * 새 대댓글 저장
     */
    private CheerComment saveNewReply(CheerPost post, CheerComment parentComment, UserEntity author, CreateCommentReq req) {
        return commentRepo.save(CheerComment.builder()
            .post(post)
            .parentComment(parentComment)
            .author(author)
            .content(req.content())
            .build());
    }
}
