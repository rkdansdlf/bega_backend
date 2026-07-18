package com.example.common.realtime.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.dm.service.DmRoomService;
import com.example.mate.entity.Party;
import com.example.mate.repository.PartyRepository;

class RealtimeAuthorizationPolicyEvaluatorTest {

    private final PartyRepository partyRepository = mock(PartyRepository.class);
    private final DmRoomService dmRoomService = mock(DmRoomService.class);
    private RealtimeAuthorizationPolicyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new RealtimeAuthorizationPolicyEvaluator(partyRepository, dmRoomService);
    }

    @Test
    void partyAccessUsesExistingHostOrApprovedParticipantQuery() {
        given(partyRepository.findAccessibleByIdAndParticipantId(10L, 123L))
                .willReturn(Optional.of(Party.builder().id(10L).hostId(999L).build()));

        assertThat(evaluator.canAccessParty(10L, 123L)).isTrue();
        verify(partyRepository).findAccessibleByIdAndParticipantId(10L, 123L);
    }

    @Test
    void missingAccessiblePartyIsDenied() {
        given(partyRepository.findAccessibleByIdAndParticipantId(10L, 123L))
                .willReturn(Optional.empty());

        assertThat(evaluator.canAccessParty(10L, 123L)).isFalse();
    }

    @Test
    void dmAccessDelegatesParticipantAndBlockPolicyWithoutDuplication() {
        given(dmRoomService.canSubscribe(77L, 123L)).willReturn(true);

        assertThat(evaluator.canAccessDmRoom(77L, 123L)).isTrue();
        verify(dmRoomService).canSubscribe(77L, 123L);
    }

    @Test
    void dmAccessDelegatesOrdinaryDenyToExistingParticipantAndBlockPolicy() {
        given(dmRoomService.canSubscribe(77L, 123L)).willReturn(false);

        assertThat(evaluator.canAccessDmRoom(77L, 123L)).isFalse();
        verify(dmRoomService).canSubscribe(77L, 123L);
    }
}
