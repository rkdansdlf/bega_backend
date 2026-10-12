package com.example.media.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.BadRequestBusinessException;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.entity.MediaLinkRole;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaLinkServiceTest {

    @InjectMocks
    private MediaLinkService mediaLinkService;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private MediaAssetLinkRepository mediaAssetLinkRepository;

    @Test
    @DisplayName("프로필 링크 동기화는 원본과 feed derivative를 함께 연결한다")
    void syncProfileLinks_linksPrimaryAndFeedAssets() {
        MediaAsset primaryAsset = MediaAsset.builder()
                .id(31L)
                .ownerUserId(5L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .objectKey("media/profile/5/31.webp")
                .build();
        MediaAsset feedAsset = MediaAsset.builder()
                .id(32L)
                .ownerUserId(5L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .objectKey("media/profile-feed/5/31.webp")
                .derivedFrom(primaryAsset)
                .build();

        when(mediaAssetLinkRepository.findByDomainAndEntityId(MediaDomain.PROFILE, 5L)).thenReturn(List.of());
        when(mediaAssetRepository.findByObjectKeyIn(List.of("media/profile/5/31.webp"))).thenReturn(List.of(primaryAsset));
        when(mediaAssetRepository.findByDerivedFrom_Id(31L)).thenReturn(Optional.of(feedAsset));
        when(mediaAssetLinkRepository.findById(31L)).thenReturn(Optional.empty());
        when(mediaAssetLinkRepository.findById(32L)).thenReturn(Optional.empty());
        when(mediaAssetLinkRepository.save(any(MediaAssetLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MediaLinkService.ProfileLinkResult result = mediaLinkService.syncProfileLinks(5L, "media/profile/5/31.webp");

        assertEquals("media/profile/5/31.webp", result.profileObjectKey());
        assertEquals("media/profile-feed/5/31.webp", result.profileFeedObjectKey());
        verify(mediaAssetLinkRepository).findByDomainAndEntityId(MediaDomain.PROFILE, 5L);

        ArgumentCaptor<MediaAssetLink> linkCaptor = ArgumentCaptor.forClass(MediaAssetLink.class);
        verify(mediaAssetLinkRepository, times(2)).save(linkCaptor.capture());
        List<MediaAssetLink> savedLinks = linkCaptor.getAllValues();
        assertEquals(MediaLinkRole.PROFILE_PRIMARY, savedLinks.get(0).getRole());
        assertEquals(MediaLinkRole.PROFILE_FEED, savedLinks.get(1).getRole());
    }

    @Test
    @DisplayName("READY 상태가 아닌 managed key는 저장에 사용할 수 없다")
    void resolveReadyAssets_rejectsNonReadyManagedAsset() {
        MediaAsset pendingAsset = MediaAsset.builder()
                .id(41L)
                .ownerUserId(2L)
                .domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.PENDING)
                .objectKey("media/cheer/2/41.webp")
                .build();

        when(mediaAssetRepository.findByObjectKeyIn(List.of("media/cheer/2/41.webp"))).thenReturn(List.of(pendingAsset));

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> mediaLinkService.resolveReadyAssets(2L, MediaDomain.CHEER, List.of("media/cheer/2/41.webp")));

        assertEquals("MEDIA_ASSET_NOT_READY", exception.getCode());
    }

    @Test
    @DisplayName("도메인이 다른 managed key는 저장에 사용할 수 없다")
    void resolveReadyAssets_rejectsDomainMismatch() {
        MediaAsset cheerAsset = MediaAsset.builder()
                .id(45L)
                .ownerUserId(2L)
                .domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.READY)
                .objectKey("media/chat/2/45.webp")
                .build();

        when(mediaAssetRepository.findByObjectKeyIn(List.of("media/chat/2/45.webp"))).thenReturn(List.of(cheerAsset));

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> mediaLinkService.resolveReadyAssets(2L, MediaDomain.CHAT, List.of("media/chat/2/45.webp")));

        assertEquals("MEDIA_ASSET_DOMAIN_MISMATCH", exception.getCode());
    }

    @Test
    @DisplayName("다른 사용자의 managed key는 없는 업로드처럼 처리한다")
    void resolveReadyAssets_rejectsNonOwnedManagedAssetAsNotFound() {
        MediaAsset otherUsersAsset = MediaAsset.builder()
                .id(51L)
                .ownerUserId(9L)
                .domain(MediaDomain.CHAT)
                .status(MediaAssetStatus.READY)
                .objectKey("media/chat/9/51.webp")
                .build();

        when(mediaAssetRepository.findByObjectKeyIn(List.of("media/chat/9/51.webp"))).thenReturn(List.of(otherUsersAsset));

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> mediaLinkService.resolveReadyAssets(2L, MediaDomain.CHAT, List.of("media/chat/9/51.webp")));

        assertEquals("MEDIA_ASSET_NOT_FOUND", exception.getCode());
    }

    @Test
    @DisplayName("standalone 시야뷰는 본인 소유의 READY SEAT_VIEW 자산만 해당 후보에 연결한다")
    void linkSeatViewAsset_validatesOwnershipDomainAndReadinessBeforeLinking() {
        String storagePath = "media/seat-view/7/61.webp";
        MediaAsset asset = MediaAsset.builder()
                .id(61L)
                .ownerUserId(7L)
                .domain(MediaDomain.SEAT_VIEW)
                .status(MediaAssetStatus.READY)
                .objectKey(storagePath)
                .build();
        when(mediaAssetRepository.findByObjectKeyIn(List.of(storagePath))).thenReturn(List.of(asset));
        when(mediaAssetLinkRepository.findById(61L)).thenReturn(Optional.empty());
        when(mediaAssetLinkRepository.save(any(MediaAssetLink.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mediaLinkService.linkSeatViewAsset(7L, storagePath, 501L);

        ArgumentCaptor<MediaAssetLink> linkCaptor = ArgumentCaptor.forClass(MediaAssetLink.class);
        verify(mediaAssetLinkRepository).save(linkCaptor.capture());
        assertEquals(MediaDomain.SEAT_VIEW, linkCaptor.getValue().getDomain());
        assertEquals(501L, linkCaptor.getValue().getEntityId());
        assertEquals(MediaLinkRole.SEAT_VIEW_PHOTO, linkCaptor.getValue().getRole());
    }

    @Test
    @DisplayName("standalone 시야뷰는 다른 계정 소유 자산을 연결할 수 없다")
    void linkSeatViewAsset_rejectsAnotherUsersAsset() {
        String storagePath = "media/seat-view/9/62.webp";
        MediaAsset otherUsersAsset = MediaAsset.builder()
                .id(62L)
                .ownerUserId(9L)
                .domain(MediaDomain.SEAT_VIEW)
                .status(MediaAssetStatus.READY)
                .objectKey(storagePath)
                .build();
        when(mediaAssetRepository.findByObjectKeyIn(List.of(storagePath))).thenReturn(List.of(otherUsersAsset));

        BadRequestBusinessException exception = assertThrows(
                BadRequestBusinessException.class,
                () -> mediaLinkService.linkSeatViewAsset(7L, storagePath, 502L));

        assertEquals("MEDIA_ASSET_NOT_FOUND", exception.getCode());
    }
}
