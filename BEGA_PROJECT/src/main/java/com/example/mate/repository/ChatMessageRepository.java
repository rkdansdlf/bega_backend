package com.example.mate.repository;

import com.example.mate.entity.ChatMessage;
import com.example.mate.entity.Party.PartyStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Query("""
            select count(message.id)
              from Party party, ChatMessage message
             where party.id = message.partyId
               and party.hostId = :userId
               and party.status in :activeStatuses
               and message.senderId <> :userId
               and message.createdAt > coalesce(
                   party.hostLastReadChatAt,
                   party.createdAt,
                   :fallback)
            """)
    long countUnreadMessagesForHostedParties(
            @Param("userId") Long userId,
            @Param("activeStatuses") List<PartyStatus> activeStatuses,
            @Param("fallback") Instant fallback);

    @Query("""
            select count(message.id)
              from PartyApplication application, Party party, ChatMessage message
             where application.partyId = party.id
               and message.partyId = party.id
               and application.applicantId = :userId
               and application.isApproved = true
               and party.status in :activeStatuses
               and message.senderId <> :userId
               and message.createdAt > coalesce(
                   application.lastReadChatAt,
                   application.createdAt,
                   :fallback)
            """)
    long countUnreadMessagesForApprovedApplications(
            @Param("userId") Long userId,
            @Param("activeStatuses") List<PartyStatus> activeStatuses,
            @Param("fallback") Instant fallback);

    Optional<ChatMessage> findByPartyIdAndSenderIdAndClientMessageId(
            Long partyId,
            Long senderId,
            String clientMessageId);
}
