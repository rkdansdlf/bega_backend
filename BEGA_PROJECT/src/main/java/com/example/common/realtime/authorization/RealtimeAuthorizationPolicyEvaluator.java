package com.example.common.realtime.authorization;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.dm.service.DmRoomService;
import com.example.mate.repository.PartyRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RealtimeAuthorizationPolicyEvaluator {

    private final PartyRepository partyRepository;
    private final DmRoomService dmRoomService;

    @Transactional(readOnly = true)
    public boolean canAccessParty(Long partyId, Long userId) {
        return partyRepository.findAccessibleByIdAndParticipantId(partyId, userId).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean canAccessDmRoom(Long roomId, Long userId) {
        return dmRoomService.canSubscribe(roomId, userId);
    }
}
