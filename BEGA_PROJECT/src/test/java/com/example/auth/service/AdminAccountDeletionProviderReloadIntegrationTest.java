package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.entity.UserProvider;
import com.example.auth.repository.AccountDeletionTokenRepository;
import com.example.auth.repository.AccountSecurityEventRepository;
import com.example.auth.repository.OAuthEmailChallengeRepository;
import com.example.auth.repository.PasswordResetTokenRepository;
import com.example.auth.repository.RefreshRepository;
import com.example.auth.repository.TrustedDeviceRepository;
import com.example.auth.repository.UserProviderRepository;
import com.example.auth.repository.UserRepository;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPostLike;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.repo.CheerPostLikeRepo;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import com.example.mate.service.PartyService;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@DataJpaTest
@Import({AccountDeletionFinalizationService.class, AccountDeletionIdentityService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminAccountDeletionProviderReloadIntegrationTest {

    @Autowired
    private AccountDeletionFinalizationService finalizationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProviderRepository userProviderRepository;

    @Autowired
    private CheerPostLikeRepo likeRepository;

    @Autowired
    private CheerPostRepo cheerPostRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private PartyService partyService;

    @MockitoBean
    private AccountDeletionTokenRepository accountDeletionTokenRepository;

    @MockitoBean
    private AccountDeletionContentService contentService;

    @MockitoBean
    private AccountDeletionReviewService reviewService;

    @MockitoBean
    private AccountDeletionFinancialService financialService;

    @MockitoBean
    private RefreshRepository refreshRepository;

    @MockitoBean
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @MockitoBean
    private OAuthEmailChallengeRepository oauthEmailChallengeRepository;

    @MockitoBean
    private TrustedDeviceRepository trustedDeviceRepository;

    @MockitoBean
    private AccountSecurityEventRepository accountSecurityEventRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private CacheManager cacheManager;

    @Test
    void adminDeletionReloadsUserBeforeLazyProviderAnonymizationAfterBulkClear() {
        LocalDateTime dueAt = LocalDateTime.now().minusDays(2);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        UserEntity user = userRepository.saveAndFlush(UserEntity.builder()
                .uniqueId(UUID.randomUUID())
                .handle("@pr" + suffix)
                .name("Admin deletion provider test")
                .email("provider-reload-" + suffix + "@example.test")
                .role("ROLE_USER")
                .enabled(false)
                .pendingDeletion(true)
                .deletionRequestedAt(null)
                .deletionScheduledFor(dueAt)
                .tokenVersion(1)
                .build());
        userProviderRepository.saveAndFlush(UserProvider.builder()
                .user(user)
                .provider("google")
                .providerId("provider-reload-" + suffix)
                .email("provider-reload-" + suffix + "@example.test")
                .build());
        TeamEntity team = teamRepository.saveAndFlush(TeamEntity.builder()
                .teamId("PR" + suffix.substring(0, 3))
                .teamName("Provider reload test team")
                .teamShortName("PRT")
                .city("Synthetic")
                .build());
        CheerPost post = cheerPostRepository.saveAndFlush(CheerPost.builder()
                .team(team)
                .author(user)
                .content("admin deletion lazy-provider regression")
                .postType(PostType.NORMAL)
                .likeCount(1)
                .build());
        CheerPostLike like = new CheerPostLike();
        like.setId(new CheerPostLike.Id(post.getId(), user.getId()));
        like.setPost(post);
        like.setUser(user);
        likeRepository.saveAndFlush(like);
        entityManager.clear();

        doAnswer(invocation -> {
            entityManager.clear();
            return null;
        }).when(contentService).anonymize(user.getId());

        assertThat(finalizationService.finalizeImmediatelyAfterAdminDeletion(user.getId(), 77L)).isTrue();

        entityManager.clear();
        UserEntity finalizedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(finalizedUser.getEmail()).isEqualTo("deleted-" + user.getId() + "@deleted.invalid");
        assertThat(userProviderRepository.findByUserId(user.getId())).isEmpty();
        assertThat(likeRepository.countByPostId(post.getId())).isZero();
        assertThat(cheerPostRepository.findLikeCountById(post.getId())).isZero();
    }
}
