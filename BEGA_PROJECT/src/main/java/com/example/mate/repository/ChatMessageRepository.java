package com.example.mate.repository;

import com.example.mate.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 파티별 채팅 메시지 조회 (생성일 기준 오름차순)
    List<ChatMessage> findByPartyIdOrderByCreatedAtAsc(Long partyId);

    // 파티별 최근 메시지 조회
    ChatMessage findTopByPartyIdOrderByCreatedAtDesc(Long partyId);

    // 나중에 읽은 메시지 개수 조회
    long countByPartyIdAndCreatedAtAfter(Long partyId, java.time.Instant createdAt);

    // 본인 발신 제외 unread 메시지 개수 조회
    long countByPartyIdAndCreatedAtAfterAndSenderIdNot(
            Long partyId,
            java.time.Instant createdAt,
            Long senderId);
}
