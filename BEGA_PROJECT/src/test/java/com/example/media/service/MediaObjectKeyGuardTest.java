package com.example.media.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.BadRequestBusinessException;
import com.example.media.entity.MediaDomain;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaObjectKeyGuardTest {

    @InjectMocks
    private MediaObjectKeyGuard guard;

    @Mock
    private MediaLinkService mediaLinkService;

    @Test
    @DisplayName("managed key는 MediaLinkService READY 검증을 통과해야 한다")
    void requireChatWriteKey_acceptsReadyManagedKey() {
        when(mediaLinkService.resolveReadyAssets(3L, MediaDomain.CHAT, List.of("media/chat/3/77.webp")))
                .thenReturn(Map.of());

        guard.requireChatWriteKey("media/chat/3/77.webp", 3L);

        verify(mediaLinkService).resolveReadyAssets(3L, MediaDomain.CHAT, List.of("media/chat/3/77.webp"));
    }

    @Test
    @DisplayName("managed key owner 불일치는 존재 여부를 숨기고 거부한다")
    void requireChatWriteKey_rejectsManagedOwnerMismatchAsNotFound() {
        doThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "not found"))
                .when(mediaLinkService)
                .resolveReadyAssets(3L, MediaDomain.CHAT, List.of("media/chat/9/77.webp"));

        assertNotFound(() -> guard.requireChatWriteKey("media/chat/9/77.webp", 3L));
    }

    @Test
    @DisplayName("managed key domain 불일치는 존재 여부를 숨기고 거부한다")
    void requireChatWriteKey_rejectsManagedDomainMismatchAsNotFound() {
        doThrow(new BadRequestBusinessException("MEDIA_ASSET_DOMAIN_MISMATCH", "domain mismatch"))
                .when(mediaLinkService)
                .resolveReadyAssets(3L, MediaDomain.CHAT, List.of("media/chat/3/77.webp"));

        assertNotFound(() -> guard.requireChatWriteKey("media/chat/3/77.webp", 3L));
    }

    @Test
    @DisplayName("managed key non-READY 상태는 존재 여부를 숨기고 거부한다")
    void requireChatWriteKey_rejectsManagedNonReadyAsNotFound() {
        doThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "not ready"))
                .when(mediaLinkService)
                .resolveReadyAssets(3L, MediaDomain.CHAT, List.of("media/chat/3/77.webp"));

        assertNotFound(() -> guard.requireChatWriteKey("media/chat/3/77.webp", 3L));
    }

    @Test
    @DisplayName("managed key missing asset은 존재 여부를 숨기고 거부한다")
    void requireChatWriteKey_rejectsMissingManagedAssetAsNotFound() {
        doThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_FOUND", "not found"))
                .when(mediaLinkService)
                .resolveReadyAssets(3L, MediaDomain.CHAT, List.of("media/chat/3/missing.webp"));

        assertNotFound(() -> guard.requireChatWriteKey("media/chat/3/missing.webp", 3L));
    }

    @Test
    @DisplayName("managed key 검증 실패는 audit decision에서 MANAGED_ASSET_INVALID로 분류한다")
    void evaluateChatReadKey_classifiesManagedValidationFailure() {
        doThrow(new BadRequestBusinessException("MEDIA_ASSET_NOT_READY", "not ready"))
                .when(mediaLinkService)
                .resolveReadyAssets(3L, MediaDomain.CHAT, List.of("media/chat/3/77.webp"));

        MediaObjectKeyGuard.KeyDecision decision = guard.evaluateChatReadKey("media/chat/3/77.webp", 3L);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.MANAGED_ASSET_INVALID);
    }

    @Test
    @DisplayName("profile legacy key는 owner 일치 시 허용하고 owner 불일치 시 거부한다")
    void requireProfileWriteKey_validatesLegacyOwner() {
        guard.requireProfileWriteKey("profiles/7/avatar.webp", 7L);

        assertNotFound(() -> guard.requireProfileWriteKey("profiles/99/avatar.webp", 7L));
    }

    @Test
    @DisplayName("profile legacy audit decision은 owner 불일치와 형식 오류를 분류한다")
    void evaluateProfileReadKey_classifiesCrossOwnerAndMalformedLegacyKeys() {
        MediaObjectKeyGuard.KeyDecision crossOwner =
                guard.evaluateProfileReadKey("profiles/99/avatar.webp", 7L, true);
        MediaObjectKeyGuard.KeyDecision malformed =
                guard.evaluateProfileReadKey("profiles/not-number/avatar.webp", 7L, true);

        assertThat(crossOwner.allowed()).isFalse();
        assertThat(crossOwner.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.LEGACY_OWNER_MISMATCH);
        assertThat(malformed.allowed()).isFalse();
        assertThat(malformed.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.LEGACY_FORMAT_INVALID);
    }

    @Test
    @DisplayName("profile write 경로는 legacy feed 하위 경로를 거부하고 render 경로는 허용한다")
    void profileLegacyFeed_isRejectedForWriteAllowedForRender() {
        assertNotFound(() -> guard.requireProfileWriteKey("profiles/7/feed-v3/avatar.webp", 7L));

        assertThat(guard.canReadProfileKey("profiles/7/feed-v3/avatar.webp", 7L, true)).isTrue();
        assertThat(guard.canReadProfileKey("profiles/7/feed-v3/avatar.webp", 7L, false)).isFalse();
        assertThat(guard.evaluateProfileWriteKey("profiles/7/feed-v3/avatar.webp", 7L).reason())
                .isEqualTo(MediaObjectKeyGuard.DecisionReason.PROFILE_FEED_PATH_IN_PRIMARY);
    }

    @Test
    @DisplayName("diary create context는 legacy diary key를 거부한다")
    void requireDiaryWriteKey_rejectsLegacyDiaryKeyWithoutDiaryId() {
        assertNotFound(() -> guard.requireDiaryWriteKey("diary/7/10/photo.webp", 7L, null, false));
    }

    @Test
    @DisplayName("diary update context는 owner와 diary id가 모두 일치하는 legacy key만 허용한다")
    void requireDiaryWriteKey_validatesLegacyOwnerAndDiaryId() {
        guard.requireDiaryWriteKey("diary/7/10/photo.webp", 7L, 10L, true);

        assertNotFound(() -> guard.requireDiaryWriteKey("diary/8/10/photo.webp", 7L, 10L, true));
        assertNotFound(() -> guard.requireDiaryWriteKey("diary/7/11/photo.webp", 7L, 10L, true));
    }

    @Test
    @DisplayName("diary legacy audit decision은 owner 불일치, diary id 불일치, 형식 오류를 분류한다")
    void evaluateDiaryReadKey_classifiesLegacyFailures() {
        MediaObjectKeyGuard.KeyDecision crossOwner =
                guard.evaluateDiaryReadKey("diary/8/10/photo.webp", 7L, 10L);
        MediaObjectKeyGuard.KeyDecision wrongDiary =
                guard.evaluateDiaryReadKey("diary/7/11/photo.webp", 7L, 10L);
        MediaObjectKeyGuard.KeyDecision malformed =
                guard.evaluateDiaryReadKey("diary/7/photo.webp", 7L, 10L);

        assertThat(crossOwner.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.LEGACY_OWNER_MISMATCH);
        assertThat(wrongDiary.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.LEGACY_DIARY_ID_MISMATCH);
        assertThat(malformed.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.LEGACY_FORMAT_INVALID);
    }

    @Test
    @DisplayName("chat legacy key는 sender owner 일치 시만 허용한다")
    void requireChatWriteKey_validatesLegacyOwner() {
        guard.requireChatWriteKey("chat/3/avatar.webp", 3L);

        assertNotFound(() -> guard.requireChatWriteKey("chat/99/avatar.webp", 3L));
    }

    @Test
    @DisplayName("chat legacy audit decision은 owner 불일치와 형식 오류를 분류한다")
    void evaluateChatReadKey_classifiesLegacyFailures() {
        MediaObjectKeyGuard.KeyDecision crossOwner =
                guard.evaluateChatReadKey("chat/99/avatar.webp", 3L);
        MediaObjectKeyGuard.KeyDecision malformed =
                guard.evaluateChatReadKey("chat/not-number/avatar.webp", 3L);

        assertThat(crossOwner.allowed()).isFalse();
        assertThat(crossOwner.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.LEGACY_OWNER_MISMATCH);
        assertThat(malformed.allowed()).isFalse();
        assertThat(malformed.reason()).isEqualTo(MediaObjectKeyGuard.DecisionReason.LEGACY_FORMAT_INVALID);
    }

    private void assertNotFound(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BadRequestBusinessException.class, ex ->
                        assertThat(ex.getCode()).isEqualTo("MEDIA_ASSET_NOT_FOUND"));
    }
}
