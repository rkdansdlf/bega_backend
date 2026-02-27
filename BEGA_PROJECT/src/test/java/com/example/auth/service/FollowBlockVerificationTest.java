package com.example.auth.service;

import com.example.auth.dto.BlockToggleResponse;
import com.example.auth.dto.FollowToggleResponse;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserBlockRepository;
import com.example.auth.repository.UserFollowRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.config.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:bega_follow_block;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class FollowBlockVerificationTest {

    @Autowired
    private FollowService followService;

    @Autowired
    private BlockService blockService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserFollowRepository followRepository;

    @Autowired
    private UserBlockRepository blockRepository;

    @MockitoBean
    private CurrentUser currentUser;

    private UserEntity userA;
    private UserEntity userB;

    @BeforeEach
    void setUp() {
        // Create test users with unique data to avoid conflicts in shared DB
        String uniqueIdStr = UUID.randomUUID().toString().substring(0, 8);

        userA = new UserEntity();
        userA.setUniqueId(UUID.randomUUID());
        userA.setEmail("userA_" + uniqueIdStr + "@test.com");
        userA.setName("User A " + uniqueIdStr);
        userA.setHandle("user_a_" + uniqueIdStr);
        userA.setRole("ROLE_USER");
        userA = userRepository.save(userA);

        userB = new UserEntity();
        userB.setUniqueId(UUID.randomUUID());
        userB.setEmail("userB_" + uniqueIdStr + "@test.com");
        userB.setName("User B " + uniqueIdStr);
        userB.setHandle("user_b_" + uniqueIdStr);
        userB.setRole("ROLE_USER");
        userB = userRepository.save(userB);
    }

    @Test
    @DisplayName("User A follows User B")
    void testFollow() {
        // Given: User A is logged in
        when(currentUser.get()).thenReturn(userA);
        when(currentUser.getOrNull()).thenReturn(userA);

        // When: A follows B
        FollowToggleResponse response = followService.toggleFollow(userB.getId());

        // Then
        assertThat(response.isFollowing()).isTrue();
        assertThat(followService.isFollowing(userA.getId(), userB.getId())).isTrue();
        assertThat(response.getFollowerCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("User A unfollows User B")
    void testUnfollow() {
        // Given: User A follows User B
        when(currentUser.get()).thenReturn(userA);
        when(currentUser.getOrNull()).thenReturn(userA);
        followService.toggleFollow(userB.getId());

        // When: A toggles follow again
        FollowToggleResponse response = followService.toggleFollow(userB.getId());

        // Then
        assertThat(response.isFollowing()).isFalse();
        assertThat(followService.isFollowing(userA.getId(), userB.getId())).isFalse();
    }

    @Test
    @DisplayName("User A blocks User B")
    void testBlock() {
        // Given: User A is logged in
        when(currentUser.get()).thenReturn(userA);

        // When: A blocks B
        BlockToggleResponse response = blockService.toggleBlock(userB.getId());

        // Then
        assertThat(response.isBlocked()).isTrue();
        assertThat(blockService.isBlocked(userA.getId(), userB.getId())).isTrue();
    }

    @Test
    @DisplayName("Blocking user should remove follow relationship")
    void testBlockRemovesFollow() {
        // Given: User A follows User B
        when(currentUser.get()).thenReturn(userA);
        when(currentUser.getOrNull()).thenReturn(userA);
        followService.toggleFollow(userB.getId());
        assertThat(followService.isFollowing(userA.getId(), userB.getId())).isTrue();

        // When: A blocks B
        blockService.toggleBlock(userB.getId());

        // Then
        assertThat(blockService.isBlocked(userA.getId(), userB.getId())).isTrue();
        assertThat(followService.isFollowing(userA.getId(), userB.getId())).isFalse();
    }

    @Test
    @DisplayName("Cannot follow blocked user")
    void testCannotFollowBlockedUser() {
        // Given: User A blocks User B
        when(currentUser.get()).thenReturn(userA);
        when(currentUser.getOrNull()).thenReturn(userA);
        blockService.toggleBlock(userB.getId());

        // When/Then: A tries to follow B -> Exception
        assertThatThrownBy(() -> followService.toggleFollow(userB.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("차단 관계가 있어 팔로우할 수 없습니다.");
    }

    @Test
    @DisplayName("Cannot follow user who blocked me")
    void testCannotFollowUserWhoBlockedMe() {
        // Given: User B blocks User A
        when(currentUser.get()).thenReturn(userB);
        blockService.toggleBlock(userA.getId());

        // When/Then: User A tries to follow User B -> Exception
        when(currentUser.get()).thenReturn(userA);
        assertThatThrownBy(() -> followService.toggleFollow(userB.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("차단 관계가 있어 팔로우할 수 없습니다.");
    }
}
