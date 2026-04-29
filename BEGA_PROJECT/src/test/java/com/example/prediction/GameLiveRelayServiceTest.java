package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Pageable;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GamePlayByPlayEntity;
import com.example.kbo.repository.GamePlayByPlayRepository;
import com.example.kbo.validation.BaseballDataIntegrityGuard;

@ExtendWith(MockitoExtension.class)
class GameLiveRelayServiceTest {

    @Mock
    private GamePlayByPlayRepository gamePlayByPlayRepository;

    @Mock
    private BaseballDataIntegrityGuard baseballDataIntegrityGuard;

    private GameLiveRelayService gameLiveRelayService;

    @BeforeEach
    void setUp() {
        gameLiveRelayService = new GameLiveRelayService(gamePlayByPlayRepository, baseballDataIntegrityGuard);
    }

    @Test
    void initialRelaySnapshotReturnsRecentEventsInAscendingIdOrder() {
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_relay", "GAME-1"))
                .thenReturn(game("GAME-1"));
        GamePlayByPlayEntity latest = relay(12, "GAME-1", "득점 장면");
        GamePlayByPlayEntity previous = relay(11, "GAME-1", "초구 스트라이크");
        when(gamePlayByPlayRepository.findByGameIdOrderByIdDesc(
                eq("GAME-1"),
                argThat((Pageable pageable) -> pageable.getPageSize() == 50)))
                .thenReturn(List.of(latest, previous));
        when(gamePlayByPlayRepository.findFirstByGameIdOrderByIdDesc("GAME-1"))
                .thenReturn(Optional.of(latest));

        GameRelaySnapshotDto snapshot = gameLiveRelayService.getRelaySnapshot("GAME-1", null, 50);

        assertThat(snapshot.getGameId()).isEqualTo("GAME-1");
        assertThat(snapshot.getLastRelayId()).isEqualTo(12);
        assertThat(snapshot.getEvents()).extracting(GameRelayEventDto::getRelayId).containsExactly(11, 12);
        assertThat(snapshot.getEvents()).extracting(GameRelayEventDto::getPlayDescription)
                .containsExactly("초구 스트라이크", "득점 장면");
    }

    @Test
    void relaySnapshotReturnsDeltaAfterId() {
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_relay", "GAME-2"))
                .thenReturn(game("GAME-2"));
        GamePlayByPlayEntity delta = relay(15, "GAME-2", "김도영 : 좌익수 왼쪽 2루타");
        when(gamePlayByPlayRepository.findByGameIdAndIdGreaterThanOrderByIdAsc(
                eq("GAME-2"),
                eq(14),
                argThat((Pageable pageable) -> pageable.getPageSize() == 25)))
                .thenReturn(List.of(delta));
        when(gamePlayByPlayRepository.findFirstByGameIdOrderByIdDesc("GAME-2"))
                .thenReturn(Optional.of(delta));

        GameRelaySnapshotDto snapshot = gameLiveRelayService.getRelaySnapshot("GAME-2", 14, 25);

        assertThat(snapshot.getLastRelayId()).isEqualTo(15);
        assertThat(snapshot.getEvents()).extracting(GameRelayEventDto::getRelayId).containsExactly(15);
        assertThat(snapshot.getEvents().get(0).getPlayDescription()).isEqualTo("김도영 : 좌익수 왼쪽 2루타");
    }

    @Test
    void emptyRelaySnapshotIsAllowed() {
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_relay", "GAME-3"))
                .thenReturn(game("GAME-3"));
        when(gamePlayByPlayRepository.findByGameIdOrderByIdDesc(eq("GAME-3"), argThat((Pageable pageable) -> true)))
                .thenReturn(List.of());
        when(gamePlayByPlayRepository.findFirstByGameIdOrderByIdDesc("GAME-3"))
                .thenReturn(Optional.empty());

        GameRelaySnapshotDto snapshot = gameLiveRelayService.getRelaySnapshot("GAME-3", null, 50);

        assertThat(snapshot.getLastRelayId()).isNull();
        assertThat(snapshot.getEvents()).isEmpty();
    }

    @Test
    void relayLimitIsClamped() {
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_relay", "GAME-4"))
                .thenReturn(game("GAME-4"));
        when(gamePlayByPlayRepository.findByGameIdOrderByIdDesc(
                eq("GAME-4"),
                argThat((Pageable pageable) -> pageable.getPageSize() == 200)))
                .thenReturn(List.of());
        when(gamePlayByPlayRepository.findFirstByGameIdOrderByIdDesc("GAME-4"))
                .thenReturn(Optional.empty());

        GameRelaySnapshotDto snapshot = gameLiveRelayService.getRelaySnapshot("GAME-4", null, 999);

        assertThat(snapshot.getEvents()).isEmpty();
    }

    private GameEntity game(String gameId) {
        return GameEntity.builder()
                .gameId(gameId)
                .gameDate(LocalDate.now())
                .gameStatus("SCHEDULED")
                .build();
    }

    private GamePlayByPlayEntity relay(int id, String gameId, String description) {
        return GamePlayByPlayEntity.builder()
                .id(id)
                .gameId(gameId)
                .inning(7)
                .inningHalf("BOTTOM")
                .pitcherName("투수")
                .batterName("타자")
                .playDescription(description)
                .eventType("PLAY")
                .result("H")
                .createdAt(LocalDateTime.of(2026, 4, 29, 20, id % 60))
                .updatedAt(LocalDateTime.of(2026, 4, 29, 20, id % 60))
                .build();
    }
}
