package com.example.dm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dm.entity.DmMessage;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

    List<DmMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    Optional<DmMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);

    @Query("""
            select m from DmMessage m
            where m.roomId in :roomIds
              and m.createdAt = (
                  select max(m2.createdAt) from DmMessage m2 where m2.roomId = m.roomId
              )
            """)
    List<DmMessage> findLatestMessagePerRoom(@Param("roomIds") List<Long> roomIds);
}
