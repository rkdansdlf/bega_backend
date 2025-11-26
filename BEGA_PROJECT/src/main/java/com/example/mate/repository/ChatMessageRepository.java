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
}