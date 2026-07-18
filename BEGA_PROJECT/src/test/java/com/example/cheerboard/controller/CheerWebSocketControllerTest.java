package com.example.cheerboard.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.cheerboard.service.CheerBattleService;
import com.example.common.realtime.RealtimeMessagePublisher;

class CheerWebSocketControllerTest {

    private final CheerBattleService battleService = mock(CheerBattleService.class);
    private final RealtimeMessagePublisher realtimeMessagePublisher = mock(RealtimeMessagePublisher.class);
    private final CheerWebSocketController controller = new CheerWebSocketController(
            battleService,
            realtimeMessagePublisher);

    @Test
    void votePublishesLatestStatsThroughSharedFanOut() {
        Principal principal = () -> "42";
        Map<String, Integer> stats = Map.of("KIA", 3);
        when(battleService.getGameStats("game-1")).thenReturn(stats);

        controller.vote("game-1", "KIA", principal);

        verify(battleService).vote("game-1", "KIA", 42L);
        verify(realtimeMessagePublisher).broadcast("/topic/battle/game-1", stats);
    }

    @Test
    void voteHasNoImplicitSpringReturnDestination() throws Exception {
        org.assertj.core.api.Assertions.assertThat(CheerWebSocketController.class.getDeclaredMethod(
                "vote",
                String.class,
                String.class,
                Principal.class).getReturnType()).isEqualTo(Void.TYPE);
    }
}
