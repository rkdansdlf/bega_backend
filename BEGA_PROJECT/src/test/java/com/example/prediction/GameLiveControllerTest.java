package com.example.prediction;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.example.common.exception.GlobalExceptionHandler;

class GameLiveControllerTest {

    @Test
    void getLiveSnapshotReturnsSnapshot() throws Exception {
        GameLiveService gameLiveService = mock(GameLiveService.class);
        GameLiveRelayService gameLiveRelayService = mock(GameLiveRelayService.class);
        GameLiveController controller = new GameLiveController(gameLiveService, gameLiveRelayService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(gameLiveService.getLiveSnapshot("GAME-1", 10, 25)).thenReturn(
                GameLiveSnapshotDto.builder()
                        .gameId("GAME-1")
                        .gameStatus("LIVE")
                        .homeScore(3)
                        .awayScore(2)
                        .lastEventSeq(12)
                        .events(List.of())
                        .build());

        mockMvc.perform(get("/api/matches/GAME-1/live")
                        .param("afterSeq", "10")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value("GAME-1"))
                .andExpect(jsonPath("$.gameStatus").value("LIVE"))
                .andExpect(jsonPath("$.homeScore").value(3))
                .andExpect(jsonPath("$.lastEventSeq").value(12));

        verify(gameLiveService).getLiveSnapshot("GAME-1", 10, 25);
    }

    @Test
    void invalidGameIdReturnsBadRequest() throws Exception {
        GameLiveService gameLiveService = mock(GameLiveService.class);
        GameLiveRelayService gameLiveRelayService = mock(GameLiveRelayService.class);
        GameLiveController controller = new GameLiveController(gameLiveService, gameLiveRelayService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/matches/BAD ID/live"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_GAME_ID"));
    }

    @Test
    void getLiveSummariesRejectsTooManyGameIds() throws Exception {
        GameLiveService gameLiveService = mock(GameLiveService.class);
        GameLiveRelayService gameLiveRelayService = mock(GameLiveRelayService.class);
        GameLiveController controller = new GameLiveController(gameLiveService, gameLiveRelayService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        String gameIds = String.join(",", java.util.stream.IntStream.rangeClosed(1, 51)
                .mapToObj(index -> "GAME-" + index)
                .toList());

        mockMvc.perform(get("/api/matches/live").param("gameIds", gameIds))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TOO_MANY_GAME_IDS"));
    }

    @Test
    void getLiveSummariesDeduplicatesGameIds() throws Exception {
        GameLiveService gameLiveService = mock(GameLiveService.class);
        GameLiveRelayService gameLiveRelayService = mock(GameLiveRelayService.class);
        GameLiveController controller = new GameLiveController(gameLiveService, gameLiveRelayService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(gameLiveService.getLiveSummaries(List.of("GAME-1", "GAME-2"))).thenReturn(List.of(
                GameLiveSummaryDto.builder().gameId("GAME-1").gameStatus("LIVE").homeScore(1).awayScore(0).build(),
                GameLiveSummaryDto.builder().gameId("GAME-2").gameStatus("SCHEDULED").build()));

        mockMvc.perform(get("/api/matches/live").param("gameIds", "GAME-1,GAME-1,GAME-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gameId").value("GAME-1"))
                .andExpect(jsonPath("$[1].gameId").value("GAME-2"));

        verify(gameLiveService).getLiveSummaries(List.of("GAME-1", "GAME-2"));
    }

    @Test
    void getLiveRelaySnapshotReturnsRawRelayEvents() throws Exception {
        GameLiveService gameLiveService = mock(GameLiveService.class);
        GameLiveRelayService gameLiveRelayService = mock(GameLiveRelayService.class);
        GameLiveController controller = new GameLiveController(gameLiveService, gameLiveRelayService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        when(gameLiveRelayService.getRelaySnapshot("GAME-1", 10, 25)).thenReturn(
                GameRelaySnapshotDto.builder()
                        .gameId("GAME-1")
                        .lastRelayId(12)
                        .events(List.of(GameRelayEventDto.builder()
                                .relayId(12)
                                .playDescription("김도영 : 좌익수 왼쪽 2루타")
                                .build()))
                        .build());

        mockMvc.perform(get("/api/matches/GAME-1/live-relay")
                        .param("afterId", "10")
                        .param("limit", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameId").value("GAME-1"))
                .andExpect(jsonPath("$.lastRelayId").value(12))
                .andExpect(jsonPath("$.events[0].playDescription").value("김도영 : 좌익수 왼쪽 2루타"));

        verify(gameLiveRelayService).getRelaySnapshot("GAME-1", 10, 25);
    }

    @Test
    void getLiveRelayRejectsInvalidAfterId() throws Exception {
        GameLiveService gameLiveService = mock(GameLiveService.class);
        GameLiveRelayService gameLiveRelayService = mock(GameLiveRelayService.class);
        GameLiveController controller = new GameLiveController(gameLiveService, gameLiveRelayService);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(get("/api/matches/GAME-1/live-relay").param("afterId", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_AFTER_ID"));
    }
}
