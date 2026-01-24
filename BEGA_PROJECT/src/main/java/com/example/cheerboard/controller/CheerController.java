package com.example.cheerboard.controller;

import com.example.cheerboard.dto.CreatePostReq;
import com.example.common.ratelimit.RateLimit;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.dto.RepostToggleResponse;
import com.example.cheerboard.dto.QuoteRepostReq;
import com.example.cheerboard.dto.BookmarkResponse;
import jakarta.validation.Valid;
import com.example.cheerboard.dto.ReportRequest;
import com.example.cheerboard.service.CheerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/cheer")
@RequiredArgsConstructor
public class CheerController {

    private final CheerService svc;
    private final com.example.cheerboard.service.CheerBattleService battleService;

    @GetMapping("/posts")
    public Page<PostSummaryRes> list(
            @RequestParam(required = false) String teamId,
            @RequestParam(required = false) String postType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return svc.list(teamId, postType, pageable);
    }

    @GetMapping("/posts/hot")
    public Page<PostSummaryRes> listHot(
            @PageableDefault(size = 20) Pageable pageable) {
        return svc.getHotPosts(pageable);
    }

    /**
     * 팔로우한 유저들의 게시글 조회 (팔로우 피드)
     */
    @GetMapping("/posts/following")
    @PreAuthorize("isAuthenticated()")
    public Page<PostSummaryRes> listFollowing(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return svc.listFollowingPosts(pageable);
    }

    @PostMapping(value = "/posts/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public java.util.List<String> uploadImages(
            @PathVariable Long id,
            @RequestPart("files") java.util.List<org.springframework.web.multipart.MultipartFile> images) {
        return svc.uploadImages(id, images);
    }

    @GetMapping("/posts/{id}/images")
    public java.util.List<com.example.cheerboard.storage.dto.PostImageDto> getImages(@PathVariable Long id) {
        return svc.getPostImages(id);
    }

    @GetMapping("/posts/search")
    public Page<PostSummaryRes> search(
            @RequestParam String q,
            @RequestParam(required = false) String teamId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return svc.search(q, teamId, pageable);
    }

    @GetMapping("/posts/{id}")
    public PostDetailRes get(@PathVariable Long id) {
        return svc.get(id);
    }

    @RateLimit(limit = 5, window = 60) // 1분에 최대 5개 게시글
    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailRes create(@RequestBody CreatePostReq req) {
        return svc.createPost(req);
    }

    @PutMapping("/posts/{id}")
    public PostDetailRes update(@PathVariable Long id, @RequestBody UpdatePostReq req) {
        return svc.updatePost(id, req);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        svc.deletePost(id);
    }

    @RateLimit(limit = 10, window = 60) // 1분에 최대 10번 좋아요 토글
    @PostMapping("/posts/{id}/like")
    public LikeToggleResponse toggleLike(@PathVariable Long id) {
        return svc.toggleLike(id);
    }

    @PostMapping("/posts/{id}/bookmark")
    public BookmarkResponse toggleBookmark(@PathVariable Long id) {
        return svc.toggleBookmark(id);
    }

    @RateLimit(limit = 10, window = 60) // 1분에 최대 10번 리포스트 토글
    @PostMapping("/posts/{id}/repost")
    public RepostToggleResponse toggleRepost(@PathVariable Long id) {
        return svc.toggleRepost(id);
    }

    /**
     * 인용 리포스트 생성
     * - 원글을 첨부하면서 의견(코멘트)을 덧붙여 작성
     * - 여러 번 가능 (토글 아님)
     */
    @RateLimit(limit = 5, window = 60) // 1분에 최대 5번 인용 리포스트
    @PostMapping("/posts/{id}/quote")
    @PreAuthorize("isAuthenticated()")
    public PostDetailRes createQuoteRepost(
            @PathVariable Long id,
            @Valid @RequestBody QuoteRepostReq req) {
        return svc.createQuoteRepost(id, req);
    }

    @GetMapping("/bookmarks")
    @PreAuthorize("isAuthenticated()")
    public Page<PostSummaryRes> getBookmarks(
            @PageableDefault(size = 20) Pageable pageable) {
        return svc.getBookmarkedPosts(pageable);
    }

    @RateLimit(limit = 3, window = 60) // 1분에 최대 3번 신고
    @PostMapping("/posts/{id}/report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> reportPost(
            @PathVariable Long id,
            @RequestBody ReportRequest req) {
        svc.reportPost(id, req);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/posts/{id}/comments")
    public Page<CommentRes> comments(@PathVariable Long id,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return svc.listComments(id, pageable);
    }

    @RateLimit(limit = 10, window = 60) // 1분에 최대 10개 댓글
    @PostMapping("/posts/{id}/comments")
    public CommentRes addComment(@PathVariable Long id, @RequestBody CreateCommentReq req) {
        return svc.addComment(id, req);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        svc.deleteComment(commentId);
    }

    @RateLimit(limit = 10, window = 60) // 1분에 최대 10번 댓글 좋아요 토글
    @PostMapping("/comments/{commentId}/like")
    public LikeToggleResponse toggleCommentLike(@PathVariable Long commentId) {
        return svc.toggleCommentLike(commentId);
    }

    @RateLimit(limit = 10, window = 60) // 1분에 최대 10개 답글
    @PostMapping("/posts/{postId}/comments/{parentCommentId}/replies")
    public CommentRes addReply(
            @PathVariable Long postId,
            @PathVariable Long parentCommentId,
            @RequestBody CreateCommentReq req) {
        return svc.addReply(postId, parentCommentId, req);
    }

    @GetMapping("/user/{handle}/posts")
    public Page<PostSummaryRes> listByUser(@PathVariable String handle,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return svc.listByUserHandle(handle, pageable);
    }

    @GetMapping("/battle/{gameId}/status")
    public com.example.cheerboard.dto.CheerBattleStatusRes getBattleStatus(
            @PathVariable String gameId,
            java.security.Principal principal) {

        java.util.Map<String, Integer> stats = battleService.getGameStats(gameId);
        String myVote = null;
        if (principal != null) {
            myVote = battleService.getUserVote(gameId, principal.getName());
        }

        return com.example.cheerboard.dto.CheerBattleStatusRes.builder()
                .stats(stats)
                .myVote(myVote)
                .build();
    }
}
