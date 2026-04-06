package com.example.cheerboard.integration;

import com.example.admin.service.AdminService;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.LikeToggleResponse;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.service.CheerInteractionService;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:cheer_like_reconcile;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=30000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
@Transactional
class CheerLikeCountReconciliationIntegrationTest {

    @Autowired
    private CheerInteractionService interactionService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private CheerPostRepo postRepo;

    @Autowired
    private CheerPostLikeRepo likeRepo;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepo;

    @Autowired
    private EntityManager entityManager;

    private TeamEntity team;
    private UserEntity author;
    private UserEntity liker;

    @BeforeEach
    void setUp() {
        if (!teamRepo.existsById("LG")) {
            team = teamRepo.save(TeamEntity.builder()
                    .teamId("LG")
                    .teamName("LG Twins")
                    .teamShortName("LG")
                    .city("Seoul")
                    .build());
        } else {
            team = teamRepo.findById("LG").orElseThrow();
        }

        author = createUser("Author", "author", "author");
        liker = createUser("Liker", "liker", "liker");
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("toggleLike likes a drifted post by exact row recount")
    void toggleLike_reconcilesDriftedLikeCountOnLike() {
        CheerPost post = createPost(author, 6);

        authenticate(author);

        LikeToggleResponse response = interactionService.toggleLike(post.getId(), author);
        clearPersistenceContext();

        assertThat(response.liked()).isTrue();
        assertThat(response.likes()).isEqualTo(1);
        assertThat(likeRepo.countByPostId(post.getId())).isEqualTo(1L);
        assertThat(postRepo.findLikeCountById(post.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("toggleLike unlikes a drifted post by exact row recount")
    void toggleLike_reconcilesDriftedLikeCountOnUnlike() {
        CheerPost post = createPost(author, 5);
        saveLike(post, author);

        authenticate(author);

        LikeToggleResponse response = interactionService.toggleLike(post.getId(), author);
        clearPersistenceContext();

        assertThat(response.liked()).isFalse();
        assertThat(response.likes()).isEqualTo(0);
        assertThat(likeRepo.countByPostId(post.getId())).isEqualTo(0L);
        assertThat(postRepo.findLikeCountById(post.getId())).isEqualTo(0);
    }

    @Test
    @DisplayName("deleteUser reconciles likecount for posts liked by the deleted user")
    void deleteUser_reconcilesAffectedPostLikeCounts() {
        CheerPost post = createPost(author, 4);
        saveLike(post, liker);

        adminService.deleteUser(liker.getId(), null);
        clearPersistenceContext();

        assertThat(likeRepo.countByPostId(post.getId())).isEqualTo(0L);
        assertThat(postRepo.findLikeCountById(post.getId())).isEqualTo(0);
        assertThat(userRepo.findById(liker.getId())).get().extracting(UserEntity::isEnabled).isEqualTo(false);
    }

    private CheerPost createPost(UserEntity postAuthor, int storedLikeCount) {
        return postRepo.saveAndFlush(CheerPost.builder()
                .author(postAuthor)
                .team(team)
                .content("count reconcile target")
                .postType(PostType.NORMAL)
                .shareMode(CheerPost.ShareMode.INTERNAL_REPOST)
                .likeCount(storedLikeCount)
                .build());
    }

    private void saveLike(CheerPost post, UserEntity user) {
        CheerPostLike like = new CheerPostLike();
        like.setId(new CheerPostLike.Id(post.getId(), user.getId()));
        like.setPost(post);
        like.setUser(user);
        likeRepo.saveAndFlush(like);
    }

    private UserEntity createUser(String name, String handlePrefix, String emailPrefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepo.save(UserEntity.builder()
                .email(emailPrefix + "+" + suffix + "@test.com")
                .name(name)
                .handle(handlePrefix + suffix)
                .uniqueId(UUID.randomUUID())
                .provider("LOCAL")
                .role("ROLE_USER")
                .build());
    }

    private void authenticate(UserEntity user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void clearPersistenceContext() {
        entityManager.flush();
        entityManager.clear();
    }
}
