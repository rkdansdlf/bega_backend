package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameEventEntity;
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.repository.GameEventRepository;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.validation.BaseballDataIntegrityGuard;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;

@ExtendWith(MockitoExtension.class)
class GameLiveServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameEventRepository gameEventRepository;

    @Mock
    private BaseballDataIntegrityGuard baseballDataIntegrityGuard;

    private GameLiveService gameLiveService;

    @BeforeEach
    void setUp() {
        gameLiveService = new GameLiveService(gameRepository, gameEventRepository, baseballDataIntegrityGuard);
    }

    @Test
    void liveSnapshotUsesLatestEventScoreAndReturnsDeltaEvents() {
        GameEntity game = game("GAME-1", LocalDate.now(), "SCHEDULED", 1, 1);
        GameDetailHeaderProjection header = header("GAME-1", LocalTime.of(18, 30));
        GameEventEntity deltaEvent = event("GAME-1", 4, 6, "TOP", 2, 2, "안타");
        GameEventEntity latestEvent = event("GAME-1", 5, 6, "BOTTOM", 3, 2, "득점");
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-1")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-1")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdAndEventSeqGreaterThanOrderByEventSeqAsc(
                eq("GAME-1"),
                eq(3),
                any(Pageable.class))).thenReturn(List.of(deltaEvent));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-1")).thenReturn(Optional.of(latestEvent));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-1", 3, 50);

        assertThat(snapshot.getGameStatus()).isEqualTo("LIVE");
        assertThat(snapshot.getHomeScore()).isEqualTo(3);
        assertThat(snapshot.getAwayScore()).isEqualTo(2);
        assertThat(snapshot.getCurrentInning()).isEqualTo(6);
        assertThat(snapshot.getCurrentInningHalf()).isEqualTo("BOTTOM");
        assertThat(snapshot.getLastEventSeq()).isEqualTo(5);
        assertThat(snapshot.getEvents()).extracting(GameLiveEventDto::getEventSeq).containsExactly(4);
    }

    @Test
    void initialLiveSnapshotReturnsRecentEventsInAscendingSequence() {
        GameEntity game = game("GAME-2", LocalDate.now(), "LIVE", 0, 0);
        GameDetailHeaderProjection header = header("GAME-2", LocalTime.of(18, 30));
        GameEventEntity latestEvent = event("GAME-2", 9, 3, "TOP", 1, 1, "뜬공");
        GameEventEntity previousEvent = event("GAME-2", 8, 3, "TOP", 1, 0, "2루타");
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-2")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-2")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-2"), any(Pageable.class)))
                .thenReturn(List.of(latestEvent, previousEvent));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-2")).thenReturn(Optional.of(latestEvent));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-2", null, 50);

        assertThat(snapshot.getEvents()).extracting(GameLiveEventDto::getEventSeq).containsExactly(8, 9);
        assertThat(snapshot.getLastEventSeq()).isEqualTo(9);
    }

    @Test
    void scheduledGameWithoutEventsIsAllowed() {
        GameEntity game = game("GAME-3", LocalDate.now().plusDays(1), "SCHEDULED", null, null);
        GameDetailHeaderProjection header = header("GAME-3", LocalTime.of(18, 30));
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-3")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-3")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-3"), any(Pageable.class)))
                .thenReturn(List.of());
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-3")).thenReturn(Optional.empty());

        assertThatCode(() -> gameLiveService.getLiveSnapshot("GAME-3", null, 50))
                .doesNotThrowAnyException();
        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-3", null, 50);
        assertThat(snapshot.getGameStatus()).isEqualTo("SCHEDULED");
        assertThat(snapshot.getEvents()).isEmpty();
    }

    @Test
    void liveGameWithoutEventsRequiresManualBaseballData() {
        GameEntity game = game("GAME-4", LocalDate.now(), "LIVE", 0, 0);
        GameDetailHeaderProjection header = header("GAME-4", LocalTime.of(18, 30));
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-4")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-4")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-4"), any(Pageable.class)))
                .thenReturn(List.of());
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-4")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> gameLiveService.getLiveSnapshot("GAME-4", null, 50))
                .isInstanceOf(ManualBaseballDataRequiredException.class)
                .satisfies(error -> {
                    ManualBaseballDataRequiredException exception = (ManualBaseballDataRequiredException) error;
                    ManualBaseballDataRequest request = (ManualBaseballDataRequest) exception.getData();
                    assertThat(request.missingItems().get(0).key()).isEqualTo("game_events");
                });
    }

    @Test
    void liveSummariesPreserveRequestOrderAndUseLatestEvents() {
        GameEntity first = game("GAME-5", LocalDate.now(), "LIVE", 0, 0);
        GameEntity second = game("GAME-6", LocalDate.now().plusDays(1), "SCHEDULED", null, null);
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_summary", "GAME-5")).thenReturn(first);
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_summary", "GAME-6")).thenReturn(second);
        when(gameEventRepository.findLatestByGameIds(List.of("GAME-5", "GAME-6")))
                .thenReturn(List.of(event("GAME-5", 7, 2, "BOTTOM", 4, 3, "홈런")));

        List<GameLiveSummaryDto> summaries = gameLiveService.getLiveSummaries(List.of("GAME-5", "GAME-6"));

        assertThat(summaries).extracting(GameLiveSummaryDto::getGameId).containsExactly("GAME-5", "GAME-6");
        assertThat(summaries.get(0).getHomeScore()).isEqualTo(4);
        assertThat(summaries.get(0).getLastEventSeq()).isEqualTo(7);
        assertThat(summaries.get(1).getGameStatus()).isEqualTo("SCHEDULED");
    }

    private GameEntity game(String gameId, LocalDate gameDate, String status, Integer homeScore, Integer awayScore) {
        return GameEntity.builder()
                .gameId(gameId)
                .gameDate(gameDate)
                .homeTeam("LG")
                .awayTeam("KT")
                .gameStatus(status)
                .homeScore(homeScore)
                .awayScore(awayScore)
                .seasonId(gameDate == null ? null : gameDate.getYear())
                .build();
    }

    private GameEventEntity event(
            String gameId,
            int eventSeq,
            int inning,
            String inningHalf,
            int homeScore,
            int awayScore,
            String description) {
        return GameEventEntity.builder()
                .gameId(gameId)
                .eventSeq(eventSeq)
                .inning(inning)
                .inningHalf(inningHalf)
                .outs(1)
                .batterName("타자")
                .pitcherName("투수")
                .description(description)
                .eventType("PLAY")
                .resultCode("H")
                .homeScore(homeScore)
                .awayScore(awayScore)
                .updatedAt(LocalDateTime.of(2026, 4, 29, 20, eventSeq))
                .build();
    }

    private GameDetailHeaderProjection header(String gameId, LocalTime startTime) {
        GameDetailHeaderProjection header = mock(GameDetailHeaderProjection.class);
        when(header.getStartTime()).thenReturn(startTime);
        return header;
    }
}
