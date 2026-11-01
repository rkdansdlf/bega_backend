package com.example.mate.repository;

import com.example.mate.entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    boolean existsByPartyId(Long partyId);

    // 파티별 채팅 메시지 조회 (생성일 기준 오름차순)
    List<ChatMessage> findByPartyIdOrderByCreatedAtAsc(Long partyId);

    List<ChatMessage> findByPartyIdOrderByIdDesc(Long partyId, Pageable pageable);

    List<ChatMessage> findByPartyIdAndIdLessThanOrderByIdDesc(
            Long partyId,
            Long beforeId,
            Pageable pageable);

    // 파티별 최근 메시지 조회
    ChatMessage findTopByPartyIdOrderByCreatedAtDesc(Long partyId);

    // 나중에 읽은 메시지 개수 조회
    long countByPartyIdAndCreatedAtAfter(Long partyId, java.time.Instant createdAt);

    // 본인 발신 제외 unread 메시지 개수 조회
    long countByPartyIdAndCreatedAtAfterAndSenderIdNot(
            Long partyId,
            java.time.Instant createdAt,
            Long senderId);

    Optional<ChatMessage> findByPartyIdAndSenderIdAndClientMessageId(
            Long partyId,
            Long senderId,
            String clientMessageId);

    @Query("""
            select distinct message.imageUrl
              from ChatMessage message
             where message.senderId = :senderId
               and message.imageUrl is not null
            """)
    List<String> findImageUrlsBySenderId(@Param("senderId") Long senderId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update ChatMessage message
               set message.senderName = :redactedSenderName,
                   message.message = :redactedContent,
                   message.imageUrl = null
             where message.senderId = :senderId
            """)
    int anonymizeBySenderId(
            @Param("senderId") Long senderId,
            @Param("redactedSenderName") String redactedSenderName,
            @Param("redactedContent") String redactedContent);
}
