package com.example.dm.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dm.entity.DmRoom;

public interface DmRoomRepository extends JpaRepository<DmRoom, Long> {

    Optional<DmRoom> findByParticipantOneIdAndParticipantTwoId(Long participantOneId, Long participantTwoId);
}
