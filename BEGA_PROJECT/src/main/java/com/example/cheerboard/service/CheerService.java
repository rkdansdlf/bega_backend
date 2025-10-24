package com.example.cheerboard.service;

import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.*;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.example.cheerboard.service.CheerServiceConstants.*;

@Service
@RequiredArgsConstructor
public class CheerService {

    private final CheerPostRepo postRepo;
    private final CheerCommentRepo commentRepo;
    private final CheerPostLikeRepo likeRepo;
    private final AppUserRepo userRepo;
    private final CurrentUser current;
    
    // 리팩토링된 컴포넌트들
    private final PermissionValidator permissionValidator;
    private final PostDtoMapper postDtoMapper;
    private final HotPostChecker hotPostChecker;

    public Page<PostSummaryRes> list(String teamId, Pageable pageable) {
        // 공지사항이 항상 상단에 오도록 정렬 (NOTICE > NORMAL 순서)
        Page<CheerPost> page = postRepo.findAllOrderByPostTypeAndCreatedAt(teamId, pageable);
        
        return page.map(postDtoMapper::toPostSummaryRes);
    }

    @Transactional
    public PostDetailRes get(Long id) {
        AppUser me = current.get();
        CheerPost post = findPostById(id);
        
        permissionValidator.validateTeamAccess(me, post.getTeamId(), "게시글 상세보기");
        
        increaseViewCount(post, me);
        
        boolean liked = isPostLikedByUser(id, me.getId());
        boolean isOwner = permissionValidator.isOwnerOrAdmin(me, post.getAuthor());

        return postDtoMapper.toPostDetailRes(post, liked, isOwner);
    }
    
    /**
     * 게시글 조회수 증가 (작성자가 아닌 경우에만)
     */
    private void increaseViewCount(CheerPost post, AppUser user) {
        if (!post.getAuthor().getId().equals(user.getId())) {
            post.setViews(post.getViews() + 1);
            postRepo.save(post);
        }
    }
    
    /**
     * 게시글 ID로 게시글 조회
     */
    private CheerPost findPostById(Long postId) {
        return postRepo.findById(postId)
            .orElseThrow(() -> new IllegalArgumentException("게시글을 찾을 수 없습니다: " + postId));
    }
    
    /**
     * 사용자가 게시글에 좋아요를 눌렀는지 확인
     */
    private boolean isPostLikedByUser(Long postId, Long userId) {
        return likeRepo.existsById(new CheerPostLike.Id(postId, userId));
    }

    @Transactional
    public PostDetailRes createPost(CreatePostReq req) {
        AppUser me = current.get();
        permissionValidator.validateTeamAccess(me, req.teamId(), "게시글 작성");
        
        PostType postType = determinePostType(req, me);
        CheerPost post = buildNewPost(req, me, postType);
        CheerPost savedPost = postRepo.save(post);

        return postDtoMapper.toNewPostDetailRes(savedPost, me);
    }
    
    /**
     * 게시글 타입 결정 (공지사항 권한 체크 포함)
     */
    private PostType determinePostType(CreatePostReq req, AppUser user) {
        if (req.postType() != null && NOTICE_POST_TYPE.equals(req.postType())) {
            permissionValidator.validateNoticePermission(user);
            return PostType.NOTICE;
        }
        return PostType.NORMAL;
    }
    
    /**
     * 새 게시글 엔티티 생성
     */
    private CheerPost buildNewPost(CreatePostReq req, AppUser author, PostType postType) {
        return CheerPost.builder()
            .author(author)
            .teamId(req.teamId())
            .title(req.title())
            .content(req.content())
            .imageUrls(req.images() != null ? new java.util.ArrayList<>(req.images()) : new java.util.ArrayList<>())
            .postType(postType)
            .build();
    }

    @Transactional
    public PostDetailRes updatePost(Long id, UpdatePostReq req) {
        AppUser me = current.get();
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
        AppUser me = current.get();
        CheerPost post = findPostById(id);
        permissionValidator.validateOwnerOrAdmin(me, post.getAuthor(), "게시글 삭제");
        
        // JPA cascade 옵션으로 관련 데이터 자동 삭제
        postRepo.delete(post);
    }

    @Transactional
    public boolean toggleLike(Long postId) {
        AppUser me = current.get();
        CheerPost post = findPostById(postId);
        
        permissionValidator.validateTeamAccess(me, post.getTeamId(), "좋아요");

        CheerPostLike.Id likeId = new CheerPostLike.Id(post.getId(), me.getId());

        if (likeRepo.existsById(likeId)) {
            return removeLike(likeId, post);
        } else {
            return addLike(likeId, post, me);
        }
    }
    
    /**
     * 좋아요 추가
     */
    private boolean addLike(CheerPostLike.Id likeId, CheerPost post, AppUser user) {
        CheerPostLike like = new CheerPostLike();
        like.setId(likeId);
        like.setPost(post);
        like.setUser(user);
        likeRepo.save(like);
        post.setLikeCount(post.getLikeCount() + 1);
        return true;
    }
    
    /**
     * 좋아요 제거
     */
    private boolean removeLike(CheerPostLike.Id likeId, CheerPost post) {
        likeRepo.deleteById(likeId);
        post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        return false;
    }

    public Page<CommentRes> listComments(Long postId, Pageable pageable) {
        return commentRepo.findByPostIdOrderByCreatedAtDesc(postId, pageable)
            .map(this::toCommentRes);
    }

    @Transactional
    public CommentRes addComment(Long postId, CreateCommentReq req) {
        AppUser me = current.get();
        CheerPost post = findPostById(postId);
        permissionValidator.validateTeamAccess(me, post.getTeamId(), "댓글 작성");

        CheerComment comment = saveNewComment(post, me, req);
        incrementCommentCount(post);

        return toCommentRes(comment);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        AppUser me = current.get();
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
            .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다: " + commentId));
    }
    
    /**
     * 새 댓글 저장
     */
    private CheerComment saveNewComment(CheerPost post, AppUser author, CreateCommentReq req) {
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
        return new CommentRes(
            comment.getId(),
            comment.getAuthor().getDisplayName(),
            comment.getAuthor().getEmail(),
            comment.getAuthor().getFavoriteTeamId(),
            comment.getContent(),
            comment.getCreatedAt()
        );
    }
}