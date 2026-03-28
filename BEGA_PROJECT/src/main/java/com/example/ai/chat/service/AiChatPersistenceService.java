package com.example.ai.chat.service;

import com.example.ai.chat.dto.ChatFavoriteItem;
import com.example.ai.chat.dto.ChatSessionSummary;
import com.example.ai.chat.dto.CreateAssistantChatMessageRequest;
import com.example.ai.chat.dto.CreateUserChatMessageRequest;
import com.example.ai.chat.dto.StoredChatMessage;
import com.example.ai.chat.entity.AiChatFavorite;
import com.example.ai.chat.entity.AiChatMessage;
import com.example.ai.chat.entity.AiChatMessageRole;
import com.example.ai.chat.entity.AiChatMessageStatus;
import com.example.ai.chat.entity.AiChatSession;
import com.example.ai.chat.repository.AiChatFavoriteRepository;
import com.example.ai.chat.repository.AiChatMessageRepository;
import com.example.ai.chat.repository.AiChatSessionRepository;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.ForbiddenBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiChatPersistenceService {

    private static final int SESSION_TITLE_LIMIT = 60;
    private static final int SESSION_PREVIEW_LIMIT = 220;

    private final AiChatSessionRepository sessionRepository;
    private final AiChatMessageRepository messageRepository;
    private final AiChatFavoriteRepository favoriteRepository;
    private final ObjectMapper objectMapper;

    public List<ChatSessionSummary> listSessions(Long currentUserId) {
        Long userId = requireUserId(currentUserId);
        return sessionRepository.findByUserIdOrderByLastMessageAtDescCreatedAtDesc(userId).stream()
                .map(this::toSessionSummary)
                .toList();
    }

    @Transactional
    public ChatSessionSummary createSession(Long currentUserId) {
        Long userId = requireUserId(currentUserId);
        AiChatSession session = sessionRepository.save(AiChatSession.builder()
                .userId(userId)
                .title(AiChatSession.DEFAULT_TITLE)
                .build());
        return toSessionSummary(session);
    }

    public List<StoredChatMessage> listMessages(Long currentUserId, Long sessionId) {
        Long userId = requireUserId(currentUserId);
        AiChatSession session = getOwnedSession(userId, sessionId);
        List<AiChatMessage> messages = messageRepository.findBySession_IdOrderByCreatedAtAsc(session.getId());
        Set<Long> favoriteMessageIds = messages.isEmpty()
                ? Set.of()
                : favoriteRepository.findByUserIdAndMessage_IdIn(
                                userId,
                                messages.stream().map(AiChatMessage::getId).toList())
                        .stream()
                        .map(favorite -> favorite.getMessage().getId())
                        .collect(Collectors.toSet());
        return messages.stream()
                .map(message -> toStoredChatMessage(message, favoriteMessageIds.contains(message.getId())))
                .toList();
    }

    @Transactional
    public StoredChatMessage addUserMessage(
            Long currentUserId,
            Long sessionId,
            CreateUserChatMessageRequest request) {
        Long userId = requireUserId(currentUserId);
        AiChatSession session = getOwnedSession(userId, sessionId);
        String content = normalizeContent(request.content());
        boolean firstUserMessage = session.getMessageCount() == null || session.getMessageCount() == 0;
        if (firstUserMessage) {
            session.setTitle(buildSessionTitle(content));
        }
        touchSession(session, content);

        AiChatMessage message = messageRepository.save(AiChatMessage.builder()
                .session(session)
                .role(AiChatMessageRole.USER)
                .status(AiChatMessageStatus.COMPLETED)
                .content(content)
                .build());

        return toStoredChatMessage(message, false);
    }

    @Transactional
    public StoredChatMessage addAssistantMessage(
            Long currentUserId,
            Long sessionId,
            CreateAssistantChatMessageRequest request) {
        Long userId = requireUserId(currentUserId);
        AiChatSession session = getOwnedSession(userId, sessionId);
        String content = normalizeContent(request.content());
        AiChatMessageStatus status = AiChatMessageStatus.fromRequest(request.status());

        touchSession(session, content);

        AiChatMessage message = messageRepository.save(AiChatMessage.builder()
                .session(session)
                .role(AiChatMessageRole.ASSISTANT)
                .status(status)
                .content(content)
                .verified(request.verified())
                .cached(request.cached())
                .intent(trimToNull(request.intent()))
                .strategy(trimToNull(request.strategy()))
                .finishReason(trimToNull(request.finishReason()))
                .cancelled(resolveCancelled(status, request.cancelled()))
                .errorCode(trimToNull(request.errorCode()))
                .plannerMode(trimToNull(request.plannerMode()))
                .plannerCacheHit(request.plannerCacheHit())
                .toolExecutionMode(trimToNull(request.toolExecutionMode()))
                .fallbackReason(trimToNull(request.fallbackReason()))
                .metadataJson(serializeJsonNode(request.metadata()))
                .citationsJson(serializeJsonNode(request.citations()))
                .toolCallsJson(serializeJsonNode(request.toolCalls()))
                .build());

        return toStoredChatMessage(message, false);
    }

    @Transactional
    public void deleteSession(Long currentUserId, Long sessionId) {
        Long userId = requireUserId(currentUserId);
        AiChatSession session = getOwnedSession(userId, sessionId);
        sessionRepository.delete(session);
    }

    public List<ChatFavoriteItem> listFavorites(Long currentUserId) {
        Long userId = requireUserId(currentUserId);
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toFavoriteItem)
                .toList();
    }

    @Transactional
    public ChatFavoriteItem addFavorite(Long currentUserId, Long messageId) {
        Long userId = requireUserId(currentUserId);
        AiChatFavorite existing = favoriteRepository.findByUserIdAndMessage_Id(userId, messageId).orElse(null);
        if (existing != null) {
            return toFavoriteItem(existing);
        }

        AiChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new NotFoundBusinessException("AI_CHAT_MESSAGE_NOT_FOUND", "메시지를 찾을 수 없습니다."));
        ensureMessageOwnedByUser(message, userId);
        if (message.getRole() != AiChatMessageRole.ASSISTANT) {
            throw new ForbiddenBusinessException("AI_CHAT_FAVORITE_ONLY_ASSISTANT", "assistant 메시지만 즐겨찾기할 수 있습니다.");
        }
        if (message.getStatus() != AiChatMessageStatus.COMPLETED) {
            throw new ForbiddenBusinessException("AI_CHAT_FAVORITE_ONLY_COMPLETED", "완료된 assistant 메시지만 즐겨찾기할 수 있습니다.");
        }

        AiChatFavorite favorite = favoriteRepository.save(AiChatFavorite.builder()
                .userId(userId)
                .message(message)
                .build());
        return toFavoriteItem(favorite);
    }

    @Transactional
    public void removeFavorite(Long currentUserId, Long messageId) {
        Long userId = requireUserId(currentUserId);
        favoriteRepository.deleteByUserIdAndMessage_Id(userId, messageId);
    }

    private ChatSessionSummary toSessionSummary(AiChatSession session) {
        return new ChatSessionSummary(
                session.getId(),
                session.getTitle(),
                session.getMessageCount(),
                session.getLastMessagePreview(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getLastMessageAt());
    }

    private StoredChatMessage toStoredChatMessage(AiChatMessage message, boolean favorite) {
        return new StoredChatMessage(
                message.getId(),
                message.getSession().getId(),
                message.getRole(),
                message.getStatus(),
                message.getContent(),
                message.getVerified(),
                message.getCached(),
                message.getIntent(),
                message.getStrategy(),
                message.getFinishReason(),
                message.isCancelled(),
                message.getErrorCode(),
                message.getPlannerMode(),
                message.getPlannerCacheHit(),
                message.getToolExecutionMode(),
                message.getFallbackReason(),
                deserializeJson(message.getMetadataJson()),
                deserializeJson(message.getCitationsJson()),
                deserializeJson(message.getToolCallsJson()),
                favorite,
                message.getCreatedAt(),
                message.getUpdatedAt());
    }

    private ChatFavoriteItem toFavoriteItem(AiChatFavorite favorite) {
        AiChatMessage message = favorite.getMessage();
        String prompt = messageRepository.findTopBySession_IdAndIdLessThanAndRoleOrderByIdDesc(
                        message.getSession().getId(),
                        message.getId(),
                        AiChatMessageRole.USER)
                .map(AiChatMessage::getContent)
                .orElse(null);
        return new ChatFavoriteItem(
                message.getId(),
                message.getSession().getId(),
                message.getSession().getTitle(),
                message.getContent(),
                prompt,
                favorite.getCreatedAt(),
                message.getCreatedAt());
    }

    private void touchSession(AiChatSession session, String content) {
        Instant now = Instant.now();
        session.setLastMessageAt(now);
        session.setUpdatedAt(now);
        session.setLastMessagePreview(buildPreview(content));
        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 1);
    }

    private void ensureMessageOwnedByUser(AiChatMessage message, Long userId) {
        Long ownerId = message.getSession().getUserId();
        if (!Objects.equals(ownerId, userId)) {
            throw new ForbiddenBusinessException("AI_CHAT_FORBIDDEN", "본인 대화만 접근할 수 있습니다.");
        }
    }

    private AiChatSession getOwnedSession(Long userId, Long sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NotFoundBusinessException("AI_CHAT_SESSION_NOT_FOUND", "세션을 찾을 수 없습니다."));
    }

    private Long requireUserId(Long currentUserId) {
        if (currentUserId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        return currentUserId;
    }

    private String normalizeContent(String rawContent) {
        String normalized = trimToNull(rawContent);
        if (normalized == null) {
            throw new IllegalArgumentException("메시지 내용이 비어 있습니다.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean resolveCancelled(AiChatMessageStatus status, Boolean cancelled) {
        return Boolean.TRUE.equals(cancelled) || status == AiChatMessageStatus.CANCELLED;
    }

    private String buildSessionTitle(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= SESSION_TITLE_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, SESSION_TITLE_LIMIT - 3).trim() + "...";
    }

    private String buildPreview(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= SESSION_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, SESSION_PREVIEW_LIMIT - 3).trim() + "...";
    }

    private String serializeJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("메타데이터를 저장할 수 없습니다.");
        }
    }

    private JsonNode deserializeJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return null;
        }
        try {
            return objectMapper.readTree(rawJson);
        } catch (Exception e) {
            Map<String, String> fallback = new LinkedHashMap<>();
            fallback.put("raw", rawJson);
            return objectMapper.valueToTree(fallback);
        }
    }
}
