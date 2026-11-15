package com.example.cheerboard.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.exception.DuplicateCommentException;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.CheerCommentService;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 댓글 중복 방지 동시성 회귀 테스트 (실제 PostgreSQL).
 *
 * H2 는 PostgreSQL 행 잠금을 재현하지 못해서 {@link CheerConcurrencyIntegrationTest} 의 C-11 은
 * {@code @Disabled} 상태다. 이 클래스는 같은 시나리오를 실제 PostgreSQL 에서 돌려서, 작성자 행 잠금
 * ({@code UserRepository.lockUsableAuthorForWrite}) 이 중복 저장을 막는지 검증한다.
 *
 * Redis 는 선택 사항이다. 연결되면 Redis 중복 가드가 함께 작동하고, 연결되지 않으면 가드가 fail-open 으로
 * 통과시키므로 테스트는 DB 잠금만으로 중복이 막히는지를 검증한다 (CI 는 Redis 없이 이 경로를 검증).
 *
 * 실행 조건: {@code realtime.postgres.integration=true} 와 PostgreSQL 접속 속성.
 * CI 의 기존 test 단계가 이미 이 속성을 넘기므로 워크플로 변경은 필요 없다.
 * <pre>
 * ./gradlew test --tests '*CheerCommentDuplicateRacePostgresIntegrationTest' \
 *   -Drealtime.postgres.integration=true \
 *   -Drealtime.postgres.url=jdbc:postgresql://127.0.0.1:5432/bega_cheer \
 *   -Drealtime.postgres.username=... -Drealtime.postgres.password=... \
 *   [-Drealtime.redis.host=127.0.0.1 -Drealtime.redis.port=6379]
 * </pre>
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("integrationEnabled")
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class CheerCommentDuplicateRacePostgresIntegrationTest {

    private static final int REPEATED_ROUNDS = 20;

    @Autowired
    private CheerCommentService commentService;

    @Autowired
    private CheerPostRepo postRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    private UserEntity author;
    private TeamEntity team;

    static boolean integrationEnabled() {
        return Boolean.getBoolean("realtime.postgres.integration");
    }

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> requiredProperty("realtime.postgres.url"));
        registry.add("spring.datasource.username", () -> requiredProperty("realtime.postgres.username"));
        registry.add("spring.datasource.password", () -> System.getProperty("realtime.postgres.password", ""));
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @TestConfiguration
    static class RealRedisConfig {

        @Bean
        @Primary
        RedisConnectionFactory realRedisConnectionFactory() {
            return new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                    System.getProperty("realtime.redis.host", "127.0.0.1"),
                    Integer.getInteger("realtime.redis.port", 6379)));
        }

        @Bean
        @Primary
        StringRedisTemplate realStringRedisTemplate(RedisConnectionFactory realRedisConnectionFactory) {
            return new StringRedisTemplate(realRedisConnectionFactory);
        }
    }

    @BeforeEach
    void setUp() {
        if (!teamRepo.existsById("LG")) {
            team = teamRepo.save(TeamEntity.builder()
                    .teamId("LG").teamName("LG Twins").teamShortName("LG").city("Seoul").build());
        } else {
            team = teamRepo.findById("LG").orElseThrow();
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        author = userRepo.save(UserEntity.builder()
                .email("c11author+" + suffix + "@test.com")
                .name("Author")
                .handle("a" + suffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .favoriteTeam(team)
                .build());
    }

    @Test
    void concurrentDuplicateCommentsAllowOnlyOneSave() throws InterruptedException {
        RaceResult result = raceTwoIdenticalComments("같은 댓글");

        assertThat(result.successIds()).hasSize(1);
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0)).isInstanceOf(DuplicateCommentException.class);
        assertThat(postRepo.findById(result.postId()).orElseThrow().getCommentCount()).isEqualTo(1);
    }

    @Test
    void repeatedRacesNeverDoubleSave() throws InterruptedException {
        for (int round = 0; round < REPEATED_ROUNDS; round++) {
            RaceResult result = raceTwoIdenticalComments("반복 라운드 댓글 " + round);

            assertThat(result.successIds())
                    .as("round %d must save exactly one comment", round)
                    .hasSize(1);
        }
    }

    private RaceResult raceTwoIdenticalComments(String content) throws InterruptedException {
        CheerPost post = postRepo.saveAndFlush(CheerPost.builder()
                .author(author)
                .team(team)
                .content("Comment Target")
                .postType(PostType.NORMAL)
                .build());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserEntity commenter = userRepo.save(UserEntity.builder()
                .email("c11commenter+" + suffix + "@test.com")
                .name("Commenter")
                .handle("cm" + suffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .favoriteTeam(team)
                .build());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());
        List<Long> successIds = Collections.synchronizedList(new ArrayList<>());
        Long postId = post.getId();
        CreateCommentReq req = new CreateCommentReq(content);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(commenter.getId(), null, List.of()));
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    successIds.add(commentService.addComment(postId, req, commenter).id());
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    SecurityContextHolder.clearContext();
                    done.countDown();
                }
            });
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        return new RaceResult(postId, List.copyOf(successIds), List.copyOf(errors));
    }

    private record RaceResult(Long postId, List<Long> successIds, List<Throwable> errors) {
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing required system property: " + name);
        }
        return value;
    }
}
