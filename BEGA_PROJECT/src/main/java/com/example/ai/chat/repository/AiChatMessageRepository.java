package com.example.ai.chat.repository;

import com.example.ai.chat.entity.AiChatMessage;
import com.example.ai.chat.entity.AiChatMessageRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findBySession_IdOrderByCreatedAtAsc(Long sessionId);

    Optional<AiChatMessage> findTopBySession_IdAndIdLessThanAndRoleOrderByIdDesc(
            Long sessionId,
            Long messageId,
            AiChatMessageRole role);
}
