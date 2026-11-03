package com.example.dm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("""
            select room
            from DmRoom room
            where room.id = :roomId
              and (room.participantOneId = :userId or room.participantTwoId = :userId)
            """)
    Optional<DmRoom> findAccessibleByIdAndParticipantIdForUpdate(
            @Param("roomId") Long roomId,
            @Param("userId") Long userId);

    @Query("""
            select room from DmRoom room
            where room.participantOneId = :userId or room.participantTwoId = :userId
            order by room.createdAt desc
            """)
    List<DmRoom> findAllByParticipantId(@Param("userId") Long userId);
}
