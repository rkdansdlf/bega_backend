package com.example.cheerboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.dto.PublicUserProfileDto;
import com.example.auth.service.BlockService;
import com.example.auth.service.FollowService;
import com.example.auth.service.PublicVisibilityVerifier;
import com.example.auth.service.UserService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostBookmark;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CheckinLinkedContentRes;
import com.example.cheerboard.dto.LinkedContentRes;
import com.example.cheerboard.dto.PostLightweightSummaryRes;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.repo.CheerBookmarkRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.service.ImageService;
import com.example.profile.storage.service.ProfileImageService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class CheerFeedServiceTestLinkedContent {

    private CheerPostRepo postRepo;
    private CheerLinkedPostService linkedPostService;
    private PostDtoMapper postDtoMapper;
    private CheerInteractionService interactionService;
    private ImageService imageService;
    private RedisPostService redisPostService;
    private ProfileImageService profileImageService;
    private CheerBookmarkRepo bookmarkRepo;
    private UserService userService;
    private PublicVisibilityVerifier publicVisibilityVerifier;
    private PopularFeedScoringService scoringService;
    private CheerFeedService feedService;

    @BeforeEach
    void setUp() {
        postRepo = mock(CheerPostRepo.class);
        linkedPostService = mock(CheerLinkedPostService.class);
        postDtoMapper = mock(PostDtoMapper.class);
        interactionService = mock(CheerInteractionService.class);
        imageService = mock(ImageService.class);
        redisPostService = mock(RedisPostService.class);
        profileImageService = mock(ProfileImageService.class);
        bookmarkRepo = mock(CheerBookmarkRepo.class);
        userService = mock(UserService.class);
        publicVisibilityVerifier = mock(PublicVisibilityVerifier.class);
        scoringService = mock(PopularFeedScoringService.class);
        feedService = new CheerFeedService(
                postRepo,
                interactionService,
                imageService,
                redisPostService,
                scoringService,
                mock(FollowService.class),
                mock(BlockService.class),
                publicVisibilityVerifier,
                userService,
                mock(PermissionValidator.class),
                postDtoMapper,
                profileImageService,
                bookmarkRepo,
                mock(CheerMonitoringMetricsService.class),
                linkedPostService);
        feedService.setFeedEnrichmentExecutorForTest(new DirectExecutorService());
        when(imageService.getPostImageUrlsByPostIds(anyList())).thenReturn(Collections.emptyMap());
        when(redisPostService.getViewCounts(anyCollection())).thenReturn(Collections.emptyMap());
        when(interactionService.getBookmarkCountMap(anyList())).thenReturn(Collections.emptyMap());
        when(interactionService.getLikedPostIds(any(Long.class), anyList())).thenReturn(Collections.emptySet());
        when(interactionService.getBookmarkedPostIds(any(Long.class), anyList())).thenReturn(Collections.emptySet());
        when(interactionService.getRepostedPostIds(any(Long.class), anyList())).thenReturn(Collections.emptySet());
        when(publicVisibilityVerifier.canAccess(any(), any())).thenReturn(true);
        when(scoringService.isHotEligible(any(CheerPost.class), anyInt(), any())).thenReturn(true);
        when(postDtoMapper.toPostSummaryRes(
                any(CheerPost.class), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(),
                anyList(), anyMap(), anyMap(), anyMap(), anyMap(), anyMap()))
                .thenAnswer(invocation -> PostSummaryRes.of(
                        invocation.<CheerPost>getArgument(0).getId(),
                        null, null, null, null, "content", "author", "author", null, null, null,
                        0, 0, 0, false, 0, false, false, false, 0, false, "NORMAL", List.of()));
    }

    @Test
    void list_resolvesTopLevelAndEmbeddedLinkedPostsInOneCall() {
        PageRequest pageable = PageRequest.of(0, 2);
        UserEntity author = UserEntity.builder().id(7L).name("author").handle("author").build();
        CheerPost embeddedOriginal = linkedPost(103L, author, PostType.CHECKIN);
        CheerPost first = linkedPost(101L, author, PostType.CHECKIN);
        first.setRepostOf(embeddedOriginal);
        first.setRepostType(CheerPost.RepostType.QUOTE);
        CheerPost second = linkedPost(102L, author, PostType.RECRUITMENT);
        LinkedContentRes linkedContent = LinkedContentRes.availableCheckin(new CheckinLinkedContentRes(
                LocalDate.of(2026, 7, 13), "LG", "KT", "LG", "Jamsil", true));

        when(postRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(first, second), pageable, 2));
        when(linkedPostService.resolveForPosts(anyCollection()))
                .thenReturn(Map.of(first.getId(), linkedContent, embeddedOriginal.getId(), linkedContent));
        assertThat(feedService.list(null, null, pageable, null).getContent()).hasSize(2);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Collection<CheerPost>> postsCaptor =
                org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(linkedPostService, times(1)).resolveForPosts(postsCaptor.capture());
        assertThat(postsCaptor.getValue())
                .extracting(CheerPost::getId)
                .containsExactly(101L, 102L, 103L);
    }

    @Test
    void listLightweight_resolvesLinkedContentOnceForTheCollection() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().id(8L).name("author").handle("author").build();
        CheerPost post = linkedPost(201L, author, PostType.CHECKIN);
        LinkedContentRes linkedContent = LinkedContentRes.availableCheckin(new CheckinLinkedContentRes(
                LocalDate.of(2026, 7, 13), "LG", "KT", "LG", "Jamsil", true));

        when(postRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(linkedPostService.resolveForPosts(anyCollection())).thenReturn(Map.of(post.getId(), linkedContent));
        when(postDtoMapper.toPostLightweightSummaryRes(
                any(CheerPost.class), anyList(), anyMap(), anyMap()))
                .thenReturn(PostLightweightSummaryRes.of(
                        post.getId(), post.getContent(), null, 0, 0, null, "author", null,
                        "CHECKIN", linkedContent));

        assertThat(feedService.listLightweight(null, null, pageable, null).getContent().getFirst().linkedContent())
                .isEqualTo(linkedContent);
        verify(linkedPostService, times(1)).resolveForPosts(anyCollection());
    }

    @Test
    void listLightweight_fallbackKeepsTopLevelLinkedContentAfterResolverSuccess() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().id(13L).name("author").handle("author").build();
        CheerPost post = linkedPost(501L, author, PostType.CHECKIN);
        LinkedContentRes linkedContent = LinkedContentRes.availableCheckin(new CheckinLinkedContentRes(
                LocalDate.of(2026, 7, 13), "LG", "KT", "LG", "Jamsil", true));

        when(postRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(linkedPostService.resolveForPosts(anyCollection())).thenReturn(Map.of(post.getId(), linkedContent));
        when(postDtoMapper.toPostLightweightSummaryRes(
                any(CheerPost.class), anyList(), anyMap(), anyMap()))
                .thenThrow(new IllegalStateException("later lightweight mapping failure"));

        PostLightweightSummaryRes result =
                feedService.listLightweight(null, null, pageable, null).getContent().getFirst();

        assertThat(result.postType()).isEqualTo("CHECKIN");
        assertThat(result.linkedContent()).isEqualTo(linkedContent);
        verify(linkedPostService, times(1)).resolveForPosts(anyCollection());
    }

    @Test
    void list_fallbackKeepsTopLevelAndImmediateOriginalLinkedContentAfterResolverSuccess() {
        PageRequest pageable = PageRequest.of(0, 2);
        UserEntity author = UserEntity.builder().id(14L).name("author").handle("author").build();
        CheerPost checkin = linkedPost(601L, author, PostType.CHECKIN);
        CheerPost original = linkedPost(602L, author, PostType.CHECKIN);
        CheerPost quote = linkedPost(603L, author, PostType.NORMAL);
        quote.setRepostOf(original);
        quote.setRepostType(CheerPost.RepostType.QUOTE);
        LinkedContentRes checkinContent = LinkedContentRes.availableCheckin(new CheckinLinkedContentRes(
                LocalDate.of(2026, 7, 13), "LG", "KT", "LG", "Jamsil", true));
        LinkedContentRes originalContent = LinkedContentRes.availableCheckin(new CheckinLinkedContentRes(
                LocalDate.of(2026, 7, 14), "KT", "LG", "KT", "Suwon", true));

        when(postRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(checkin, quote), pageable, 2));
        when(linkedPostService.resolveForPosts(anyCollection()))
                .thenReturn(Map.of(checkin.getId(), checkinContent, original.getId(), originalContent));
        when(postDtoMapper.toPostSummaryRes(
                any(CheerPost.class), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(),
                anyList(), anyMap(), anyMap(), anyMap(), anyMap(), anyMap()))
                .thenThrow(new IllegalStateException("later full mapping failure"));

        List<PostSummaryRes> results = feedService.list(null, null, pageable, null).getContent();
        PostSummaryRes checkinResult = results.getFirst();
        PostSummaryRes quoteResult = results.get(1);

        assertThat(checkinResult.postType()).isEqualTo("CHECKIN");
        assertThat(checkinResult.linkedContent()).isEqualTo(checkinContent);
        assertThat(quoteResult.originalDeleted()).isFalse();
        assertThat(quoteResult.originalPost()).isNotNull();
        assertThat(quoteResult.originalPost().id()).isEqualTo(original.getId());
        assertThat(quoteResult.originalPost().postType()).isEqualTo("CHECKIN");
        assertThat(quoteResult.originalPost().linkedContent()).isEqualTo(originalContent);
        verify(linkedPostService, times(1)).resolveForPosts(anyCollection());
    }

    @Test
    void list_propagatesUnexpectedLinkedSourceDatabaseFailure() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().id(9L).name("author").handle("author").build();
        CheerPost post = linkedPost(301L, author, PostType.RECRUITMENT);
        IllegalStateException databaseFailure = new IllegalStateException("linked source database unavailable");

        when(postRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(linkedPostService.resolveForPosts(anyCollection())).thenThrow(databaseFailure);

        assertThatThrownBy(() -> feedService.list(null, null, pageable, null)).isSameAs(databaseFailure);
        verify(postDtoMapper, times(0)).toPostSummaryRes(
                any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean(), anyInt(),
                anyList(), anyMap(), anyMap(), anyMap(), anyMap(), anyMap());
    }

    @Test
    void bookmarks_resolveLinkedContentThroughTheSharedCollectionMapper() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity me = UserEntity.builder().id(10L).name("viewer").handle("viewer").build();
        CheerPost post = linkedPost(401L, me, PostType.CHECKIN);
        CheerPostBookmark bookmark = new CheerPostBookmark();
        bookmark.setPost(post);
        when(bookmarkRepo.findVisibleByUserIdOrderByCreatedAtDesc(me.getId(), me.getId(), pageable))
                .thenReturn(new PageImpl<>(List.of(bookmark), pageable, 1));
        when(linkedPostService.resolveForPosts(anyCollection())).thenReturn(Collections.emptyMap());

        assertThat(feedService.getBookmarkedPosts(pageable, me).getContent()).hasSize(1);
        verify(linkedPostService, times(1)).resolveForPosts(anyCollection());
    }

    @Test
    void profile_resolvesLinkedContentThroughTheSharedCollectionMapper() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().id(11L).name("profile").handle("profile").build();
        CheerPost post = linkedPost(402L, author, PostType.RECRUITMENT);
        when(userService.getPublicUserProfileByHandle("profile", null))
                .thenReturn(PublicUserProfileDto.builder().handle("profile").build());
        when(postRepo.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(post), pageable, 1));
        when(linkedPostService.resolveForPosts(anyCollection())).thenReturn(Collections.emptyMap());

        assertThat(feedService.listByUserHandle("profile", pageable, null).getContent()).hasSize(1);
        verify(linkedPostService, times(1)).resolveForPosts(anyCollection());
    }

    @Test
    void hotFeed_resolvesLinkedContentThroughTheSharedCollectionMapper() {
        PageRequest pageable = PageRequest.of(0, 1);
        UserEntity author = UserEntity.builder().id(12L).name("hot").handle("hot").build();
        CheerPost post = linkedPost(403L, author, PostType.CHECKIN);
        LinkedHashSet<Long> hotIds = new LinkedHashSet<>(List.of(post.getId()));
        when(redisPostService.getHotListSize(PopularFeedAlgorithm.TIME_DECAY)).thenReturn(1L);
        when(redisPostService.getHotPostIds(0, 0, PopularFeedAlgorithm.TIME_DECAY)).thenReturn(hotIds);
        when(postRepo.findAllByIdWithGraph(hotIds)).thenReturn(List.of(post));
        when(linkedPostService.resolveForPosts(anyCollection())).thenReturn(Collections.emptyMap());

        assertThat(feedService.getHotPosts(pageable, "TIME_DECAY", null).getContent()).hasSize(1);
        verify(linkedPostService, times(1)).resolveForPosts(anyCollection());
    }

    private CheerPost linkedPost(Long id, UserEntity author, PostType postType) {
        return CheerPost.builder()
                .id(id)
                .author(author)
                .postType(postType)
                .content("content-" + id)
                .build();
    }

    private static final class DirectExecutorService extends AbstractExecutorService {
        private final AtomicInteger executions = new AtomicInteger();
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            executions.incrementAndGet();
            command.run();
        }
    }
}
