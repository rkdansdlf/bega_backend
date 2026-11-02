package com.example.cheerboard.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.auth.service.AccountDeletionContentService;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.domain.ReportReason;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.service.CheerPurgeCoordination;
import com.example.cheerboard.storage.config.StorageConfig;
import com.example.cheerboard.storage.service.ImageService;
import com.example.cheerboard.storage.strategy.StorageStrategy;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.image.ImageOptimizationMetricsService;
import com.example.common.image.ImageUtil;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetLink;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaLinkRole;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import com.example.media.service.AccountCheerAssetVerifier;
import com.example.media.service.MediaFinalizeAttemptCleanupService;
import com.example.media.service.MediaFinalizeClaimService;
import com.example.media.service.MediaQuotaService;
import com.example.media.service.MediaRateLimitService;
import com.example.media.service.MediaUploadService;
import com.example.media.service.MediaUploadValidationService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Reproduces the account-hold case where a reported post's purge has started and its media is reserved. Uses the real
 * {@link MediaUploadService} so the pending-purge check runs against the same query the production code uses.
 * In-memory H2 only.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_purge_reported_hold;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerPurgeReportedHoldReproductionTest {

    private static final String CONTENT = "synthetic purge content";

    @Autowired private CheerPostRepo postRepository;
    @Autowired private CheerCommentRepo commentRepository;
    @Autowired private CheerReportRepo reportRepository;
    @Autowired private com.example.auth.repository.LegacyPostImageHoldRepository legacyImageHolds;
    @Autowired private com.example.cheerboard.retention.ReportEvidenceMediaRetentionRepository evidenceRetention;
    @Autowired private MediaAssetRepository mediaAssetRepository;
    @Autowired private MediaAssetLinkRepository mediaAssetLinkRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void reservedAssetOfAReportedPostHoldsTheAccountWithoutProgress() {
        Fixture fixture = createAuthorWithPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);
        transaction().executeWithoutResult(status -> mediaAssetRepository.saveAndFlush(reservedAsset(fixture.authorId())));
        jdbcTemplate.update("UPDATE cheer_post SET purge_started_at = ?, purge_claim_token = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC), "legacy-claim", fixture.postId());

        assertThat(anonymize(fixture.authorId())).isEqualTo(AccountDeletionContentService.Result.PURGE_PENDING);
        assertThat(anonymize(fixture.authorId())).isEqualTo(AccountDeletionContentService.Result.PURGE_PENDING);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM cheer_post WHERE id = ?", String.class, fixture.postId()))
                .isEqualTo(CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_assets WHERE owner_user_id = ? AND status = ?", Integer.class,
                fixture.authorId(), MediaAssetStatus.CHEER_PURGE_PENDING.name())).isEqualTo(1);
    }

    @Test
    void reportIntakeIsRefusedOnceAnAuthorsPurgeHasStarted() {
        Fixture fixture = createAuthorWithPost(null, null);
        assertThat(claim(fixture.postId())).isEqualTo(1);

        assertThatThrownBy(() -> transaction().executeWithoutResult(status ->
                CheerPurgeCoordination.assertNoStartedPurge(postRepository, fixture.authorId())))
                .isInstanceOfSatisfying(ConflictBusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("POST_PURGE_IN_PROGRESS"));
    }

    @Test
    void reservationOnAReportedPostHoldsWithinTheLimitAndReleasesAfterIt() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Fixture recent = createAuthorWithPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);
        reserveLinkedAsset(recent, now.minusDays(1));
        assertThat(anonymize(recent.authorId())).isEqualTo(AccountDeletionContentService.Result.PURGE_PENDING);

        Fixture stale = createAuthorWithPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);
        reserveLinkedAsset(stale, now.minusDays(31));
        assertThat(anonymize(stale.authorId())).isEqualTo(AccountDeletionContentService.Result.COMPLETED);
        assertThat(countReservedAssets(stale.authorId())).isEqualTo(1);
    }

    @Test
    void staleReservedManagedImageOnReportedPostReleasesAfterTheLimit() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Fixture stale = createAuthorWithPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);
        String path = "media/cheer/" + stale.authorId() + "/" + UUID.randomUUID() + ".webp";
        jdbcTemplate.update("INSERT INTO post_images (post_id, storage_path, mime_type, bytes, is_thumbnail, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)", stale.postId(), path, "image/webp", 1L, false, java.time.Instant.now());
        reserveLinkedAsset(stale, now.minusDays(31));
        jdbcTemplate.update("UPDATE media_assets SET object_key = ? WHERE owner_user_id = ? AND status = ?",
                path, stale.authorId(), MediaAssetStatus.CHEER_PURGE_PENDING.name());

        // The reserved asset is still owned and linked, so the verifier passes and the stale reservation releases.
        assertThat(anonymize(stale.authorId())).isEqualTo(AccountDeletionContentService.Result.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("SELECT content FROM cheer_post WHERE id = ?", String.class, stale.postId()))
                .isEqualTo("게시글이 비식별 처리되었습니다.");
        // The image row is cleared, the path is registered for evidence retention, and the reserved asset and its link survive.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM post_images WHERE post_id = ?", Integer.class, stale.postId()))
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM report_evidence_media_retention WHERE storage_path = ?",
                Integer.class, path)).isEqualTo(1);
        assertThat(countReservedAssets(stale.authorId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM media_asset_links WHERE entity_id = ? AND domain = 'CHEER'",
                Integer.class, stale.postId())).isEqualTo(1);
    }

    @Test
    void authorGuardsReleaseOnlyReportedHoldsAfterTheLimit() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        Fixture recentReported = createAuthorWithPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);
        reserveLinkedAsset(recentReported, now.minusDays(1));
        assertThatThrownBy(() -> authorGuards(recentReported.authorId()))
                .isInstanceOf(ConflictBusinessException.class);

        Fixture staleReported = createAuthorWithPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);
        reserveLinkedAsset(staleReported, now.minusDays(31));
        assertThatCode(() -> authorGuards(staleReported.authorId())).doesNotThrowAnyException();

        Fixture staleUnreported = createAuthorWithPost(null, null);
        jdbcTemplate.update("UPDATE cheer_post SET purge_started_at = ?, purge_claim_token = ? WHERE id = ?",
                now.minusDays(31), "unreported-claim", staleUnreported.postId());
        assertThatThrownBy(() -> authorGuards(staleUnreported.authorId()))
                .isInstanceOf(ConflictBusinessException.class);
    }

    private void authorGuards(Long authorId) {
        transaction().executeWithoutResult(status -> {
            CheerPurgeCoordination.assertNoStartedPurge(postRepository, authorId);
            CheerPurgeCoordination.assertNoOwnedReservedAssets(mediaAssetRepository, authorId);
        });
    }

    private void reserveLinkedAsset(Fixture fixture, LocalDateTime purgeStartedAt) {
        transaction().executeWithoutResult(status -> {
            MediaAsset asset = mediaAssetRepository.saveAndFlush(reservedAsset(fixture.authorId()));
            mediaAssetLinkRepository.saveAndFlush(MediaAssetLink.builder()
                    .assetId(asset.getId())
                    .asset(asset)
                    .domain(MediaDomain.CHEER)
                    .entityId(fixture.postId())
                    .role(MediaLinkRole.CHEER_IMAGE)
                    .build());
        });
        jdbcTemplate.update("UPDATE cheer_post SET purge_started_at = ?, purge_claim_token = ? WHERE id = ?",
                purgeStartedAt, "reserved-claim", fixture.postId());
    }

    private int countReservedAssets(Long ownerId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM media_assets WHERE owner_user_id = ? AND status = ?", Integer.class,
                ownerId, MediaAssetStatus.CHEER_PURGE_PENDING.name());
    }

    private AccountDeletionContentService.Result anonymize(Long authorId) {
        return transaction().execute(status -> contentService().anonymize(authorId));
    }

    private AccountDeletionContentService contentService() {
        MediaUploadService mediaUploadService = new MediaUploadService(
                mediaAssetRepository,
                mock(MediaFinalizeClaimService.class),
                mock(MediaFinalizeAttemptCleanupService.class),
                mediaAssetLinkRepository,
                mock(StorageStrategy.class),
                mock(StorageConfig.class),
                mock(MediaUploadValidationService.class),
                mock(MediaQuotaService.class),
                mock(MediaRateLimitService.class),
                mock(ImageUtil.class),
                mock(ImageOptimizationMetricsService.class));
        return new AccountDeletionContentService(postRepository, commentRepository, reportRepository,
                mediaUploadService, mock(ImageService.class), legacyImageHolds, evidenceRetention, mock(StorageConfig.class),
                new AccountCheerAssetVerifier(mediaAssetRepository, mediaAssetLinkRepository));
    }

    private int claim(Long postId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Integer claimed = transaction().execute(status -> postRepository.claimPurge(
                postId, "repro-claim", now, now.plusMinutes(15)));
        return claimed == null ? 0 : claimed;
    }

    private static MediaAsset reservedAsset(Long ownerId) {
        return MediaAsset.builder()
                .ownerUserId(ownerId).domain(MediaDomain.CHEER)
                .status(MediaAssetStatus.CHEER_PURGE_PENDING)
                .declaredContentType("image/png").declaredBytes(1L).declaredWidth(1).declaredHeight(1)
                .stagingObjectKey("synthetic/" + UUID.randomUUID())
                .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private Fixture createAuthorWithPost(CheerPostReport.ReportStatus reportStatus,
                                         CheerPostReport.AppealStatus appealStatus) {
        return transaction().execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                    .teamId("RH" + suffix)
                    .teamName("Reported Hold Team")
                    .teamShortName("RH")
                    .city("Synthetic")
                    .build());
            UserEntity author = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("h" + suffix)
                    .name("Synthetic Author")
                    .email("hold-author-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .build());
            UserEntity reporter = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("s" + suffix)
                    .name("Synthetic Reporter")
                    .email("hold-reporter-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .build());
            CheerPost post = postRepository.saveAndFlush(CheerPost.builder()
                    .author(author)
                    .team(team)
                    .postType(PostType.NORMAL)
                    .content(CONTENT)
                    .build());
            if (reportStatus != null) {
                reportRepository.saveAndFlush(CheerPostReport.builder()
                        .post(post)
                        .reporter(reporter)
                        .reason(ReportReason.SPAM)
                        .status(reportStatus)
                        .appealStatus(appealStatus)
                        .build());
            }
            jdbcTemplate.update("UPDATE cheer_post SET deleted = ? WHERE id = ?", true, post.getId());
            return new Fixture(author.getId(), post.getId());
        });
    }

    private record Fixture(Long authorId, Long postId) {}
}
