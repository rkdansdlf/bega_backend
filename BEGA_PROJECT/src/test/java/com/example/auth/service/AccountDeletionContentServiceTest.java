package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.LegacyPostImageHoldRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.retention.ReportEvidenceMediaRetention;
import com.example.cheerboard.retention.ReportEvidenceMediaRetentionRepository;
import com.example.cheerboard.retention.ReportEvidenceMediaStatus;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.service.ImageService;
import com.example.media.service.AccountCheerAssetVerifier;
import com.example.media.service.MediaUploadService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionContentServiceTest {

    @Mock
    private CheerPostRepo cheerPostRepo;

    @Mock
    private CheerCommentRepo cheerCommentRepo;

    @Mock
    private CheerReportRepo cheerReportRepo;

    @Mock
    private MediaUploadService mediaUploadService;

    @Mock
    private ImageService imageService;

    @Mock private LegacyPostImageHoldRepository legacyImageHolds;
    @Mock private ReportEvidenceMediaRetentionRepository evidenceRetention;
    @Mock private StorageConfig storageConfig;
    @Mock private AccountCheerAssetVerifier cheerAssetVerifier;

    @InjectMocks
    private AccountDeletionContentService service;

    @BeforeEach
    void ownedManagedImagesAreVerified() {
        // Default to verified; the managed-image rule is covered by the integration tests with real SQL.
        lenient().when(cheerAssetVerifier.allManagedImagesHaveOwnedLinks(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyCollection())).thenReturn(true);
    }

    @Test
    void preservesAllAccountContentAndMediaWhileAnyModerationCaseRemainsOpen() {
        CheerPost post = CheerPost.builder().id(101L).content("identifying post").sourceUrl("private pointer")
                .images(new ArrayList<>()).build();
        CheerPost protectedPost = CheerPost.builder().id(102L).content("unresolved evidence").build();
        when(cheerReportRepo.findOpenModerationPostIdsByAuthorId(7L)).thenReturn(Set.of(102L));
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L))
                .thenReturn(List.of(post, protectedPost));

        service.anonymize(7L);

        assertThat(post.getContent()).isEqualTo("identifying post");
        assertThat(post.getSourceUrl()).isEqualTo("private pointer");
        assertThat(post.getImages()).isEmpty();
        assertThat(protectedPost.getContent()).isEqualTo("unresolved evidence");
        verify(cheerCommentRepo, never()).findAllByAuthor_Id(7L);
        // Moderation is checked first and returns before the purge check, so the media service is never called.
        verify(mediaUploadService, never()).unlinkEntityMedia(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        verify(mediaUploadService, never()).deleteOwnedAssetsForAccount(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anySet());
    }

    @Test
    void defersAllContentWhilePostPurgeHasStarted() {
        CheerPost post = CheerPost.builder().id(101L).content("original")
                .purgeStartedAt(java.time.LocalDateTime.now()).build();
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));
        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.PURGE_PENDING);
        assertThat(post.getContent()).isEqualTo("original");
        verifyNoInteractions(mediaUploadService);
    }

    @Test
    void reportsModerationPendingBeforePurgeCheckWhenPostPurgeHasStartedUnderOpenCase() {
        CheerPost post = CheerPost.builder().id(101L).content("original")
                .purgeStartedAt(java.time.LocalDateTime.now()).build();
        when(cheerReportRepo.findOpenModerationPostIdsByAuthorId(7L)).thenReturn(Set.of(102L));
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));

        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.MODERATION_PENDING);
        assertThat(post.getContent()).isEqualTo("original");
        verifyNoInteractions(mediaUploadService);
        verify(cheerCommentRepo, never()).findAllByAuthor_Id(7L);
    }

    @Test
    void defersWhenOwnedAssetIsReservedByAnotherAuthorsPostPurge() {
        CheerPost post = CheerPost.builder().id(101L).content("original").build();
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));
        when(mediaUploadService.hasPendingCheerPurge(7L)).thenReturn(true);

        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.PURGE_PENDING);
        assertThat(post.getContent()).isEqualTo("original");
        verify(mediaUploadService, never()).deleteOwnedAssetsForAccount(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anySet());
        verify(cheerCommentRepo, never()).findAllByAuthor_Id(7L);
    }

    @Test
    void reportsAssetCleanupPendingWhenAnOwnedAssetCouldNotBeClaimed() {
        CheerPost post = CheerPost.builder().id(101L).content("original").build();
        when(cheerReportRepo.findOpenModerationPostIdsByAuthorId(7L)).thenReturn(Set.of());
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));
        when(mediaUploadService.deleteOwnedAssetsForAccount(7L, Set.of())).thenReturn(1);

        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.ASSET_CLEANUP_PENDING);
    }

    @Test
    void reportedPostMediaIsProtectedFromAccountCleanupWhileTextIsRedacted() {
        CheerPost post = CheerPost.builder().id(101L).content("evidence").sourceUrl("private pointer")
                .images(new ArrayList<>()).build();
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));
        when(cheerPostRepo.existsReportByPostId(101L)).thenReturn(true);

        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.COMPLETED);

        assertThat(post.getContent()).isEqualTo("게시글이 비식별 처리되었습니다.");
        assertThat(post.getSourceUrl()).isNull();
        verify(mediaUploadService, never()).unlinkEntityMedia(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyLong());
        verify(mediaUploadService).deleteOwnedAssetsForAccount(7L, Set.of(101L));
        verify(imageService).evictPostImageUrlCache(101L);
    }

    @Test
    void reportedImageIsRegisteredForRetentionBeforeItsRowIsCleared() {
        LocalDateTime requestedAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        UserEntity owner = UserEntity.builder().id(7L).firstDeletionRequestedAt(requestedAt).build();
        PostImage image = PostImage.builder().id(55L).storagePath("media/cheer/evidence.webp").build();
        CheerPost post = CheerPost.builder().id(101L).author(owner).images(new ArrayList<>(List.of(image))).build();
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));
        when(cheerPostRepo.existsReportByPostId(101L)).thenReturn(true);
        AtomicInteger imagesWhenSaved = new AtomicInteger(-1);
        org.mockito.Mockito.doAnswer(invocation -> {
            imagesWhenSaved.set(post.getImages().size());
            return invocation.getArgument(0);
        }).when(evidenceRetention).save(org.mockito.ArgumentMatchers.any());

        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.COMPLETED);

        ArgumentCaptor<ReportEvidenceMediaRetention> saved = ArgumentCaptor.forClass(ReportEvidenceMediaRetention.class);
        verify(evidenceRetention).save(saved.capture());
        assertThat(imagesWhenSaved.get()).isEqualTo(1);
        assertThat(post.getImages()).isEmpty();
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportEvidenceMediaStatus.ACTIVE);
        assertThat(saved.getValue().getPostImageId()).isEqualTo(55L);
        assertThat(saved.getValue().getBaseRequestedAt()).isEqualTo(requestedAt);
    }

    @Test
    void reportedImageWithoutDeletionRequestDateGoesToManualReview() {
        UserEntity owner = UserEntity.builder().id(7L).build();
        PostImage image = PostImage.builder().id(56L).storagePath("media/cheer/immediate.webp").build();
        CheerPost post = CheerPost.builder().id(102L).author(owner).images(new ArrayList<>(List.of(image))).build();
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));
        when(cheerPostRepo.existsReportByPostId(102L)).thenReturn(true);

        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.COMPLETED);

        ArgumentCaptor<ReportEvidenceMediaRetention> saved = ArgumentCaptor.forClass(ReportEvidenceMediaRetention.class);
        verify(evidenceRetention).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(ReportEvidenceMediaStatus.REVIEW_REQUIRED);
        assertThat(saved.getValue().getBaseRequestedAt()).isNull();
    }

    @Test
    void unverifiedManagedImagesHoldTheAccountWithoutClearingOrUnlinking() {
        PostImage image = PostImage.builder().id(57L).storagePath("media/cheer/7/unverified.webp").build();
        CheerPost post = CheerPost.builder().id(103L).content("original").images(new ArrayList<>(List.of(image))).build();
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L)).thenReturn(List.of(post));
        when(cheerAssetVerifier.allManagedImagesHaveOwnedLinks(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyCollection())).thenReturn(false);

        assertThat(service.anonymize(7L)).isEqualTo(AccountDeletionContentService.Result.PURGE_PENDING);
        assertThat(post.getContent()).isEqualTo("original");
        assertThat(post.getImages()).containsExactly(image);
        verify(mediaUploadService, never()).unlinkEntityMedia(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(mediaUploadService, never()).deleteOwnedAssetsForAccount(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anySet());
    }
}
