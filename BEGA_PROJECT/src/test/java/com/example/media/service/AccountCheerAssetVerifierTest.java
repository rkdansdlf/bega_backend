package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountCheerAssetVerifierTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final Long POST_ID = 101L;
    private static final Long ASSET_ID = 55L;
    private static final String PATH = "media/cheer/7/synthetic-image.webp";

    @Mock
    private MediaAssetRepository mediaAssets;

    @Mock
    private MediaAssetLinkRepository mediaAssetLinks;

    private AccountCheerAssetVerifier verifier;

    @BeforeEach
    void createVerifier() {
        verifier = new AccountCheerAssetVerifier(mediaAssets, mediaAssetLinks);
    }

    @Test
    void readyAssetOwnedAndLinkedToThePostIsBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.READY);
        when(mediaAssetLinks.countCheerPostLink(ASSET_ID, POST_ID)).thenReturn(1L);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isTrue();
    }

    @Test
    void reservedAssetOfAReportedPostIsStillBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.CHEER_PURGE_PENDING);
        when(mediaAssetLinks.countCheerPostLink(ASSET_ID, POST_ID)).thenReturn(1L);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isTrue();
    }

    @Test
    void assetOwnedByAnotherUserIsNotBacked() {
        stubAsset(OTHER_USER_ID, MediaDomain.CHEER, MediaAssetStatus.READY);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        // The owner check fails first, so the link is never consulted.
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void reservedAssetOwnedByAnotherUserIsNotBacked() {
        stubAsset(OTHER_USER_ID, MediaDomain.CHEER, MediaAssetStatus.CHEER_PURGE_PENDING);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void assetOfAnotherDomainIsNotBacked() {
        stubAsset(USER_ID, MediaDomain.PROFILE, MediaAssetStatus.READY);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void missingAssetIsNotBacked() {
        when(mediaAssets.findByObjectKey(PATH)).thenReturn(Optional.empty());

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void assetWithoutAnyLinkIsNotBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.READY);
        when(mediaAssetLinks.countCheerPostLink(ASSET_ID, POST_ID)).thenReturn(0L);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
    }

    @Test
    void deletedAssetIsNotBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.DELETED);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void orphanedAssetIsNotBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.ORPHANED);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void assetWithNoOwnerIsNotBackedEvenWhenTheAccountIdIsNull() {
        // Equal nulls must not count as ownership: the null guard is what fails this case.
        when(mediaAssets.findByObjectKey(PATH)).thenReturn(Optional.of(asset(null, MediaDomain.CHEER,
                MediaAssetStatus.READY)));

        assertThat(verifier.allManagedImagesHaveOwnedLinks(null, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void pendingAssetIsNotBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.PENDING);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void finalizingAssetIsNotBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.FINALIZING);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void missingAccountIdIsNotBacked() {
        stubAsset(USER_ID, MediaDomain.CHEER, MediaAssetStatus.READY);

        assertThat(verifier.allManagedImagesHaveOwnedLinks(null, List.of(post(PATH)))).isFalse();
        verifyNoInteractions(mediaAssetLinks);
    }

    @Test
    void legacyPathIsSkippedWithoutAnyLookup() {
        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post("posts/legacy/old.webp")))).isTrue();
        verifyNoInteractions(mediaAssets, mediaAssetLinks);
    }

    @Test
    void otherMediaDomainPathIsSkippedWithoutAnyLookup() {
        // A profile path is not a managed cheer path, so the cheer verifier leaves it alone.
        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID,
                List.of(post("media/profile/" + USER_ID + "/avatar.webp")))).isTrue();
        verifyNoInteractions(mediaAssets, mediaAssetLinks);
    }

    @Test
    void postWithoutImagesPasses() {
        CheerPost noImages = CheerPost.builder().id(POST_ID).images(null).build();

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(noImages))).isTrue();
        verifyNoInteractions(mediaAssets, mediaAssetLinks);
    }

    @Test
    void noPostsPasses() {
        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of())).isTrue();
        verifyNoInteractions(mediaAssets, mediaAssetLinks);
    }

    @Test
    void oneUnbackedManagedImageFailsTheWholeAccount() {
        String otherPath = "media/cheer/7/second-image.webp";
        when(mediaAssets.findByObjectKey(PATH)).thenReturn(Optional.of(asset(USER_ID, MediaDomain.CHEER,
                MediaAssetStatus.READY)));
        when(mediaAssetLinks.countCheerPostLink(ASSET_ID, POST_ID)).thenReturn(1L);
        when(mediaAssets.findByObjectKey(otherPath)).thenReturn(Optional.empty());

        CheerPost post = CheerPost.builder().id(POST_ID)
                .images(new ArrayList<>(List.of(image(PATH), image(otherPath)))).build();

        assertThat(verifier.allManagedImagesHaveOwnedLinks(USER_ID, List.of(post))).isFalse();
    }

    private void stubAsset(Long ownerId, MediaDomain domain, MediaAssetStatus status) {
        when(mediaAssets.findByObjectKey(PATH)).thenReturn(Optional.of(asset(ownerId, domain, status)));
    }

    private static MediaAsset asset(Long ownerId, MediaDomain domain, MediaAssetStatus status) {
        return MediaAsset.builder()
                .id(ASSET_ID)
                .ownerUserId(ownerId)
                .domain(domain)
                .status(status)
                .objectKey(PATH)
                .build();
    }

    private static CheerPost post(String path) {
        return CheerPost.builder().id(POST_ID).images(new ArrayList<>(List.of(image(path)))).build();
    }

    private static PostImage image(String path) {
        return PostImage.builder().storagePath(path).build();
    }
}
