package com.example.cheerboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.kbo.entity.TeamEntity;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(CheerPostViewSyncService.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_post_view_sync;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerPostViewSyncServiceIntegrationTest {

    @Autowired
    private CheerPostViewSyncService viewSyncService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void appliesEachPostViewSyncSequenceAtMostOnceAndRejectsStaleBatches() {
        long postId = createPost();

        assertThat(viewSyncService.applyBatch(postId, 7L, 4))
                .get()
                .extracting(projection -> projection.getViews())
                .isEqualTo(14);
        assertThat(viewSyncService.applyBatch(postId, 7L, 4))
                .get()
                .extracting(projection -> projection.getViews())
                .isEqualTo(14);
        assertThat(viewSyncService.applyBatch(postId, 6L, 9))
                .get()
                .extracting(projection -> projection.getViews())
                .isEqualTo(14);
        assertThat(viewSyncService.applyBatch(postId, 8L, 3))
                .get()
                .extracting(projection -> projection.getViews())
                .isEqualTo(17);

        PostState persisted = loadPost(postId);
        assertThat(persisted.views()).isEqualTo(17);
        assertThat(persisted.viewSyncWatermark()).isEqualTo(8L);
    }

    @Test
    void viewCountAndSyncWatermarkRollBackTogether() {
        long postId = createPost();

        assertThatThrownBy(() -> transaction().executeWithoutResult(status -> {
            viewSyncService.applyBatch(postId, 1L, 5);
            throw new IllegalStateException("force enclosing transaction rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("force enclosing transaction rollback");

        PostState persisted = loadPost(postId);
        assertThat(persisted.views()).isEqualTo(10);
        assertThat(persisted.viewSyncWatermark()).isZero();
    }

    private long createPost() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return transaction().execute(status -> {
            TeamEntity team = TeamEntity.builder()
                    .teamId("SYNC-" + suffix)
                    .teamName("Sync Team " + suffix)
                    .teamShortName("SYNC")
                    .city("Seoul")
                    .stadiumName("Jamsil")
                    .build();
            UserEntity author = UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("sync" + suffix)
                    .name("Sync Test")
                    .email("sync-" + suffix + "@example.test")
                    .password("encoded-password")
                    .role("ROLE_USER")
                    .provider("LOCAL")
                    .build();
            entityManager.persist(team);
            entityManager.persist(author);

            CheerPost post = CheerPost.builder()
                    .author(author)
                    .team(team)
                    .postType(PostType.NORMAL)
                    .content("view sync test")
                    .views(10)
                    .build();
            entityManager.persist(post);
            entityManager.flush();
            return post.getId();
        });
    }

    private PostState loadPost(long postId) {
        return transaction().execute(status -> {
            CheerPost post = entityManager.find(CheerPost.class, postId);
            return new PostState(post.getViews(), post.getViewSyncWatermark());
        });
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private record PostState(int views, long viewSyncWatermark) {
    }
}
