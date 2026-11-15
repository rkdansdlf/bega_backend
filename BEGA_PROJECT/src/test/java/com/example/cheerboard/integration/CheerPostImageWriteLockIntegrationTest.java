package com.example.cheerboard.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.cheerboard.storage.repository.PostImageRepository;
import com.example.kbo.entity.TeamEntity;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:cheer_post_image_write_lock;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "spring.jpa.properties.hibernate.show_sql=false"
})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class CheerPostImageWriteLockIntegrationTest {

    private static final int MAX_IMAGES_PER_POST = 4;
    private static final long TIMEOUT_SECONDS = 10;

    @Autowired
    private CheerPostRepo postRepository;

    @Autowired
    private PostImageRepository postImageRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void managedImageUpdateSerializesWithDirectUploadAtThePerPostLimit() throws Exception {
        long postId = createPostWithImages(3);
        CountDownLatch managedUpdateHasReadChildren = new CountDownLatch(1);
        CountDownLatch releaseManagedUpdate = new CountDownLatch(1);
        CountDownLatch directUploadAttempting = new CountDownLatch(1);
        AtomicLong managedSessionId = new AtomicLong();
        AtomicLong directUploadSessionId = new AtomicLong();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Void> managedUpdate = executor.submit(() -> transaction().execute(status -> {
                managedSessionId.set(currentSessionId());
                CheerPost post = postRepository.findByIdForWrite(postId).orElseThrow();
                assertThat(postImageRepository.findByPostIdOrderByCreatedAtAsc(postId)).hasSize(3);
                managedUpdateHasReadChildren.countDown();
                await(releaseManagedUpdate);
                postImageRepository.saveAndFlush(newPostImage(post, "managed-fourth.webp"));
                return null;
            }));

            assertThat(managedUpdateHasReadChildren.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<UploadAttempt> directUpload = executor.submit(() -> transaction().execute(status -> {
                directUploadSessionId.set(currentSessionId());
                directUploadAttempting.countDown();
                CheerPost post = postRepository.findByIdForWrite(postId).orElseThrow();
                long currentCount = postImageRepository.countByPostId(postId);
                if (currentCount < MAX_IMAGES_PER_POST) {
                    postImageRepository.saveAndFlush(newPostImage(post, "direct-upload.webp"));
                    return new UploadAttempt(currentCount, true);
                }
                return new UploadAttempt(currentCount, false);
            }));

            assertThat(directUploadAttempting.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
            awaitBlockedBy(directUploadSessionId.get(), managedSessionId.get());

            releaseManagedUpdate.countDown();
            managedUpdate.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            UploadAttempt result = directUpload.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

            assertThat(result.observedCount()).isEqualTo(MAX_IMAGES_PER_POST);
            assertThat(result.accepted()).isFalse();
            assertThat(countImages(postId)).isEqualTo(MAX_IMAGES_PER_POST);
        } finally {
            releaseManagedUpdate.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long createPostWithImages(int initialImageCount) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return transaction().execute(status -> {
            TeamEntity team = TeamEntity.builder()
                    .teamId("IMG-" + suffix)
                    .teamName("Image Lock Team")
                    .teamShortName("IMG")
                    .city("Seoul")
                    .stadiumName("Jamsil")
                    .build();
            UserEntity author = UserEntity.builder()
                    .uniqueId(UUID.randomUUID())
                    .handle("img" + suffix)
                    .name("Image Lock Test")
                    .email("img-" + suffix + "@example.test")
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
                    .content("image lock test")
                    .build();
            entityManager.persist(post);
            entityManager.flush();
            for (int index = 1; index <= initialImageCount; index++) {
                postImageRepository.save(newPostImage(post, "existing-" + index + ".webp"));
            }
            postImageRepository.flush();
            return post.getId();
        });
    }

    private long countImages(long postId) {
        return transaction().execute(status -> postImageRepository.countByPostId(postId));
    }

    private long currentSessionId() {
        return ((Number) entityManager.createNativeQuery("SELECT SESSION_ID()")
                .getSingleResult())
                .longValue();
    }

    private void awaitBlockedBy(long blockedSessionId, long expectedBlockerSessionId) throws InterruptedException {
        assertThat(blockedSessionId).isPositive();
        assertThat(expectedBlockerSessionId).isPositive();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        Long blockerSessionId = null;
        while (System.nanoTime() < deadline) {
            blockerSessionId = jdbcTemplate.query(
                    "SELECT BLOCKER_ID FROM INFORMATION_SCHEMA.SESSIONS WHERE SESSION_ID = ?",
                    resultSet -> {
                        if (!resultSet.next()) {
                            return null;
                        }
                        Number blocker = (Number) resultSet.getObject("BLOCKER_ID");
                        return blocker == null ? null : blocker.longValue();
                    },
                    blockedSessionId);
            if (blockerSessionId != null && blockerSessionId == expectedBlockerSessionId) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(blockerSessionId)
                .as("H2 should report the direct-upload session blocked by the managed-update lock")
                .isEqualTo(expectedBlockerSessionId);
    }

    private PostImage newPostImage(CheerPost post, String path) {
        return PostImage.builder()
                .post(post)
                .storagePath("posts/" + post.getId() + "/" + path)
                .mimeType("image/webp")
                .bytes(1024L)
                .build();
    }

    private TransactionTemplate transaction() {
        return new TransactionTemplate(transactionManager);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating image-write transactions");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating image-write transactions", exception);
        }
    }

    private record UploadAttempt(long observedCount, boolean accepted) {
    }
}
