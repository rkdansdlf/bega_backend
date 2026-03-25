package com.example.ai.chat.repository;

import com.example.ai.chat.entity.AiChatSession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    List<AiChatSession> findByUserIdOrderByLastMessageAtDescCreatedAtDesc(Long userId);

    Optional<AiChatSession> findByIdAndUserId(Long id, Long userId);
}
