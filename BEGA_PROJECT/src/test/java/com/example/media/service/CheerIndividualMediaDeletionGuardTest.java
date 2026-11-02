package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.common.exception.ConflictBusinessException;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@Import(CheerIndividualMediaDeletion.class)
class CheerIndividualMediaDeletionGuardTest {
    @Autowired MediaAssetRepository assets;
    @Autowired CheerPostRepo posts;
    @Autowired UserRepository users;
    @Autowired TeamRepository teams;
    @Autowired JdbcTemplate jdbc;
    @Autowired CheerIndividualMediaDeletion individualMediaDeletion;
    @MockitoBean com.example.cheerboard.storage.config.StorageConfig storageConfig;

    @Test
    void ordinaryAuthorAndAssetOwnerPassTheGuard() {
        Fixture fixture = fixture(user("author", null, false), user("owner", null, false));

        assertThatCode(() -> individualMediaDeletion.lockOwners(fixture.key(), fixture.author().getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void authorInGracePeriodBlocksTheGuard() {
        Fixture fixture = fixture(user("gauthor", LocalDateTime.now().plusDays(7), false),
                user("owner", null, false));

        assertThatThrownBy(() -> individualMediaDeletion.lockOwners(fixture.key(), fixture.author().getId()))
                .isInstanceOf(ConflictBusinessException.class);
    }

    @Test
    void assetOwnerInGracePeriodBlocksTheGuard() {
        Fixture fixture = fixture(user("author", null, false),
                user("gowner", LocalDateTime.now().plusDays(7), false));

        assertThatThrownBy(() -> individualMediaDeletion.lockOwners(fixture.key(), fixture.author().getId()))
                .isInstanceOf(ConflictBusinessException.class);
    }

    @Test
    void tombstoneAuthorWithOpenReportBlocksTheGuard() {
        UserEntity tombstone = user("tauth", null, true);
        Fixture fixture = fixture(tombstone, user("owner", null, false));
        insertOpenReport(fixture.postOfAuthor(), fixture.reporter().getId());

        assertThatThrownBy(() -> individualMediaDeletion.lockOwners(fixture.key(), tombstone.getId()))
                .isInstanceOf(ConflictBusinessException.class);
        assertAssetStillReady(fixture.key());
    }

    @Test
    void tombstoneAuthorWithoutOpenReportPassesTheGuard() {
        UserEntity tombstone = user("tquiet", null, true);
        Fixture fixture = fixture(tombstone, user("owner", null, false));

        assertThatCode(() -> individualMediaDeletion.lockOwners(fixture.key(), tombstone.getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void tombstoneAssetOwnerWithOpenReportBlocksTheGuard() {
        UserEntity tombstoneOwner = user("towner", null, true);
        Fixture fixture = fixture(user("author", null, false), tombstoneOwner);
        insertOpenReport(fixture.postOfOwner(), fixture.reporter().getId());

        assertThatThrownBy(() -> individualMediaDeletion.lockOwners(fixture.key(), fixture.author().getId()))
                .isInstanceOf(ConflictBusinessException.class);
    }

    @Test
    void openReportOnActiveAuthorPassesTheGuard() {
        Fixture fixture = fixture(user("oauthor", null, false), user("oowner", null, false));
        insertOpenReport(fixture.postOfAuthor(), fixture.reporter().getId());

        assertThatCode(() -> individualMediaDeletion.lockOwners(fixture.key(), fixture.author().getId()))
                .doesNotThrowAnyException();
    }

    @Test
    void reserveRefusesMismatchedPathWithoutTouchingAsset() {
        Fixture fixture = fixture(user("rauthor", null, false), user("rowner", null, false));
        MediaAsset asset = assets.findByObjectKey(fixture.key()).orElseThrow();
        var candidate = new CheerIndividualMediaDeletion.Candidate(asset.getId(), asset.getOwnerUserId(), fixture.key());

        assertThatThrownBy(() -> individualMediaDeletion.reserve(candidate, 0L, 0L, "media/cheer/other.webp"))
                .isInstanceOf(ConflictBusinessException.class);
        assertAssetStillReady(fixture.key());
    }

    private void assertAssetStillReady(String key) {
        assertThat(assets.findByObjectKey(key).orElseThrow().getStatus()).isEqualTo(MediaAssetStatus.READY);
    }

    private Fixture fixture(UserEntity author, UserEntity owner) {
        UserEntity reporter = user("reporter", null, false);
        TeamEntity team = teams.saveAndFlush(TeamEntity.builder().teamId("ZI").teamName("Synthetic")
                .teamShortName("ZI").city("Synthetic").build());
        CheerPost postOfAuthor = posts.saveAndFlush(CheerPost.builder().author(author).team(team)
                .content("synthetic").build());
        CheerPost postOfOwner = posts.saveAndFlush(CheerPost.builder().author(owner).team(team)
                .content("synthetic").build());
        String key = "media/cheer/synthetic/" + UUID.randomUUID() + ".webp";
        assets.saveAndFlush(MediaAsset.builder()
                .ownerUserId(owner.getId()).domain(MediaDomain.CHEER).status(MediaAssetStatus.READY)
                .declaredContentType("image/webp").declaredBytes(10L).declaredWidth(1).declaredHeight(1)
                .stagingObjectKey("synthetic-stage-" + UUID.randomUUID()).objectKey(key)
                .uploadExpiresAt(LocalDateTime.now().plusHours(1)).build());
        return new Fixture(key, author, owner, reporter, postOfAuthor.getId(), postOfOwner.getId());
    }

    private UserEntity user(String handle, LocalDateTime scheduledFor, boolean tombstone) {
        boolean pending = scheduledFor != null || tombstone;
        return users.saveAndFlush(UserEntity.builder().uniqueId(UUID.randomUUID())
                .handle(handle + "-" + UUID.randomUUID().toString().substring(0, 6)).name("Synthetic")
                .email(handle + "-" + UUID.randomUUID() + "@example.invalid")
                .role("ROLE_USER").enabled(!pending).pendingDeletion(pending)
                .deletionScheduledFor(scheduledFor).locked(tombstone).build());
    }

    private void insertOpenReport(Long postId, Long reporterId) {
        jdbc.update("INSERT INTO cheer_post_reports (post_id, reporter_id, reason, status, appeal_status, appeal_count, createdat) VALUES (?, ?, ?, ?, ?, ?, ?)",
                postId, reporterId, "OTHER", "PENDING", "NONE", 0, LocalDateTime.now().minusDays(2));
    }

    private record Fixture(String key, UserEntity author, UserEntity owner, UserEntity reporter,
                           Long postOfAuthor, Long postOfOwner) {
    }
}
