package com.example.dm.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.dm.entity.DmMessage;

public interface DmMessageRepository extends JpaRepository<DmMessage, Long> {

    List<DmMessage> findByRoomIdOrderByCreatedAtAsc(Long roomId);

    Optional<DmMessage> findByRoomIdAndSenderIdAndClientMessageId(Long roomId, Long senderId, String clientMessageId);
}
