package com.example.media.service;

import com.example.common.exception.BadRequestBusinessException;
import com.example.media.entity.MediaDomain;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class MediaObjectKeyGuard {

    private static final String MEDIA_ASSET_NOT_FOUND = "MEDIA_ASSET_NOT_FOUND";
    private static final String MEDIA_ASSET_NOT_FOUND_MESSAGE = "업로드를 다시 완료한 뒤 저장해주세요.";

    private static final String LEGACY_PROFILE_PREFIX = "profiles/";
    private static final String LEGACY_DIARY_PREFIX = "diary/";
    private static final String LEGACY_CHAT_PREFIX = "chat/";
    private static final String LEGACY_PROFILE_FEED_SEGMENT = "feed";
    private static final String LEGACY_PROFILE_FEED_V2_SEGMENT = "feed-v2";
    private static final String LEGACY_PROFILE_FEED_V3_SEGMENT = "feed-v3";

    private final MediaLinkService mediaLinkService;

    public void requireDiaryWriteKey(String objectKey, Long ownerUserId, Long diaryId, boolean allowLegacy) {
        KeyDecision decision = evaluateDiaryWriteKey(objectKey, ownerUserId, diaryId, allowLegacy);
        if (!decision.allowed()) {
            throwNotFound();
        }
    }

    public boolean canReadDiaryKey(String objectKey, Long ownerUserId, Long diaryId) {
        return evaluateDiaryReadKey(objectKey, ownerUserId, diaryId).allowed();
    }

    public void requireProfileWriteKey(String objectKey, Long ownerUserId) {
        KeyDecision decision = evaluateProfileWriteKey(objectKey, ownerUserId);
        if (!decision.allowed()) {
            throwNotFound();
        }
    }

    public boolean canReadProfileKey(String objectKey, Long ownerUserId, boolean allowFeedPath) {
        return evaluateProfileReadKey(objectKey, ownerUserId, allowFeedPath).allowed();
    }

    public void requireChatWriteKey(String objectKey, Long ownerUserId) {
        KeyDecision decision = evaluateChatWriteKey(objectKey, ownerUserId);
        if (!decision.allowed()) {
            throwNotFound();
        }
    }

    public boolean canReadChatKey(String objectKey, Long ownerUserId) {
        return evaluateChatReadKey(objectKey, ownerUserId).allowed();
    }

    KeyDecision evaluateDiaryWriteKey(String objectKey, Long ownerUserId, Long diaryId, boolean allowLegacy) {
        if (!allowLegacy && isLegacyDiaryKey(objectKey)) {
            return new KeyDecision(false, DecisionReason.UNSUPPORTED_KEY);
        }
        return evaluateDiaryReadKey(objectKey, ownerUserId, diaryId);
    }

    KeyDecision evaluateDiaryReadKey(String objectKey, Long ownerUserId, Long diaryId) {
        if (!StringUtils.hasText(objectKey) || ownerUserId == null) {
            return new KeyDecision(false, DecisionReason.BLANK);
        }
        if (isManagedKey(MediaDomain.DIARY, objectKey)) {
            return managedDecision(ownerUserId, MediaDomain.DIARY, objectKey);
        }
        if (!isLegacyDiaryKey(objectKey)) {
            return new KeyDecision(false, DecisionReason.UNSUPPORTED_KEY);
        }
        String[] segments = objectKey.split("/");
        if (segments.length < 4 || !isLong(segments[1]) || !isLong(segments[2])) {
            return new KeyDecision(false, DecisionReason.LEGACY_FORMAT_INVALID);
        }
        Long keyOwnerUserId = Long.parseLong(segments[1]);
        Long keyDiaryId = Long.parseLong(segments[2]);
        if (!ownerUserId.equals(keyOwnerUserId)) {
            return new KeyDecision(false, DecisionReason.LEGACY_OWNER_MISMATCH);
        }
        if (diaryId == null || !diaryId.equals(keyDiaryId)) {
            return new KeyDecision(false, DecisionReason.LEGACY_DIARY_ID_MISMATCH);
        }
        return new KeyDecision(true, DecisionReason.LEGACY_OWNER_MATCH);
    }

    KeyDecision evaluateProfileWriteKey(String objectKey, Long ownerUserId) {
        KeyDecision decision = evaluateProfileReadKey(objectKey, ownerUserId, false);
        if (decision.allowed() && isLegacyProfileFeedKey(objectKey)) {
            return new KeyDecision(false, DecisionReason.PROFILE_FEED_PATH_IN_PRIMARY);
        }
        return decision;
    }

    KeyDecision evaluateProfileReadKey(String objectKey, Long ownerUserId, boolean allowFeedPath) {
        if (!StringUtils.hasText(objectKey) || ownerUserId == null) {
            return new KeyDecision(false, DecisionReason.BLANK);
        }
        if (isManagedKey(MediaDomain.PROFILE, objectKey)) {
            if (!allowFeedPath && objectKey.startsWith("media/profile-feed/")) {
                return new KeyDecision(false, DecisionReason.PROFILE_FEED_PATH_IN_PRIMARY);
            }
            return managedDecision(ownerUserId, MediaDomain.PROFILE, objectKey);
        }
        if (!objectKey.startsWith(LEGACY_PROFILE_PREFIX)) {
            return new KeyDecision(false, DecisionReason.UNSUPPORTED_KEY);
        }
        String[] segments = objectKey.split("/");
        if (segments.length < 3 || !isLong(segments[1])) {
            return new KeyDecision(false, DecisionReason.LEGACY_FORMAT_INVALID);
        }
        Long keyOwnerUserId = Long.parseLong(segments[1]);
        if (!ownerUserId.equals(keyOwnerUserId)) {
            return new KeyDecision(false, DecisionReason.LEGACY_OWNER_MISMATCH);
        }
        if (!allowFeedPath && isLegacyProfileFeedKey(objectKey)) {
            return new KeyDecision(false, DecisionReason.PROFILE_FEED_PATH_IN_PRIMARY);
        }
        return new KeyDecision(true, DecisionReason.LEGACY_OWNER_MATCH);
    }

    KeyDecision evaluateChatWriteKey(String objectKey, Long ownerUserId) {
        return evaluateChatReadKey(objectKey, ownerUserId);
    }

    KeyDecision evaluateChatReadKey(String objectKey, Long ownerUserId) {
        if (!StringUtils.hasText(objectKey) || ownerUserId == null) {
            return new KeyDecision(false, DecisionReason.BLANK);
        }
        if (isManagedKey(MediaDomain.CHAT, objectKey)) {
            return managedDecision(ownerUserId, MediaDomain.CHAT, objectKey);
        }
        if (!objectKey.startsWith(LEGACY_CHAT_PREFIX)) {
            return new KeyDecision(false, DecisionReason.UNSUPPORTED_KEY);
        }
        String[] segments = objectKey.split("/");
        if (segments.length < 3 || !isLong(segments[1])) {
            return new KeyDecision(false, DecisionReason.LEGACY_FORMAT_INVALID);
        }
        Long keyOwnerUserId = Long.parseLong(segments[1]);
        if (!ownerUserId.equals(keyOwnerUserId)) {
            return new KeyDecision(false, DecisionReason.LEGACY_OWNER_MISMATCH);
        }
        return new KeyDecision(true, DecisionReason.LEGACY_OWNER_MATCH);
    }

    private KeyDecision managedDecision(Long ownerUserId, MediaDomain domain, String objectKey) {
        if (isReadyManagedKey(ownerUserId, domain, objectKey)) {
            return new KeyDecision(true, DecisionReason.LEGACY_OWNER_MATCH);
        }
        return new KeyDecision(false, DecisionReason.MANAGED_ASSET_INVALID);
    }

    private boolean isReadyManagedKey(Long ownerUserId, MediaDomain domain, String objectKey) {
        try {
            requireReadyManagedKey(ownerUserId, domain, objectKey);
            return true;
        } catch (BadRequestBusinessException e) {
            log.warn("Managed media key validation failed. domain={}, key={}, code={}",
                    domain,
                    objectKey,
                    e.getCode());
            return false;
        }
    }

    private void requireReadyManagedKey(Long ownerUserId, MediaDomain domain, String objectKey) {
        if (ownerUserId == null || !StringUtils.hasText(objectKey) || !domain.isManagedPath(objectKey)) {
            throwNotFound();
        }
        try {
            mediaLinkService.resolveReadyAssets(ownerUserId, domain, List.of(objectKey));
        } catch (BadRequestBusinessException e) {
            throwNotFound();
        }
    }

    private boolean isManagedKey(MediaDomain domain, String objectKey) {
        return StringUtils.hasText(objectKey) && domain.isManagedPath(objectKey);
    }

    private boolean isOwnerProfileLegacyKey(String objectKey, Long ownerUserId) {
        return StringUtils.hasText(objectKey)
                && ownerUserId != null
                && objectKey.startsWith(LEGACY_PROFILE_PREFIX + ownerUserId + "/");
    }

    private boolean isLegacyProfileFeedKey(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }
        String[] segments = objectKey.split("/");
        return segments.length >= 4
                && "profiles".equals(segments[0])
                && (LEGACY_PROFILE_FEED_SEGMENT.equals(segments[2])
                || LEGACY_PROFILE_FEED_V2_SEGMENT.equals(segments[2])
                || LEGACY_PROFILE_FEED_V3_SEGMENT.equals(segments[2]));
    }

    private boolean isLegacyDiaryKey(String objectKey) {
        return StringUtils.hasText(objectKey) && objectKey.startsWith(LEGACY_DIARY_PREFIX);
    }

    private boolean isOwnerDiaryLegacyKey(String objectKey, Long ownerUserId, Long diaryId) {
        return StringUtils.hasText(objectKey)
                && ownerUserId != null
                && diaryId != null
                && objectKey.startsWith(LEGACY_DIARY_PREFIX + ownerUserId + "/" + diaryId + "/");
    }

    private boolean isOwnerChatLegacyKey(String objectKey, Long ownerUserId) {
        return StringUtils.hasText(objectKey)
                && ownerUserId != null
                && objectKey.startsWith(LEGACY_CHAT_PREFIX + ownerUserId + "/");
    }

    private boolean isLong(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private void throwNotFound() {
        throw new BadRequestBusinessException(MEDIA_ASSET_NOT_FOUND, MEDIA_ASSET_NOT_FOUND_MESSAGE);
    }

    enum DecisionReason {
        LEGACY_OWNER_MATCH,
        LEGACY_OWNER_MISMATCH,
        LEGACY_DIARY_ID_MISMATCH,
        LEGACY_FORMAT_INVALID,
        PROFILE_FEED_PATH_IN_PRIMARY,
        MANAGED_ASSET_INVALID,
        UNSUPPORTED_KEY,
        BLANK
    }

    record KeyDecision(boolean allowed, DecisionReason reason) {
    }
}
