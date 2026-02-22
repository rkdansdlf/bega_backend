package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.dto.BookmarkResponse;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.dto.PostChangesResponse;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.QuoteRepostReq;
import com.example.cheerboard.dto.ReportCaseRes;
import com.example.cheerboard.dto.ReportRequest;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.storage.dto.PostImageDto;
import com.example.auth.entity.UserEntity;
import com.example.auth.service.BlockService;
import com.example.cheerboard.config.CurrentUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * CheerService Core Facade
 * - Delegates actual business logic to specialized services
 * - Maintains backward compatibility for Controller layer
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheerService {

    private final CheerPostService postService;
    private final CheerInteractionService interactionService;
    private final CheerFeedService feedService;
    private final CheerCommentService commentService;

    // Components retained for orchestration
    private final CurrentUser current;
    private final PostDtoMapper postDtoMapper;
    private final RedisPostService redisPostService;
    private final BlockService blockService;
    private final PermissionValidator permissionValidator;

    // --- Feed Operations ---

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> list(String teamId, String postTypeStr, Pageable pageable) {
        UserEntity me = current.getOrNull();
        return feedService.list(teamId, postTypeStr, pageable, me);
    }

    @Transactional(readOnly = true)
    public Page<PostLightweightSummaryRes> listLightweight(String teamId, String postTypeStr, Pageable pageable) {
        UserEntity me = current.getOrNull();
        return feedService.listLightweight(teamId, postTypeStr, pageable, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> search(String q, String teamId, Pageable pageable) {
        UserEntity me = current.getOrNull();
        return feedService.search(q, teamId, pageable, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getHotPosts(Pageable pageable) {
        return getHotPosts(pageable, PopularFeedAlgorithm.HYBRID.name());
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getHotPosts(Pageable pageable, String algorithmRaw) {
        UserEntity me = current.getOrNull();
        return feedService.getHotPosts(pageable, algorithmRaw, me);
    }

    @Transactional(readOnly = true)
    public PostChangesResponse checkPostChanges(Long sinceId, String teamId) {
        UserEntity me = current.getOrNull();
        return feedService.checkPostChanges(sinceId, teamId, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listFollowingPosts(Pageable pageable) {
        UserEntity me = current.get(); // Throws exception if not logged in (handled mostly by PreAuthorize)
        return feedService.listFollowingPosts(pageable, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> listByUserHandle(String handle, Pageable pageable) {
        UserEntity me = current.getOrNull();
        return feedService.listByUserHandle(handle, pageable, me);
    }

    @Transactional(readOnly = true)
    public Page<PostSummaryRes> getBookmarkedPosts(Pageable pageable) {
        UserEntity me = current.get();
        return feedService.getBookmarkedPosts(pageable, me);
    }

    // --- Post CRUD ---

    @Transactional
    public PostDetailRes createPost(CreatePostReq req) {
        UserEntity me = current.get();
        return postService.createPost(req, me);
    }

    @Transactional
    public PostDetailRes updatePost(Long id, UpdatePostReq req) {
        UserEntity me = current.get();
        CheerPost post = postService.updatePostEntity(id, req, me);
        // Reconstruct DTO with interaction status
        return reconstructPostDetailRes(post, me);
    }

    @Transactional
    public void deletePost(Long id) {
        UserEntity me = current.get();
        postService.deletePost(id, me);
    }

    @Transactional
    public PostDetailRes get(Long id) {
        UserEntity me = current.getOrNull();
        CheerPost post = postService.findPostById(id);

        if (me != null && blockService.hasBidirectionalBlock(me.getId(), post.getAuthor().getId())) {
            throw new IllegalStateException("차단된 사용자의 게시글은 조회할 수 없습니다.");
        }

        // Increase view count
        if (me == null || !post.getAuthor().getId().equals(me.getId())) {
            redisPostService.incrementViewCount(id, me != null ? me.getId() : null);
        }

        return reconstructPostDetailRes(post, me);
    }

    // --- Interactions ---

    @Transactional
    public LikeToggleResponse toggleLike(Long id) {
        UserEntity me = current.get();
        return interactionService.toggleLike(id, me);
    }

    @Transactional
    public BookmarkResponse toggleBookmark(Long id) {
        UserEntity me = current.get();
        return interactionService.toggleBookmark(id, me);
    }

    @Transactional
    public RepostToggleResponse toggleRepost(Long id) {
        UserEntity me = current.get();
        return postService.toggleRepost(id, me);
    }

    @Transactional
    public RepostToggleResponse cancelRepost(Long id) {
        UserEntity me = current.get();
        return postService.cancelRepost(id, me);
    }

    @Transactional
    public PostDetailRes createQuoteRepost(Long originalPostId, QuoteRepostReq req) {
        UserEntity me = current.get();
        CheerPost quoteRepost = postService.createQuoteRepost(originalPostId, req, me);
        return postDtoMapper.toNewPostDetailRes(quoteRepost, me);
    }

    @Transactional
    public ReportCaseRes reportPost(Long postId, ReportRequest req) {
        UserEntity me = current.get();
        return interactionService.reportPost(postId, req, me);
    }

    // --- Comments ---

    @Transactional(readOnly = true)
    public Page<CommentRes> listComments(Long postId, Pageable pageable) {
        UserEntity me = current.getOrNull();
        return commentService.listComments(postId, pageable, me);
    }

    @Transactional
    public CommentRes addComment(Long postId, CreateCommentReq req) {
        UserEntity me = current.get();
        return commentService.addComment(postId, req, me);
    }

    @Transactional
    public CommentRes addReply(Long postId, Long parentCommentId, CreateCommentReq req) {
        UserEntity me = current.get();
        return commentService.addReply(postId, parentCommentId, req, me);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        UserEntity me = current.get();
        commentService.deleteComment(commentId, me);
    }

    @Transactional
    public LikeToggleResponse toggleCommentLike(Long commentId) {
        UserEntity me = current.get();
        return interactionService.toggleCommentLike(commentId, me);
    }

    // --- Image ---

    @Transactional
    public List<String> uploadImages(Long postId, List<MultipartFile> files) {
        return postService.uploadImages(postId, files);
    }

    @Transactional
    public void updateHotScore(CheerPost post) {
        postService.updateHotScore(post);
    }

    @Transactional(readOnly = true)
    public List<PostImageDto> getPostImages(Long postId) {
        return postService.getPostImages(postId);
    }

    // --- Helpers ---

    private PostDetailRes reconstructPostDetailRes(CheerPost post, UserEntity me) {
        boolean liked = me != null && interactionService.isPostLikedByUser(post.getId(), me.getId());
        boolean isBookmarked = me != null && interactionService.isPostBookmarkedByUser(post.getId(), me.getId());
        boolean isOwner = me != null && permissionValidator.isOwnerOrAdmin(me, post.getAuthor());
        boolean repostedByMe = me != null && interactionService.isPostRepostedByUser(post.getId(), me.getId());
        int bookmarkCount = interactionService.getBookmarkCount(post.getId());

        return postDtoMapper.toPostDetailRes(post, liked, isBookmarked, isOwner, repostedByMe, bookmarkCount);
    }
}
