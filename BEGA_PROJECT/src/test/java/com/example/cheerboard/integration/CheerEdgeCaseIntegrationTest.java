package com.example.cheerboard.integration;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.QuoteRepostReq;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.CheerPostService;
import com.example.common.exception.RepostNotAllowedException;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:cheer_edge;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.flyway.enabled=false",
                "spring.jpa.open-in-view=false",
                "spring.data.redis.host=127.0.0.1",
                "spring.data.redis.port=6379",
                "spring.data.redis.repositories.enabled=false",
                "storage.type=oci",
                "oci.s3.endpoint=http://localhost:4566",
                "oci.s3.access-key=test-access-key",
                "oci.s3.secret-key=test-secret-key",
                "oci.s3.bucket=test-bucket",
                "oci.s3.region=ap-seoul-1",
                "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration"
})
@Transactional
class CheerEdgeCaseIntegrationTest {

        @Autowired
        private CheerPostService postService;

        @Autowired
        private CheerPostRepo postRepo;

        @Autowired
        private UserRepository userRepo;

        @Autowired
        private TeamRepository teamRepo;

        private UserEntity author1;
        private UserEntity author2;
        private TeamEntity team;

        @BeforeEach
        void setUp() {
                if (!teamRepo.existsById("LG")) {
                        team = TeamEntity.builder().teamId("LG").teamName("LG Twins").teamShortName("LG").city("Seoul")
                                        .build();
                        teamRepo.save(team);
                } else {
                        team = teamRepo.findById("LG").orElseThrow();
                }

                author1 = createUser("Author1", "a1", "author1@test.com");
                author2 = createUser("Author2", "a2", "author2@test.com");
        }

        private UserEntity createUser(String name, String handlePrefix, String email) {
                String setupSuffix = UUID.randomUUID().toString().substring(0, 8);
                UserEntity u = UserEntity.builder()
                                .email(email)
                                .name(name)
                                .handle(handlePrefix + setupSuffix)
                                .uniqueId(UUID.randomUUID())
                                .provider("LOCAL")
                                .role("ROLE_USER")
                                .build();
                return userRepo.save(u);
        }

        private void authenticate(UserEntity user) {
                SecurityContextHolder.getContext().setAuthentication(
                                new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
        }

        @Test
        @DisplayName("Repost Cycle: Manually crafting a circular repost graph should be rejected over resolveRepostRootPost")
        void testRepostCycleDetection() {
                // Given: Create a root post by Author1
                authenticate(author1);
                CheerPost post1 = CheerPost.builder()
                                .author(author1)
                                .team(team)
                                .content("Root Content 1")
                                .postType(PostType.NORMAL)
                                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                                .build();
                CheerPost savedPost1 = postRepo.saveAndFlush(post1);

                // Create another root post by Author2
                authenticate(author2);
                CheerPost post2 = CheerPost.builder()
                                .author(author2)
                                .team(team)
                                .content("Root Content 2")
                                .postType(PostType.NORMAL)
                                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                                .build();
                CheerPost savedPost2 = postRepo.saveAndFlush(post2);

                // Now, manually manipulate the DB to create a cycle: post1 -> post2 -> post1
                savedPost1.setRepostOf(savedPost2);
                savedPost1.setRepostType(CheerPost.RepostType.QUOTE);
                postRepo.saveAndFlush(savedPost1);

                savedPost2.setRepostOf(savedPost1);
                savedPost2.setRepostType(CheerPost.RepostType.QUOTE);
                postRepo.saveAndFlush(savedPost2);

                // When/Then: Attempting to resolve the root of post2 should now throw a cycle
                // exception
                // Note: Creating a simple repost forces target resolution
                authenticate(author2);
                assertThatThrownBy(() -> postService.toggleRepost(savedPost2.getId(), author2))
                                .isInstanceOf(RepostNotAllowedException.class)
                                .hasMessageContaining("리포스트 대상이 비정상적으로 설정되어 있습니다.");
        }

        @Test
        @DisplayName("AI Moderation: Create Post should block profanity/inappropriate content")
        void testAIModerationBlocksInappropriatePost() {
                authenticate(author1);
                CreatePostReq req = new CreatePostReq("LG", "개새끼야 똑바로 해라", null, "NORMAL");

                assertThatThrownBy(() -> postService.createPost(req, author1))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("부적절한 내용이 포함되어 게시글을 작성할 수 없습니다.");
        }

        @Test
        @DisplayName("AI Moderation: Quote Repost should block profanity/inappropriate content")
        void testAIModerationBlocksInappropriateQuoteRepost() {
                authenticate(author1);
                CheerPost rootPost = CheerPost.builder()
                                .author(author1)
                                .team(team)
                                .content("Good Post")
                                .postType(PostType.NORMAL)
                                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                                .build();
                CheerPost savedRootPost = postRepo.saveAndFlush(rootPost);

                authenticate(author2);
                QuoteRepostReq quoteReq = new QuoteRepostReq("이런 씨발");

                assertThatThrownBy(() -> postService.createQuoteRepost(savedRootPost.getId(), quoteReq, author2))
                                .isInstanceOf(IllegalArgumentException.class)
                                .hasMessageContaining("부적절한 내용이 포함되어 리포스트를 작성할 수 없습니다.");
        }
}
