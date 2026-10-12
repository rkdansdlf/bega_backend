package com.example.dm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dm.entity.DmMessage;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update DmMessage message set message.content = :redactedContent where message.senderId = :senderId")
    int anonymizeBySenderId(
            @Param("senderId") Long senderId,
            @Param("redactedContent") String redactedContent);

    List<DmMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    List<DmMessage> findByRoomIdOrderByCreatedAtAscIdAsc(Long roomId);

    Optional<DmMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);

    @Query("""
            select m from DmMessage m
            where m.roomId in :roomIds
              and m.createdAt = (
                  select max(m2.createdAt) from DmMessage m2 where m2.roomId = m.roomId
              )
            """)
    List<DmMessage> findLatestMessagePerRoom(@Param("roomIds") List<Long> roomIds);

    @Query("""
            select distinct message.roomId
            from DmMessage message, DmRoom room
            where room.id = message.roomId
              and message.roomId in :roomIds
              and (
                    (room.participantOneId = :userId
                     and message.senderId = room.participantTwoId
                     and (
                           (room.participantOneLastReadMessageId is not null
                            and message.id > room.participantOneLastReadMessageId)
                           or (room.participantOneLastReadMessageId is null
                               and (room.participantOneLastReadAt is null
                                    or message.createdAt > room.participantOneLastReadAt))
                     ))
                    or (room.participantTwoId = :userId
                        and message.senderId = room.participantOneId
                        and (
                              (room.participantTwoLastReadMessageId is not null
                               and message.id > room.participantTwoLastReadMessageId)
                              or (room.participantTwoLastReadMessageId is null
                                  and (room.participantTwoLastReadAt is null
                                       or message.createdAt > room.participantTwoLastReadAt))
                        ))
              )
            """)
    List<Long> findUnreadRoomIdsForParticipant(
            @Param("userId") Long userId,
            @Param("roomIds") List<Long> roomIds);
}
