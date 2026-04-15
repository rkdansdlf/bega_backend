package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.service.ImageService;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.cheerboard.storage.strategy.StoredObject;
import com.example.common.image.ImageUtil;
import com.example.mate.repository.ChatMessageRepository;
import com.example.mate.entity.ChatMessage;
import com.example.mate.service.ChatImageService;
import com.example.media.dto.MediaBackfillDomainReport;
import com.example.media.dto.MediaBackfillReport;
import com.example.media.dto.MediaCleanupReport;
import com.example.media.dto.MediaCleanupTargetReport;
import com.example.media.dto.MediaSmokeDomainReport;
import com.example.media.dto.MediaSmokeReport;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaCleanupTarget;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetRepository;
import com.example.profile.storage.service.ProfileImageService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MediaMaintenanceServiceTest {

    @InjectMocks
    private MediaMaintenanceService mediaMaintenanceService;

    @Mock
    private MediaAssetRepository mediaAssetRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BegaDiaryRepository begaDiaryRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private StorageStrategy storageStrategy;

    @Mock
    private StorageConfig storageConfig;

    @Mock
    private ProfileImageService profileImageService;

    @Mock
    private ImageService imageService;

    @Mock
    private ChatImageService chatImageService;

    @Mock
    private MediaLinkService mediaLinkService;

    @Mock
    private MediaUploadService mediaUploadService;

    @Mock
    private ImageUtil imageUtil;

    private AtomicLong mediaAssetIdSequence;

    @BeforeEach
    void setUp() {
        mediaAssetIdSequence = new AtomicLong(70L);
        lenient().when(userRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        lenient().when(begaDiaryRepository.findAllBy(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        lenient().when(chatMessageRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(Page.empty());
        lenient().when(mediaAssetRepository.findByDomainAndStatusOrderByIdDesc(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(mediaAssetRepository.findByDomainAndStatusAndDerivedFromIsNullOrderByIdDesc(any(), any(), any()))
                .thenReturn(List.of());
        lenient().when(mediaAssetRepository.saveAndFlush(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        lenient().when(mediaAssetRepository.save(any(MediaAsset.class)))
                .thenAnswer(invocation -> assignMediaAssetId(invocation.getArgument(0)));
        lenient().when(chatMessageRepository.save(any(ChatMessage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("profile backfill dry-run은 signed URL을 canonical key로 정규화하고 feed key를 제안한다")
    void backfillExistingData_profileDryRunNormalizesManagedKey() {
        UserEntity user = UserEntity.builder()
                .id(7L)
                .profileImageUrl("https://cdn.example.com/media/profile/7/11.webp?sig=abc")
                .build();
        MediaAsset primaryAsset = MediaAsset.builder()
                .id(11L)
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .objectKey("media/profile/7/11.webp")
                .build();
        MediaAsset feedAsset = MediaAsset.builder()
                .id(12L)
                .ownerUserId(7L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .objectKey("media/profile-feed/7/11.webp")
                .derivedFrom(primaryAsset)
                .build();

        when(userRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(user)));
        when(profileImageService.normalizeProfileStoragePath("https://cdn.example.com/media/profile/7/11.webp?sig=abc"))
                .thenReturn("media/profile/7/11.webp");
        when(mediaLinkService.resolveReadyAssets(7L, MediaDomain.PROFILE, List.of("media/profile/7/11.webp")))
                .thenReturn(Map.of("media/profile/7/11.webp", primaryAsset));
        when(mediaAssetRepository.findByDerivedFrom_Id(11L)).thenReturn(Optional.of(feedAsset));

        MediaBackfillReport report = mediaMaintenanceService.backfillExistingData(false, 100, List.of(MediaDomain.PROFILE), false);
        MediaBackfillDomainReport profileReport = report.domains().stream()
                .filter(domain -> "PROFILE".equals(domain.domain()))
                .findFirst()
                .orElseThrow();

        assertThat(report.requestedDomains()).containsExactly("PROFILE");
        assertThat(profileReport.scannedCount()).isEqualTo(1);
        assertThat(profileReport.normalizedCount()).isEqualTo(1);
        assertThat(profileReport.updatedCount()).isZero();
        assertThat(profileReport.clearedCount()).isZero();
        assertThat(profileReport.linkSyncedCount()).isEqualTo(1);
        assertThat(profileReport.legacyPathRetainedCount()).isZero();
        assertThat(profileReport.manualReviewCount()).isZero();
        assertThat(profileReport.sampleNormalizedTargets()).containsExactly(
                "userId=7:media/profile/7/11.webp,media/profile-feed/7/11.webp");
        assertThat(profileReport.sampleManualReviewTargets()).isEmpty();
        verify(userRepository, never()).updateProfileImageUrlsById(any(), any(), any());
        verify(mediaLinkService, never()).syncProfileLinks(any(), any());
    }

    @Test
    @DisplayName("cleanup maintenance는 요청된 target만 실행한다")
    void runCleanup_runsRequestedTargets() {
        when(mediaUploadService.cleanupExpiredPendingAssets())
                .thenReturn(new MediaCleanupTargetReport(MediaCleanupTarget.PENDING, 2, 2, 0));

        MediaCleanupReport report = mediaMaintenanceService.runCleanup(List.of(MediaCleanupTarget.PENDING));

        assertThat(report.requestedTargets()).containsExactly("PENDING");
        assertThat(report.targets()).containsExactly(new MediaCleanupTargetReport(MediaCleanupTarget.PENDING, 2, 2, 0));
        assertThat(report.hasFailures()).isFalse();
        verify(mediaUploadService).cleanupExpiredPendingAssets();
        verify(mediaUploadService, never()).cleanupUnlinkedReadyAssets();
    }

    @Test
    @DisplayName("diary backfill apply는 canonical key를 저장하고 media link를 동기화한다")
    void backfillExistingData_diaryApplyNormalizesAndSyncsLinks() {
        UserEntity user = UserEntity.builder()
                .id(5L)
                .build();
        BegaDiary diary = BegaDiary.builder()
                .user(user)
                .photoUrls(new java.util.ArrayList<>(List.of("https://cdn.example.com/media/diary/5/21.webp?sig=abc")))
                .build();
        ReflectionTestUtils.setField(diary, "id", 101L);

        when(begaDiaryRepository.findAllBy(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(diary)));
        when(imageService.normalizeDiaryStoragePaths(List.of("https://cdn.example.com/media/diary/5/21.webp?sig=abc")))
                .thenReturn(List.of("media/diary/5/21.webp"));
        when(mediaLinkService.resolveReadyAssets(5L, MediaDomain.DIARY, List.of("media/diary/5/21.webp")))
                .thenReturn(Map.of(
                        "media/diary/5/21.webp",
                        MediaAsset.builder()
                                .id(21L)
                                .ownerUserId(5L)
                                .domain(MediaDomain.DIARY)
                                .status(MediaAssetStatus.READY)
                                .objectKey("media/diary/5/21.webp")
                                .build()));
        when(mediaAssetRepository.findByObjectKey("media/diary/5/21.webp"))
                .thenReturn(Optional.of(MediaAsset.builder()
                        .id(21L)
                        .ownerUserId(5L)
                        .domain(MediaDomain.DIARY)
                        .status(MediaAssetStatus.READY)
                        .objectKey("media/diary/5/21.webp")
                        .build()));

        MediaBackfillReport report = mediaMaintenanceService.backfillExistingData(true, 100, List.of(MediaDomain.DIARY), false);
        MediaBackfillDomainReport diaryReport = report.domains().stream()
                .filter(domain -> "DIARY".equals(domain.domain()))
                .findFirst()
                .orElseThrow();

        assertThat(report.requestedDomains()).containsExactly("DIARY");
        assertThat(diaryReport.scannedCount()).isEqualTo(1);
        assertThat(diaryReport.normalizedCount()).isEqualTo(1);
        assertThat(diaryReport.updatedCount()).isEqualTo(1);
        assertThat(diaryReport.clearedCount()).isZero();
        assertThat(diaryReport.linkSyncedCount()).isEqualTo(1);
        assertThat(diaryReport.sampleNormalizedTargets()).containsExactly("diaryId=101:media/diary/5/21.webp");
        verify(begaDiaryRepository).save(diary);
        verify(mediaLinkService).syncDiaryLinks(101L, 5L, List.of("media/diary/5/21.webp"));
        assertThat(diary.getPhotoUrls()).containsExactly("media/diary/5/21.webp");
    }

    @Test
    @DisplayName("media smoke는 프로필 feed derivative 누락을 실패로 집계한다")
    void runSmoke_reportsMissingProfileFeedDerivative() {
        MediaAsset primaryAsset = MediaAsset.builder()
                .id(31L)
                .ownerUserId(9L)
                .domain(MediaDomain.PROFILE)
                .status(MediaAssetStatus.READY)
                .objectKey("media/profile/9/31.webp")
                .build();

        when(mediaAssetRepository.findByDomainAndStatusAndDerivedFromIsNullOrderByIdDesc(
                        eq(MediaDomain.PROFILE),
                        eq(MediaAssetStatus.READY),
                        any()))
                .thenReturn(List.of(primaryAsset));
        when(mediaAssetRepository.findByDerivedFrom_Id(31L)).thenReturn(Optional.empty());
        when(storageConfig.getProfileBucket()).thenReturn("profile-bucket");
        when(storageConfig.getSignedUrlTtlSeconds()).thenReturn(600);
        when(storageStrategy.exists("profile-bucket", "media/profile/9/31.webp")).thenReturn(Mono.just(true));
        when(storageStrategy.getUrl("profile-bucket", "media/profile/9/31.webp", 600))
                .thenReturn(Mono.just("https://signed.example/media/profile/9/31.webp"));

        MediaSmokeReport report = mediaMaintenanceService.runSmoke(5, List.of(MediaDomain.PROFILE));
        MediaSmokeDomainReport profileReport = report.domains().stream()
                .filter(domain -> domain.domain() == MediaDomain.PROFILE)
                .findFirst()
                .orElseThrow();

        assertThat(report.requestedDomains()).containsExactly("PROFILE");
        assertThat(report.hasFailures()).isTrue();
        assertThat(profileReport.checkedCount()).isEqualTo(1);
        assertThat(profileReport.feedDerivativeMissingCount()).isEqualTo(1);
        assertThat(profileReport.failedObjectKeys()).contains("media/profile/9/31.webp (feed_missing)");
    }

    @Test
    @DisplayName("chat backfill apply는 legacy chat 경로를 canonical media key와 registry로 승격한다")
    void backfillExistingData_chatApplyPromotesLegacyPath() throws Exception {
        ChatMessage message = ChatMessage.builder()
                .senderId(24L)
                .imageUrl("chat/24/legacy.png")
                .build();
        ReflectionTestUtils.setField(message, "id", 101L);

        byte[] sourceBytes = new byte[] {1, 2, 3};
        byte[] processedBytes = new byte[] {4, 5, 6};
        when(chatMessageRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(chatImageService.normalizeChatStoragePath("chat/24/legacy.png"))
                .thenReturn("chat/24/legacy.png");
        when(storageConfig.getCheerBucket()).thenReturn("cheer-bucket");
        when(storageStrategy.download("cheer-bucket", "chat/24/legacy.png"))
                .thenReturn(Mono.just(new StoredObject(sourceBytes, "image/png")));
        when(imageUtil.getImageDimension(sourceBytes))
                .thenReturn(new ImageUtil.ImageDimension(640, 640));
        when(imageUtil.process(any(), eq("media_backfill_chat")))
                .thenReturn(new ImageUtil.ProcessedImage(processedBytes, "image/webp", "webp"));
        when(imageUtil.getImageDimension(processedBytes))
                .thenReturn(new ImageUtil.ImageDimension(512, 512));
        when(storageStrategy.uploadBytes(processedBytes, "image/webp", "cheer-bucket", "media/chat/24/70.webp"))
                .thenReturn(Mono.just("media/chat/24/70.webp"));
        when(mediaLinkService.resolveReadyAssets(24L, MediaDomain.CHAT, List.of("media/chat/24/70.webp")))
                .thenReturn(Map.of(
                        "media/chat/24/70.webp",
                        MediaAsset.builder()
                                .id(70L)
                                .ownerUserId(24L)
                                .domain(MediaDomain.CHAT)
                                .status(MediaAssetStatus.READY)
                                .objectKey("media/chat/24/70.webp")
                                .build()));

        MediaBackfillReport report = mediaMaintenanceService.backfillExistingData(true, 100, List.of(MediaDomain.CHAT), false);
        MediaBackfillDomainReport chatReport = report.domains().stream()
                .filter(domain -> "CHAT".equals(domain.domain()))
                .findFirst()
                .orElseThrow();

        assertThat(chatReport.scannedCount()).isEqualTo(1);
        assertThat(chatReport.normalizedCount()).isEqualTo(1);
        assertThat(chatReport.updatedCount()).isEqualTo(1);
        assertThat(chatReport.clearedCount()).isZero();
        assertThat(chatReport.linkSyncedCount()).isEqualTo(1);
        assertThat(chatReport.legacyPathRetainedCount()).isZero();
        assertThat(chatReport.sampleNormalizedTargets()).containsExactly("messageId=101:media/chat/24/70.webp");
        assertThat(chatReport.sampleManualReviewTargets()).isEmpty();
        assertThat(message.getImageUrl()).isEqualTo("media/chat/24/70.webp");
        verify(chatMessageRepository).save(message);
        verify(mediaLinkService).syncChatLink(101L, 24L, "media/chat/24/70.webp");
    }

    @Test
    @DisplayName("chat backfill apply는 옵션이 켜지면 손상된 legacy chat 이미지를 비운다")
    void backfillExistingData_chatApplyClearsBrokenLegacyPathWhenEnabled() {
        ChatMessage message = ChatMessage.builder()
                .senderId(24L)
                .imageUrl("chat/24/broken.png")
                .build();
        ReflectionTestUtils.setField(message, "id", 227L);

        when(chatMessageRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message)));
        when(chatImageService.normalizeChatStoragePath("chat/24/broken.png"))
                .thenReturn("chat/24/broken.png");
        when(storageConfig.getCheerBucket()).thenReturn("cheer-bucket");
        when(storageStrategy.download("cheer-bucket", "chat/24/broken.png"))
                .thenReturn(Mono.just(new StoredObject(new byte[] {9, 9, 9}, "image/png")));
        when(imageUtil.getImageDimension(new byte[] {9, 9, 9}))
                .thenThrow(new IllegalArgumentException("이미지 치수 확인 실패: Error reading PNG image data"));

        MediaBackfillReport report = mediaMaintenanceService.backfillExistingData(true, 100, List.of(MediaDomain.CHAT), true);
        MediaBackfillDomainReport chatReport = report.domains().stream()
                .filter(domain -> "CHAT".equals(domain.domain()))
                .findFirst()
                .orElseThrow();

        assertThat(chatReport.scannedCount()).isEqualTo(1);
        assertThat(chatReport.normalizedCount()).isEqualTo(1);
        assertThat(chatReport.updatedCount()).isEqualTo(1);
        assertThat(chatReport.clearedCount()).isEqualTo(1);
        assertThat(chatReport.manualReviewCount()).isZero();
        assertThat(chatReport.sampleNormalizedTargets()).containsExactly("messageId=227:(cleared broken legacy chat image)");
        assertThat(chatReport.sampleManualReviewTargets()).isEmpty();
        assertThat(message.getImageUrl()).isNull();
        verify(chatMessageRepository).save(message);
        verify(mediaLinkService).unlinkEntity(MediaDomain.CHAT, 227L);
    }

    private MediaAsset assignMediaAssetId(MediaAsset asset) {
        if (asset.getId() == null) {
            asset.setId(mediaAssetIdSequence.getAndIncrement());
        }
        return asset;
    }
}
