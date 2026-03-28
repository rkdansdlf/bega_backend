package com.example.ai.chat.repository;

import com.example.ai.chat.entity.AiChatFavorite;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatFavoriteRepository extends JpaRepository<AiChatFavorite, Long> {

    Optional<AiChatFavorite> findByUserIdAndMessage_Id(Long userId, Long messageId);

    List<AiChatFavorite> findByUserIdAndMessage_IdIn(Long userId, Collection<Long> messageIds);

    @EntityGraph(attributePaths = {"message", "message.session"})
    List<AiChatFavorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteByUserIdAndMessage_Id(Long userId, Long messageId);
}
