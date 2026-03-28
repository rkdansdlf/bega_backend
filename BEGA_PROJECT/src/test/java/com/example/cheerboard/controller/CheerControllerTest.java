package com.example.cheerboard.controller;

import com.example.cheerboard.dto.*;
import com.example.cheerboard.service.CheerBattleService;
import com.example.cheerboard.service.CheerService;
import com.example.cheerboard.storage.dto.PostImageDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheerControllerTest {

    @Mock
    private CheerService svc;

    @Mock
    private CheerBattleService battleService;

    @InjectMocks
    private CheerController controller;

    private final Pageable pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"));

    // ── list ──

    @Test
    @DisplayName("summary=false이면 전체 게시글 목록을 반환한다")
    void list_withSummaryFalse_returnsFullPage() {
        Page<PostSummaryRes> page = new PageImpl<>(List.of());
        when(svc.list(null, null, pageable)).thenReturn(page);

        ResponseEntity<?> result = controller.list(null, null, false, pageable);

        assertThat(result.getBody()).isEqualTo(page);
        verify(svc).list(null, null, pageable);
    }

    @Test
    @DisplayName("summary=true이면 경량 게시글 목록을 반환한다")
    void list_withSummaryTrue_returnsLightweightPage() {
        Page<PostLightweightSummaryRes> page = new PageImpl<>(List.of());
        when(svc.listLightweight(null, null, pageable)).thenReturn(page);

        ResponseEntity<?> result = controller.list(null, null, true, pageable);

        assertThat(result.getBody()).isEqualTo(page);
        verify(svc).listLightweight(null, null, pageable);
    }

    // ── listHot ──

    @Test
    @DisplayName("인기 게시글 목록을 반환한다")
    void listHot_returnsPage() {
        Page<PostSummaryRes> page = new PageImpl<>(List.of());
        when(svc.getHotPosts(pageable, "HYBRID")).thenReturn(page);

        Page<PostSummaryRes> result = controller.listHot("HYBRID", pageable);

        assertThat(result).isEqualTo(page);
    }

    // ── checkChanges ──

    @Test
    @DisplayName("게시글 변경사항을 조회한다")
    void checkChanges_returnsPostChanges() {
        PostChangesResponse resp = new PostChangesResponse(3, 100L);
        when(svc.checkPostChanges(100L, "KIA")).thenReturn(resp);

        PostChangesResponse result = controller.checkChanges(100L, "KIA");

        assertThat(result.newCount()).isEqualTo(3);
    }

    // ── listFollowing ──

    @Test
    @DisplayName("팔로잉 피드를 조회한다")
    void listFollowing_returnsPage() {
        Page<PostSummaryRes> page = new PageImpl<>(List.of());
        when(svc.listFollowingPosts(pageable)).thenReturn(page);

        Page<PostSummaryRes> result = controller.listFollowing(pageable);

        assertThat(result).isEqualTo(page);
    }

    // ── uploadImages ──

    @Test
    @DisplayName("이미지 업로드 시 201을 반환한다")
    void uploadImages_returns201() {
        List<MultipartFile> files = List.of(mock(MultipartFile.class));
        PostImageDto dto = new PostImageDto(1L, "/path", "image/png", 1024L, false, "http://url");
        when(svc.uploadImages(5L, files)).thenReturn(List.of(dto));

        ResponseEntity<List<PostImageDto>> result = controller.uploadImages(5L, files);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getBody()).hasSize(1);
    }

    // ── getImages ──

    @Test
    @DisplayName("게시글 이미지 목록을 반환한다")
    void getImages_returnsList() {
        PostImageDto dto = new PostImageDto(1L, "/path", "image/png", 1024L, false, "http://url");
        when(svc.getPostImages(5L)).thenReturn(List.of(dto));

        List<PostImageDto> result = controller.getImages(5L);

        assertThat(result).hasSize(1);
    }

    // ── search ──

    @Test
    @DisplayName("검색어로 게시글을 조회한다")
    void search_returnsPage() {
        Page<PostSummaryRes> page = new PageImpl<>(List.of());
        when(svc.search("KIA", null, pageable)).thenReturn(page);

        Page<PostSummaryRes> result = controller.search("KIA", null, pageable);

        assertThat(result).isEqualTo(page);
    }

    // ── get ──

    @Test
    @DisplayName("게시글 상세를 반환한다")
    void get_returnsPostDetail() {
        PostDetailRes detail = mock(PostDetailRes.class);
        when(svc.get(5L)).thenReturn(detail);

        PostDetailRes result = controller.get(5L);

        assertThat(result).isEqualTo(detail);
    }

    // ── create ──

    @Test
    @DisplayName("게시글 생성 시 상세를 반환한다")
    void create_returnsPostDetail() {
        CreatePostReq req = mock(CreatePostReq.class);
        PostDetailRes detail = mock(PostDetailRes.class);
        when(svc.createPost(req)).thenReturn(detail);

        PostDetailRes result = controller.create(req);

        assertThat(result).isEqualTo(detail);
    }

    // ── update ──

    @Test
    @DisplayName("게시글 수정 시 상세를 반환한다")
    void update_returnsPostDetail() {
        UpdatePostReq req = new UpdatePostReq("updated content");
        PostDetailRes detail = mock(PostDetailRes.class);
        when(svc.updatePost(5L, req)).thenReturn(detail);

        PostDetailRes result = controller.update(5L, req);

        assertThat(result).isEqualTo(detail);
    }

    // ── delete ──

    @Test
    @DisplayName("게시글 삭제 시 서비스를 호출한다")
    void delete_callsService() {
        controller.delete(5L);

        verify(svc).deletePost(5L);
    }

    // ── toggleLike ──

    @Test
    @DisplayName("좋아요 토글 시 결과를 반환한다")
    void toggleLike_returnsResponse() {
        LikeToggleResponse resp = new LikeToggleResponse(true, 10);
        when(svc.toggleLike(5L)).thenReturn(resp);

        LikeToggleResponse result = controller.toggleLike(5L);

        assertThat(result.liked()).isTrue();
        assertThat(result.likes()).isEqualTo(10);
    }

    // ── toggleBookmark ──

    @Test
    @DisplayName("북마크 토글 시 결과를 반환한다")
    void toggleBookmark_returnsResponse() {
        BookmarkResponse resp = new BookmarkResponse(true, 5);
        when(svc.toggleBookmark(5L)).thenReturn(resp);

        BookmarkResponse result = controller.toggleBookmark(5L);

        assertThat(result.bookmarked()).isTrue();
    }

    // ── toggleRepost ──

    @Test
    @DisplayName("리포스트 토글 시 결과를 반환한다")
    void toggleRepost_returnsResponse() {
        RepostToggleResponse resp = new RepostToggleResponse(true, 3);
        when(svc.toggleRepost(5L)).thenReturn(resp);

        RepostToggleResponse result = controller.toggleRepost(5L);

        assertThat(result.reposted()).isTrue();
    }

    // ── cancelRepost ──

    @Test
    @DisplayName("리포스트 취소 시 결과를 반환한다")
    void cancelRepost_returnsResponse() {
        RepostToggleResponse resp = new RepostToggleResponse(false, 2);
        when(svc.cancelRepost(5L)).thenReturn(resp);

        RepostToggleResponse result = controller.cancelRepost(5L);

        assertThat(result.reposted()).isFalse();
    }

    // ── createQuoteRepost ──

    @Test
    @DisplayName("인용 리포스트 생성 시 상세를 반환한다")
    void createQuoteRepost_returnsPostDetail() {
        QuoteRepostReq req = new QuoteRepostReq("my opinion");
        PostDetailRes detail = mock(PostDetailRes.class);
        when(svc.createQuoteRepost(5L, req)).thenReturn(detail);

        PostDetailRes result = controller.createQuoteRepost(5L, req);

        assertThat(result).isEqualTo(detail);
    }

    // ── getBookmarks ──

    @Test
    @DisplayName("북마크 목록을 반환한다")
    void getBookmarks_returnsPage() {
        Page<PostSummaryRes> page = new PageImpl<>(List.of());
        when(svc.getBookmarkedPosts(pageable)).thenReturn(page);

        Page<PostSummaryRes> result = controller.getBookmarks(pageable);

        assertThat(result).isEqualTo(page);
    }

    // ── reportPost ──

    @Test
    @DisplayName("게시글 신고 시 결과를 반환한다")
    void reportPost_returnsReportCaseRes() {
        ReportRequest req = new ReportRequest(null, "spam");
        ReportCaseRes res = new ReportCaseRes(1L, "PENDING", null, null, null);
        when(svc.reportPost(5L, req)).thenReturn(res);

        ResponseEntity<ReportCaseRes> result = controller.reportPost(5L, req);

        assertThat(result.getBody()).isEqualTo(res);
    }

    // ── comments ──

    @Test
    @DisplayName("댓글 목록을 반환한다")
    void comments_returnsPage() {
        Page<CommentRes> page = new PageImpl<>(List.of());
        when(svc.listComments(5L, pageable)).thenReturn(page);

        Page<CommentRes> result = controller.comments(5L, pageable);

        assertThat(result).isEqualTo(page);
    }

    // ── addComment ──

    @Test
    @DisplayName("댓글 작성 시 결과를 반환한다")
    void addComment_returnsCommentRes() {
        CreateCommentReq req = new CreateCommentReq("great post");
        CommentRes res = new CommentRes(1L, "author", null, null, "handle", "great post", null, 0, false, null);
        when(svc.addComment(5L, req)).thenReturn(res);

        CommentRes result = controller.addComment(5L, req);

        assertThat(result).isEqualTo(res);
    }

    // ── deleteComment ──

    @Test
    @DisplayName("댓글 삭제 시 서비스를 호출한다")
    void deleteComment_callsService() {
        controller.deleteComment(10L);

        verify(svc).deleteComment(10L);
    }

    // ── toggleCommentLike ──

    @Test
    @DisplayName("댓글 좋아요 토글 시 결과를 반환한다")
    void toggleCommentLike_returnsResponse() {
        LikeToggleResponse resp = new LikeToggleResponse(true, 5);
        when(svc.toggleCommentLike(10L)).thenReturn(resp);

        LikeToggleResponse result = controller.toggleCommentLike(10L);

        assertThat(result.liked()).isTrue();
    }

    // ── addReply ──

    @Test
    @DisplayName("답글 작성 시 결과를 반환한다")
    void addReply_returnsCommentRes() {
        CreateCommentReq req = new CreateCommentReq("reply");
        CommentRes res = new CommentRes(2L, "author", null, null, "handle", "reply", null, 0, false, null);
        when(svc.addReply(5L, 10L, req)).thenReturn(res);

        CommentRes result = controller.addReply(5L, 10L, req);

        assertThat(result).isEqualTo(res);
    }

    // ── listByUser ──

    @Test
    @DisplayName("사용자별 게시글 목록을 반환한다")
    void listByUser_returnsPage() {
        Page<PostSummaryRes> page = new PageImpl<>(List.of());
        when(svc.listByUserHandle("testuser", pageable)).thenReturn(page);

        Page<PostSummaryRes> result = controller.listByUser("testuser", pageable);

        assertThat(result).isEqualTo(page);
    }

    // ── getBattleStatus ──

    @Test
    @DisplayName("인증된 사용자의 배틀 상태를 조회하면 myVote가 포함된다")
    void getBattleStatus_withPrincipal_includesMyVote() {
        Principal principal = () -> "42";
        when(battleService.getGameStats("GAME001")).thenReturn(Map.of("KIA", 10, "LG", 5));
        when(battleService.getUserVote("GAME001", 42L)).thenReturn("KIA");

        CheerBattleStatusRes result = controller.getBattleStatus("GAME001", principal);

        assertThat(result.getStats()).containsEntry("KIA", 10);
        assertThat(result.getMyVote()).isEqualTo("KIA");
    }

    @Test
    @DisplayName("비인증 사용자의 배틀 상태 조회 시 myVote가 null이다")
    void getBattleStatus_withoutPrincipal_myVoteIsNull() {
        when(battleService.getGameStats("GAME001")).thenReturn(Map.of("KIA", 10));

        CheerBattleStatusRes result = controller.getBattleStatus("GAME001", null);

        assertThat(result.getMyVote()).isNull();
    }
}
