package com.example.cheerboard.repo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostReport;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.domain.ReportReason;
import com.example.cheerboard.service.CheerPurgeCoordination;
import com.example.common.exception.ConflictBusinessException;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
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
 * Reproduces which purge states the current claim and report-intake rules allow. Read-only with respect to
 * production: it runs only against an in-memory H2 database.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_purge_stuck_state;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerPurgeStuckStateReproductionTest {

    @Autowired private CheerPostRepo postRepository;
    @Autowired private CheerReportRepo reportRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void openReportKeepsDeletedPostFromBeingClaimed() {
        Fixture fixture = createDeletedPost(CheerPostReport.ReportStatus.PENDING, null);

        assertThat(claim(fixture.postId())).isZero();
    }

    @Test
    void resolvedReportAlsoKeepsDeletedPostFromBeingClaimed() {
        Fixture fixture = createDeletedPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);

        assertThat(claim(fixture.postId())).isZero();
    }

    @Test
    void deletedPostWithoutReportsIsClaimedAndBlocksAuthorReportIntake() {
        Fixture fixture = createDeletedPost(null, null);

        assertThat(claim(fixture.postId())).isEqualTo(1);
        assertThatThrownBy(() -> CheerPurgeCoordination.assertNoStartedPurge(postRepository, fixture.authorId()))
                .isInstanceOfSatisfying(ConflictBusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("POST_PURGE_IN_PROGRESS"));
    }

    @Test
    void reportedPostWithPurgeStartedBeforeTheRuleChangeIsStuckAndBlocksTheAuthor() {
        // Legacy state: purge started on a post that already has a resolved report. The current claim rule can no
        // longer reclaim it, so the author-level guard never clears.
        Fixture fixture = createDeletedPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);
        jdbcTemplate.update("UPDATE cheer_post SET purge_started_at = ?, purge_claim_token = ? WHERE id = ?",
                LocalDateTime.now(ZoneOffset.UTC), "legacy-claim", fixture.postId());

        assertThat(claim(fixture.postId())).isZero();
        assertThatThrownBy(() -> CheerPurgeCoordination.assertNoStartedPurge(postRepository, fixture.authorId()))
                .isInstanceOfSatisfying(ConflictBusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("POST_PURGE_IN_PROGRESS"));
    }

    @Test
    void hardDeleteKeepsPostWithReportHistoryAndReportsZeroRows() {
        Fixture fixture = createDeletedPost(CheerPostReport.ReportStatus.RESOLVED, CheerPostReport.AppealStatus.NONE);

        Integer deleted = transaction().execute(status -> postRepository.hardDeleteById(fixture.postId()));

        assertThat(deleted).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cheer_post WHERE id = ?", Integer.class,
                fixture.postId())).isOne();
    }

    private int claim(Long postId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Integer claimed = transaction().execute(status -> postRepository.claimPurge(
                postId, "repro-claim", now, now.plusMinutes(15)));
        return claimed == null ? 0 : claimed;
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private Fixture createDeletedPost(CheerPostReport.ReportStatus reportStatus,
                                      CheerPostReport.AppealStatus appealStatus) {
        return transaction().execute(status -> {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                    .teamId("PS" + suffix)
                    .teamName("Purge Stuck Team")
                    .teamShortName("PS")
                    .city("Synthetic")
                    .build());
            UserEntity author = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("p" + suffix)
                    .name("Synthetic Author")
                    .email("purge-author-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .build());
            UserEntity reporter = userRepository.saveAndFlush(UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("q" + suffix)
                    .name("Synthetic Reporter")
                    .email("purge-reporter-" + suffix + "@example.test")
                    .role("ROLE_USER")
                    .build());
            CheerPost post = postRepository.saveAndFlush(CheerPost.builder()
                    .author(author)
                    .team(team)
                    .postType(PostType.NORMAL)
                    .content("synthetic purge content")
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
