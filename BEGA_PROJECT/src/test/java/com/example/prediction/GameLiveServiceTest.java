package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.repository.GameDetailHeaderProjection;
import com.example.kbo.repository.GameEventRepository;
import com.example.kbo.repository.GameInningScoreRepository;
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
    private GameInningScoreRepository gameInningScoreRepository;

    @Mock
    private BaseballDataIntegrityGuard baseballDataIntegrityGuard;

    @Mock
    private PredictionLiveMetricsService predictionLiveMetricsService;

    private GameLiveService gameLiveService;

    @BeforeEach
    void setUp() {
        gameLiveService = new GameLiveService(
                gameRepository,
                gameEventRepository,
                gameInningScoreRepository,
                baseballDataIntegrityGuard,
                predictionLiveMetricsService);
        lenient().when(gameEventRepository.findByGameIdOrderByEventSeqDesc(anyString(), any(Pageable.class)))
                .thenReturn(List.of());
        lenient().when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(anyString()))
                .thenReturn(List.of());
        lenient().when(gameInningScoreRepository.findAllByGameIdInOrderByGameIdAscInningAscTeamSideAsc(any()))
                .thenReturn(List.of());
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
    void liveSnapshotDerivesInningScoresFromEventScoreDeltasWhenBoxScoreRowsAreMissing() {
        GameEntity game = game("GAME-EVENT-BOX", LocalDate.now(), "LIVE", null, null);
        GameDetailHeaderProjection header = header("GAME-EVENT-BOX", LocalTime.of(18, 30));
        GameEventEntity first = event("GAME-EVENT-BOX", 1, 1, "TOP", 0, 1, "원정 득점");
        GameEventEntity second = event("GAME-EVENT-BOX", 2, 1, "BOTTOM", 2, 1, "홈 득점");
        GameEventEntity third = event("GAME-EVENT-BOX", 3, 2, "TOP", 2, 3, "원정 추가 득점");
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-EVENT-BOX")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-EVENT-BOX")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-EVENT-BOX"), any(Pageable.class)))
                .thenReturn(List.of(third, second, first));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-EVENT-BOX")).thenReturn(Optional.of(third));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-EVENT-BOX", null, 50);

        assertThat(snapshot.getHomeScore()).isEqualTo(2);
        assertThat(snapshot.getAwayScore()).isEqualTo(3);
        assertThat(snapshot.getInningScores())
                .extracting(GameInningScoreDto::getInning, GameInningScoreDto::getTeamSide, GameInningScoreDto::getTeamCode, GameInningScoreDto::getRuns)
                .containsExactly(
                        tuple(1, "away", "KT", 1),
                        tuple(1, "home", "LG", 2),
                        tuple(2, "away", "KT", 2),
                        tuple(2, "home", "LG", 0));
        verify(predictionLiveMetricsService).recordLiveSnapshot("game_events", true);
    }

    @Test
    void liveSnapshotDerivesInningScoresFromFullEventHistoryDuringDeltaPolling() {
        GameEntity game = game("GAME-EVENT-POLL", LocalDate.now(), "LIVE", null, null);
        GameDetailHeaderProjection header = header("GAME-EVENT-POLL", LocalTime.of(18, 30));
        GameEventEntity first = event("GAME-EVENT-POLL", 1, 1, "TOP", 0, 1, "원정 득점");
        GameEventEntity second = event("GAME-EVENT-POLL", 2, 1, "BOTTOM", 2, 1, "홈 득점");
        GameEventEntity third = event("GAME-EVENT-POLL", 3, 2, "TOP", 2, 3, "원정 추가 득점");
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-EVENT-POLL")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-EVENT-POLL")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdAndEventSeqGreaterThanOrderByEventSeqAsc(
                eq("GAME-EVENT-POLL"),
                eq(2),
                any(Pageable.class))).thenReturn(List.of(third));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-EVENT-POLL"), any(Pageable.class)))
                .thenReturn(List.of(third, second, first));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-EVENT-POLL")).thenReturn(Optional.of(third));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-EVENT-POLL", 2, 50);

        assertThat(snapshot.getEvents()).extracting(GameLiveEventDto::getEventSeq).containsExactly(3);
        assertThat(snapshot.getInningScores())
                .extracting(GameInningScoreDto::getInning, GameInningScoreDto::getTeamSide, GameInningScoreDto::getRuns)
                .containsExactly(
                        tuple(1, "away", 1),
                        tuple(1, "home", 2),
                        tuple(2, "away", 2),
                        tuple(2, "home", 0));
        verify(predictionLiveMetricsService).recordLiveSnapshot("game_events", true);
    }

    @Test
    void liveSnapshotDerivesInningScoresFromFullEventHistoryWhenInitialEventListIsLimited() {
        GameEntity game = game("GAME-EVENT-LIMIT", LocalDate.now(), "LIVE", null, null);
        GameDetailHeaderProjection header = header("GAME-EVENT-LIMIT", LocalTime.of(18, 30));
        GameEventEntity first = event("GAME-EVENT-LIMIT", 1, 1, "TOP", 0, 1, "원정 득점");
        GameEventEntity second = event("GAME-EVENT-LIMIT", 2, 1, "BOTTOM", 2, 1, "홈 득점");
        GameEventEntity third = event("GAME-EVENT-LIMIT", 3, 2, "TOP", 2, 3, "원정 추가 득점");
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-EVENT-LIMIT")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-EVENT-LIMIT")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(
                eq("GAME-EVENT-LIMIT"),
                argThat(pageable -> pageable != null && pageable.getPageSize() == 1)))
                .thenReturn(List.of(third));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(
                eq("GAME-EVENT-LIMIT"),
                argThat(pageable -> pageable != null && pageable.getPageSize() == 200)))
                .thenReturn(List.of(third, second, first));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-EVENT-LIMIT")).thenReturn(Optional.of(third));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-EVENT-LIMIT", null, 1);

        assertThat(snapshot.getEvents()).extracting(GameLiveEventDto::getEventSeq).containsExactly(3);
        assertThat(snapshot.getInningScores())
                .extracting(GameInningScoreDto::getInning, GameInningScoreDto::getTeamSide, GameInningScoreDto::getRuns)
                .containsExactly(
                        tuple(1, "away", 1),
                        tuple(1, "home", 2),
                        tuple(2, "away", 2),
                        tuple(2, "home", 0));
        verify(predictionLiveMetricsService).recordLiveSnapshot("game_events", true);
    }

    @Test
    void liveSnapshotDoesNotDeriveZeroBoxRowsFromEventsWithoutScores() {
        GameEntity game = game("GAME-TEXT-EVENT", LocalDate.now(), "LIVE", null, null);
        GameDetailHeaderProjection header = header("GAME-TEXT-EVENT", LocalTime.of(18, 30));
        GameEventEntity textOnlyEvent = GameEventEntity.builder()
                .gameId("GAME-TEXT-EVENT")
                .eventSeq(1)
                .inning(1)
                .inningHalf("TOP")
                .description("점수 없는 문자 이벤트")
                .updatedAt(LocalDateTime.of(2026, 4, 29, 18, 31))
                .build();
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-TEXT-EVENT")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-TEXT-EVENT")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-TEXT-EVENT"), any(Pageable.class)))
                .thenReturn(List.of(textOnlyEvent));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-TEXT-EVENT")).thenReturn(Optional.of(textOnlyEvent));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-TEXT-EVENT", null, 50);

        assertThat(snapshot.getInningScores()).isEmpty();
        verify(predictionLiveMetricsService).recordLiveSnapshot("none", false);
    }

    @Test
    void liveSnapshotIncludesInningScoresAndFallsBackToInningTotalsWithoutEvents() {
        GameEntity game = game("GAME-INNING", LocalDate.now(), "LIVE", null, null);
        GameDetailHeaderProjection header = header("GAME-INNING", LocalTime.of(18, 30));
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-INNING")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-INNING")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-INNING"), any(Pageable.class)))
                .thenReturn(List.of());
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-INNING")).thenReturn(Optional.empty());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME-INNING")).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId("GAME-INNING").inning(1).teamSide("away").teamCode("KT").runs(2).build(),
                GameInningScoreEntity.builder().gameId("GAME-INNING").inning(1).teamSide("home").teamCode("LG").runs(1).build()
        ));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-INNING", null, 50);

        assertThat(snapshot.getHomeScore()).isEqualTo(1);
        assertThat(snapshot.getAwayScore()).isEqualTo(2);
        assertThat(snapshot.getEvents()).isEmpty();
        assertThat(snapshot.getInningScores()).hasSize(2);
        assertThat(snapshot.getInningScores())
                .extracting(GameInningScoreDto::getInning, GameInningScoreDto::getTeamSide, GameInningScoreDto::getRuns)
                .containsExactly(tuple(1, "away", 2), tuple(1, "home", 1));
    }

    @Test
    void liveSnapshotPrefersInningTotalsWhenLatestEventScoreIsStale() {
        GameEntity game = game("GAME-BOX", LocalDate.now(), "LIVE", 0, 0);
        GameDetailHeaderProjection header = header("GAME-BOX", LocalTime.of(18, 30));
        GameEventEntity latestEvent = event("GAME-BOX", 11, 2, "BOTTOM", 0, 0, "이벤트 점수 지연");
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-BOX")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-BOX")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-BOX"), any(Pageable.class)))
                .thenReturn(List.of(latestEvent));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-BOX")).thenReturn(Optional.of(latestEvent));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME-BOX")).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId("GAME-BOX").inning(1).teamSide("away").teamCode("KT").runs(0).build(),
                GameInningScoreEntity.builder().gameId("GAME-BOX").inning(1).teamSide("home").teamCode("LG").runs(2).build()
        ));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-BOX", null, 50);

        assertThat(snapshot.getHomeScore()).isEqualTo(2);
        assertThat(snapshot.getAwayScore()).isEqualTo(0);
        assertThat(snapshot.getLastEventSeq()).isEqualTo(11);
        verify(predictionLiveMetricsService).recordLiveSnapshot("inning_scores", true);
    }

    @Test
    void liveSnapshotFallsBackToLatestEventForSideMissingInningRows() {
        GameEntity game = game("GAME-PARTIAL-BOX", LocalDate.now(), "LIVE", 1, 3);
        GameDetailHeaderProjection header = header("GAME-PARTIAL-BOX", LocalTime.of(18, 30));
        GameEventEntity latestEvent = event("GAME-PARTIAL-BOX", 12, 2, "BOTTOM", 1, 3, "이벤트 점수");
        when(baseballDataIntegrityGuard.requireValidGame("prediction.live_snapshot", "GAME-PARTIAL-BOX")).thenReturn(game);
        when(gameRepository.findGameDetailHeaderByGameId("GAME-PARTIAL-BOX")).thenReturn(Optional.of(header));
        when(gameEventRepository.findByGameIdOrderByEventSeqDesc(eq("GAME-PARTIAL-BOX"), any(Pageable.class)))
                .thenReturn(List.of(latestEvent));
        when(gameEventRepository.findFirstByGameIdOrderByEventSeqDesc("GAME-PARTIAL-BOX")).thenReturn(Optional.of(latestEvent));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME-PARTIAL-BOX")).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId("GAME-PARTIAL-BOX").inning(1).teamSide("home").teamCode("LG").runs(2).build()
        ));

        GameLiveSnapshotDto snapshot = gameLiveService.getLiveSnapshot("GAME-PARTIAL-BOX", null, 50);

        assertThat(snapshot.getHomeScore()).isEqualTo(2);
        assertThat(snapshot.getAwayScore()).isEqualTo(3);
        verify(predictionLiveMetricsService).recordLiveSnapshot("mixed", true);
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
                    assertThat(request.missingItems().get(0).key()).isEqualTo("game_events_or_inning_scores");
                });
    }

    @Test
    void liveSummariesPreserveRequestOrderAndUseLatestEvents() {
        GameEntity first = game("GAME-5", LocalDate.now(), "LIVE", 0, 0);
        GameEntity second = game("GAME-6", LocalDate.now().plusDays(1), "SCHEDULED", null, null);
        when(baseballDataIntegrityGuard.requireValidGames("prediction.live_summary", List.of("GAME-5", "GAME-6")))
                .thenReturn(List.of(first, second));
        when(gameEventRepository.findLatestByGameIds(List.of("GAME-5", "GAME-6")))
                .thenReturn(List.of(event("GAME-5", 7, 2, "BOTTOM", 4, 3, "홈런")));

        List<GameLiveSummaryDto> summaries = gameLiveService.getLiveSummaries(List.of("GAME-5", "GAME-6"));

        assertThat(summaries).extracting(GameLiveSummaryDto::getGameId).containsExactly("GAME-5", "GAME-6");
        assertThat(summaries.get(0).getHomeScore()).isEqualTo(4);
        assertThat(summaries.get(0).getLastEventSeq()).isEqualTo(7);
        assertThat(summaries.get(1).getGameStatus()).isEqualTo("SCHEDULED");
        verify(baseballDataIntegrityGuard).requireValidGames("prediction.live_summary", List.of("GAME-5", "GAME-6"));
        verify(gameInningScoreRepository).findAllByGameIdInOrderByGameIdAscInningAscTeamSideAsc(List.of("GAME-5", "GAME-6"));
        verify(gameInningScoreRepository, never()).findAllByGameIdOrderByInningAscTeamSideAsc(anyString());
    }

    @Test
    void liveSummariesUseInningTotalsWithoutEvents() {
        GameEntity game = game("GAME-SUMMARY-INNING", LocalDate.now(), "SCHEDULED", null, null);
        when(baseballDataIntegrityGuard.requireValidGames("prediction.live_summary", List.of("GAME-SUMMARY-INNING")))
                .thenReturn(List.of(game));
        when(gameEventRepository.findLatestByGameIds(List.of("GAME-SUMMARY-INNING")))
                .thenReturn(List.of());
        when(gameInningScoreRepository.findAllByGameIdInOrderByGameIdAscInningAscTeamSideAsc(List.of("GAME-SUMMARY-INNING")))
                .thenReturn(List.of(
                        GameInningScoreEntity.builder().gameId("GAME-SUMMARY-INNING").inning(1).teamSide("away").teamCode("KT").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("GAME-SUMMARY-INNING").inning(1).teamSide("home").teamCode("LG").runs(2).build()
                ));

        List<GameLiveSummaryDto> summaries = gameLiveService.getLiveSummaries(List.of("GAME-SUMMARY-INNING"));

        assertThat(summaries).hasSize(1);
        assertThat(summaries.get(0).getGameStatus()).isEqualTo("LIVE");
        assertThat(summaries.get(0).getHomeScore()).isEqualTo(2);
        assertThat(summaries.get(0).getAwayScore()).isEqualTo(0);
        assertThat(summaries.get(0).getLastEventSeq()).isNull();
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
