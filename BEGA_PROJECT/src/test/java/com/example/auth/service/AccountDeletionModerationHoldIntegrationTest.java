package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.media.entity.MediaDomain;
import com.example.media.service.MediaUploadService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real-SQL checks for the moderation hold: a purge that already started under an open case must stay
 * unclaimed, and the account-deletion content pass must report MODERATION_PENDING without touching media.
 */
@DataJpaTest
@Import(AccountDeletionContentService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AccountDeletionModerationHoldIntegrationTest {
    @Autowired CheerPostRepo posts;
    @Autowired CheerReportRepo reports;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired AccountDeletionContentService contentService;

    @MockitoBean MediaUploadService mediaUploadService;

    private static final String REDACTED_POST = "게시글이 비식별 처리되었습니다.";

    private record Fixture(Long authorId, Long postId) {}

    private Fixture newDeletedPostWithAuthor() {
        Long authorId = newUser();
        return new Fixture(authorId, newPost(authorId, true));
    }

    private Long newUser() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            return users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                    .handle("u" + suffix).name("Synthetic").email(suffix + "@example.invalid")
                    .role("ROLE_USER").enabled(true).build()).getId();
        });
    }

    private Long newPost(Long authorId, boolean deleted) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            TeamEntity team = teams.findById("ZZ").orElseGet(() -> teams.save(TeamEntity.builder()
                    .teamId("ZZ").teamName("Synthetic").teamShortName("ZZ").city("Synthetic").build()));
            UserEntity author = users.findById(authorId).orElseThrow();
            CheerPost post = posts.saveAndFlush(CheerPost.builder().author(author).team(team)
                    .postType(PostType.NORMAL).content("original").build());
            jdbc.update("UPDATE cheer_post SET deleted = ? WHERE id = ?", deleted, post.getId());
            return post.getId();
        });
    }

    private void requestDeletion(Long userId, boolean scheduled) {
        jdbc.update("UPDATE users SET pending_deletion = ?, deletion_scheduled_for = ? WHERE id = ?",
                true, scheduled ? LocalDateTime.now().minusMinutes(1) : null, userId);
    }

    private void insertReport(Long postId, Long reporterId, String status, LocalDateTime createdAt) {
        insertReport(postId, reporterId, status, "NONE", createdAt);
    }

    private void insertReport(Long postId, Long reporterId, String status, String appealStatus, LocalDateTime createdAt) {
        jdbc.update("INSERT INTO cheer_post_reports (post_id, reporter_id, reason, status, appeal_status, appeal_count, createdat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                postId, reporterId, "OTHER", status, appealStatus, 0, createdAt);
    }

    /** Puts a media asset owned by {@code ownerId} on the post, the way a cheer image upload links it. */
    private void linkMediaOwnedBy(Long ownerId, Long postId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        String stagingKey = "staging/" + UUID.randomUUID();
        jdbc.update("""
                INSERT INTO media_assets (owner_user_id, domain, status, declared_content_type, declared_bytes,
                                          declared_width, declared_height, staging_object_key,
                                          upload_expires_at, created_at, updated_at)
                VALUES (?, 'CHEER', 'READY', 'image/png', 1, 1, 1, ?, ?, ?, ?)
                """, ownerId, stagingKey, now.plusHours(1), now, now);
        Long assetId = jdbc.queryForObject("SELECT id FROM media_assets WHERE staging_object_key = ?",
                Long.class, stagingKey);
        jdbc.update("INSERT INTO media_asset_links (asset_id, domain, entity_id, role, linked_at) VALUES (?, 'CHEER', ?, 'CHEER_IMAGE', ?)",
                assetId, postId, now);
    }

    private Integer claimPurge(Long postId, String token, LocalDateTime now) {
        return new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(postId, token, now, now.plusMinutes(15)));
    }

    private AccountDeletionContentService.Result anonymize(Long authorId) {
        return new TransactionTemplate(transactionManager).execute(status -> contentService.anonymize(authorId));
    }

    private String contentOf(Long postId) {
        return jdbc.queryForObject("SELECT content FROM cheer_post WHERE id = ?", String.class, postId);
    }

    @Test
    void startedPurgeUnderOpenCaseStaysUnclaimedAndContentIsHeld() {
        Fixture fixture = newDeletedPostWithAuthor();
        Long authorId = fixture.authorId();
        Long postId = fixture.postId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        // Start the purge while the author is still active. The lease is already expired so a later claim can run.
        Integer firstClaim = new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(postId, "first", now, now.minusMinutes(1)));
        assertThat(firstClaim).isEqualTo(1);

        // The author requests deletion and a case is opened on the post while the purge is in progress.
        jdbc.update("UPDATE users SET pending_deletion = ?, deletion_scheduled_for = ? WHERE id = ?",
                true, LocalDateTime.now().minusMinutes(1), authorId);
        insertReport(postId, authorId, "PENDING", LocalDateTime.now().minusDays(2));

        assertThat(reports.findOpenModerationPostIdsByAuthorId(authorId)).containsExactly(postId);
        assertThat(posts.findDuePurgeIds(now, 1, PageRequest.of(0, 50))).doesNotContain(postId);
        Integer blockedClaim = new TransactionTemplate(transactionManager).execute(status ->
                posts.claimPurge(postId, "blocked", now, now.plusMinutes(15)));
        assertThat(blockedClaim).isZero();

        // Content pass: moderation holds first, so the post is not redacted and no media call happens.
        var result = new TransactionTemplate(transactionManager).execute(status -> contentService.anonymize(authorId));
        assertThat(result).isEqualTo(AccountDeletionContentService.Result.MODERATION_PENDING);
        assertThat(jdbc.queryForObject("SELECT content FROM cheer_post WHERE id = ?", String.class, postId))
                .isEqualTo("original");
        verifyNoInteractions(mediaUploadService);
    }

    @Test
    void reportedPostNeverResumesPurgeAndDoesNotHoldTheAccount() {
        Fixture fixture = newDeletedPostWithAuthor();
        Long authorId = fixture.authorId();
        Long postId = fixture.postId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("UPDATE cheer_post SET purge_started_at = ?, purge_claim_token = ? WHERE id = ?",
                LocalDateTime.now().minusMinutes(1), "held", postId);
        insertReport(postId, authorId, "PENDING", LocalDateTime.now().minusDays(2));
        jdbc.update("UPDATE cheer_post_reports SET status = ? WHERE post_id = ?", "RESOLVED", postId);

        // Reports keep the post out of purge for good, so the started purge is never resumed or claimed again.
        assertThat(posts.findDuePurgeIds(now, 1, PageRequest.of(0, 50))).doesNotContain(postId);
        assertThat(claimPurge(postId, "allowed", now)).isZero();
        // The content pass does not wait on a purge that cannot finish, so it completes and redacts the content.
        assertThat(anonymize(authorId)).isEqualTo(AccountDeletionContentService.Result.COMPLETED);
        assertThat(contentOf(postId)).isEqualTo(REDACTED_POST);
    }

    @Test
    void openCaseAgeQueryDetectsOnlyCasesOlderThanCutoff() {
        Fixture fixture = newDeletedPostWithAuthor();
        Long authorId = fixture.authorId();
        insertReport(fixture.postId(), authorId, "IN_REVIEW", LocalDateTime.now().minusDays(2));

        assertThat(reports.existsOpenModerationOlderThan(authorId, LocalDateTime.now().minusDays(1))).isTrue();
        assertThat(reports.existsOpenModerationOlderThan(authorId, LocalDateTime.now().minusDays(3))).isFalse();
    }

    @Test
    void appealOnlyHoldKeepsContentAndPurgeHeld() {
        Fixture fixture = newDeletedPostWithAuthor();
        Long authorId = fixture.authorId();
        Long postId = fixture.postId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // Pending without a schedule: a scheduled deletion blocks claims on its own, so the case must be what holds.
        requestDeletion(authorId, false);
        // The moderation itself is closed, but the author has appealed it, so the case is still open.
        insertReport(postId, authorId, "RESOLVED", "REQUESTED", LocalDateTime.now().minusDays(2));

        assertThat(reports.findOpenModerationPostIdsByAuthorId(authorId)).containsExactly(postId);
        assertThat(claimPurge(postId, "appeal-held", now)).isZero();
        assertThat(anonymize(authorId)).isEqualTo(AccountDeletionContentService.Result.MODERATION_PENDING);
        assertThat(contentOf(postId)).isEqualTo("original");
        verifyNoInteractions(mediaUploadService);
    }

    @Test
    void openCaseBlocksPurgeOfSoftDeletedPostWhenAuthorIsNotPendingDeletion() {
        Long authorId = newUser();
        Long postId = newPost(authorId, true);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        // The author has not asked for deletion, so the open case is the only thing that can hold the purge.
        insertReport(postId, authorId, "PENDING", LocalDateTime.now().minusDays(2));

        assertThat(claimPurge(postId, "active-author-held", now)).isZero();

        jdbc.update("UPDATE cheer_post_reports SET status = ? WHERE post_id = ?", "RESOLVED", postId);

        // The case is closed, but the report stays on the post, so the post is still never purged.
        assertThat(claimPurge(postId, "active-author-released", now)).isZero();
    }

    @Test
    void closedCasesDoNotHoldContentOrPurge() {
        Fixture fixture = newDeletedPostWithAuthor();
        Long authorId = fixture.authorId();
        Long postId = fixture.postId();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        requestDeletion(authorId, false);
        insertReport(postId, authorId, "RESOLVED", "NONE", LocalDateTime.now().minusDays(3));
        insertReport(postId, authorId, "CLOSED", "REJECTED", LocalDateTime.now().minusDays(2));

        assertThat(reports.findOpenModerationPostIdsByAuthorId(authorId)).isEmpty();
        assertThat(claimPurge(postId, "closed", now)).isZero();
        assertThat(anonymize(authorId)).isEqualTo(AccountDeletionContentService.Result.COMPLETED);
        assertThat(contentOf(postId)).isEqualTo(REDACTED_POST);
    }

    @Test
    void openCaseOnAnotherAuthorsPostDoesNotHoldThisAuthorsContent() {
        Long authorId = newUser();
        Long otherAuthorId = newUser();
        Long ownPostId = newPost(authorId, false);
        Long otherPostId = newPost(otherAuthorId, true);
        requestDeletion(authorId, true);
        // This account is only the reporter; the open case is on someone else's post.
        insertReport(otherPostId, authorId, "PENDING", LocalDateTime.now().minusDays(2));

        assertThat(reports.findOpenModerationPostIdsByAuthorId(authorId)).isEmpty();
        assertThat(anonymize(authorId)).isEqualTo(AccountDeletionContentService.Result.COMPLETED);
        assertThat(contentOf(ownPostId)).isEqualTo(REDACTED_POST);
        assertThat(contentOf(otherPostId)).isEqualTo("original");
        verify(mediaUploadService).deleteOwnedAssetsForAccount(authorId, Set.of());
    }

    @Test
    void openCaseOnAnotherAuthorsPostDoesNotBlockPurgeClaim() {
        Long authorId = newUser();
        Long otherAuthorId = newUser();
        Long postId = newPost(authorId, true);
        Long otherPostId = newPost(otherAuthorId, true);
        requestDeletion(authorId, false);
        insertReport(otherPostId, authorId, "PENDING", LocalDateTime.now().minusDays(2));

        assertThat(claimPurge(postId, "scoped", LocalDateTime.now(ZoneOffset.UTC))).isEqualTo(1);
    }

    @Test
    void openCaseAgainstAssetOwnerBlocksPurgeOfPostSharingTheirMedia() {
        Long authorId = newUser();
        Long assetOwnerId = newUser();
        Long postId = newPost(authorId, true);
        Long assetOwnerPostId = newPost(assetOwnerId, false);
        linkMediaOwnedBy(assetOwnerId, postId);
        // Pending without a schedule, so only the open-case clause on the owner's posts can block the claim.
        jdbc.update("UPDATE users SET pending_deletion = ? WHERE id = ?", true, assetOwnerId);
        insertReport(assetOwnerPostId, authorId, "IN_REVIEW", LocalDateTime.now().minusDays(2));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        assertThat(claimPurge(postId, "media-held", now)).isZero();

        jdbc.update("UPDATE cheer_post_reports SET status = ? WHERE post_id = ?", "RESOLVED", assetOwnerPostId);

        assertThat(claimPurge(postId, "media-released", now)).isEqualTo(1);
    }

    @Test
    void heldContentIsRedactedOnceTheOpenCaseCloses() {
        Long authorId = newUser();
        Long postId = newPost(authorId, false);
        insertReport(postId, authorId, "PENDING", LocalDateTime.now().minusDays(2));

        assertThat(anonymize(authorId)).isEqualTo(AccountDeletionContentService.Result.MODERATION_PENDING);
        assertThat(contentOf(postId)).isEqualTo("original");
        verifyNoInteractions(mediaUploadService);

        // Moderators resolve the case; the next content pass runs to completion.
        jdbc.update("UPDATE cheer_post_reports SET status = ? WHERE post_id = ?", "RESOLVED", postId);

        assertThat(anonymize(authorId)).isEqualTo(AccountDeletionContentService.Result.COMPLETED);
        assertThat(contentOf(postId)).isEqualTo(REDACTED_POST);
        verify(mediaUploadService).unlinkEntityMedia(MediaDomain.CHEER, postId);
        verify(mediaUploadService).deleteOwnedAssetsForAccount(authorId, Set.of());
    }

    @Test
    void repostGuardSeesOnlyOpenCasesOnThatPost() {
        Long authorId = newUser();
        Long reporterId = newUser();
        Long repostId = newPost(authorId, false);
        Long otherPostId = newPost(authorId, false);
        insertReport(repostId, reporterId, "CLOSED", "REJECTED", LocalDateTime.now().minusDays(2));
        insertReport(otherPostId, reporterId, "PENDING", LocalDateTime.now().minusDays(2));

        assertThat(posts.existsOpenModerationByPostId(repostId)).isFalse();

        insertReport(repostId, reporterId, "IN_REVIEW", LocalDateTime.now().minusDays(1));

        assertThat(posts.existsOpenModerationByPostId(repostId)).isTrue();
    }
}
