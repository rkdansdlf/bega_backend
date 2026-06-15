package com.example.dm.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.dm.entity.DmRoom;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    Optional<DmRoom> findByParticipantOneIdAndParticipantTwoId(Long participantOneId, Long participantTwoId);

    @Query("""
            select room
            from DmRoom room
            where room.id = :roomId
              and (room.participantOneId = :userId or room.participantTwoId = :userId)
            """)
    Optional<DmRoom> findAccessibleByIdAndParticipantId(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId);
}
