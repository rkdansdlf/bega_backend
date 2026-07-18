package com.example.prediction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameDetailHeaderProjection;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PredictionServiceGameSyncTest extends PredictionServiceTestFixture {

    @Test
    void getGameDetailShouldReturnHeaderMetadataAndCollections() {
        GameDetailHeaderProjection detailHeader = buildGameDetailHeader("202603200001", LocalDate.of(2026, 3, 20));
        GameInningScoreEntity inning = GameInningScoreEntity.builder()
                .gameId("202603200001")
                .inning(1)
                .teamSide("home")
                .teamCode("LG")
                .runs(2)
                .isExtra(false)
                .build();
        GameSummaryEntity summary = GameSummaryEntity.builder()
                .gameId("202603200001")
                .summaryType("HIGHLIGHT")
                .playerId(7)
                .playerName("홍길동")
                .detailText("결승타")
                .build();

        when(gameRepository.findGameDetailHeaderByGameId("202603200001")).thenReturn(Optional.of(detailHeader));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("202603200001"))
                .thenReturn(List.of(inning));
        when(gameSummaryRepository.findAllByGameIdOrderBySummaryTypeAscIdAsc("202603200001"))
                .thenReturn(List.of(summary));

        GameDetailDto response = predictionService.getGameDetail("202603200001");

        assertThat(response.getGameId()).isEqualTo("202603200001");
        assertThat(response.getStadiumName()).isEqualTo("잠실야구장");
        assertThat(response.getAttendance()).isEqualTo(12345);
        assertThat(response.getStartTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(response.getInningScores()).hasSize(1);
        assertThat(response.getInningScores().get(0).getRuns()).isEqualTo(2);
        assertThat(response.getSummary()).hasSize(1);
        assertThat(response.getSummary().get(0).getDetail()).isEqualTo("결승타");
        verify(gameRepository, never()).findByGameId("202603200001");
        verify(gameMetadataRepository, never()).findByGameId("202603200001");
    }

    @Test
    void getGameDetailShouldHideStructuredInternalSummaryTypes() {
        GameDetailHeaderProjection detailHeader = buildGameDetailHeader("20260419HHLT0", LocalDate.of(2026, 4, 19));
        GameSummaryEntity winningHit = GameSummaryEntity.builder()
                .gameId("20260419HHLT0")
                .summaryType("결승타")
                .playerName("김태연")
                .detailText("5회말 결승타")
                .build();
        GameSummaryEntity reviewWpa = GameSummaryEntity.builder()
                .gameId("20260419HHLT0")
                .summaryType("리뷰_WPA")
                .playerName("기록")
                .detailText("{\"game_id\":\"20260419HHLT0\"}")
                .build();
        GameSummaryEntity preview = GameSummaryEntity.builder()
                .gameId("20260419HHLT0")
                .summaryType("프리뷰")
                .playerName("기록")
                .detailText("{\"game_id\":\"20260419HHLT0\"}")
                .build();

        when(gameRepository.findGameDetailHeaderByGameId("20260419HHLT0")).thenReturn(Optional.of(detailHeader));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("20260419HHLT0"))
                .thenReturn(List.of());
        when(gameSummaryRepository.findAllByGameIdOrderBySummaryTypeAscIdAsc("20260419HHLT0"))
                .thenReturn(List.of(winningHit, reviewWpa, preview));

        GameDetailDto response = predictionService.getGameDetail("20260419HHLT0");

        assertThat(response.getSummary())
                .extracting(GameSummaryDto::getType)
                .containsExactly("결승타");
    }

    @Test
    void getGameDetailShouldKeepMetadataFieldsNullWhenHeaderHasNoMetadata() {
        GameDetailHeaderProjection detailHeader = buildGameDetailHeaderWithoutMetadata("202603210001", LocalDate.of(2026, 3, 21));

        when(gameRepository.findGameDetailHeaderByGameId("202603210001")).thenReturn(Optional.of(detailHeader));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("202603210001"))
                .thenReturn(List.of());
        when(gameSummaryRepository.findAllByGameIdOrderBySummaryTypeAscIdAsc("202603210001"))
                .thenReturn(List.of());

        GameDetailDto response = predictionService.getGameDetail("202603210001");

        assertThat(response.getGameId()).isEqualTo("202603210001");
        assertThat(response.getStadiumName()).isNull();
        assertThat(response.getAttendance()).isNull();
        assertThat(response.getWeather()).isNull();
        assertThat(response.getGameTimeMinutes()).isNull();
        assertThat(response.getInningScores()).isEmpty();
        assertThat(response.getSummary()).isEmpty();
        verify(gameRepository, never()).findByGameId("202603210001");
        verify(gameMetadataRepository, never()).findByGameId("202603210001");
    }

    @Test
    void getGameDetailShouldThrowWhenHeaderIsMissing() {
        when(gameRepository.findGameDetailHeaderByGameId("MISSING")).thenReturn(Optional.empty());

        assertThrows(NotFoundBusinessException.class, () -> predictionService.getGameDetail("MISSING"));
        verify(gameInningScoreRepository, never()).findAllByGameIdOrderByInningAscTeamSideAsc(any());
        verify(gameSummaryRepository, never()).findAllByGameIdOrderBySummaryTypeAscIdAsc(any());
    }



    // ──────────────────────────────────────────────────
    // upsertInningScores
    // ──────────────────────────────────────────────────

    @Test
    @DisplayName("upsertInningScores - 정상 저장")
    void upsertInningScoresShouldSaveAll() {
        String gameId = "GAME001";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.deleteAllByGameId(gameId)).thenReturn(0);
        when(gameInningScoreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GameInningScoreRequestDto away1 = scoreRequest(1, "away", "KT", 2, false);
        GameInningScoreRequestDto home1 = scoreRequest(1, "home", "LG", 0, false);
        seedPredictionGameCaches(gameId, game.getGameDate());

        int saved = predictionService.upsertInningScores(gameId, List.of(away1, home1));

        assertThat(saved).isEqualTo(2);
        assertThat(game.getHomeScore()).isEqualTo(0);
        assertThat(game.getAwayScore()).isEqualTo(2);
        assertThat(game.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(game.getWinningTeam()).isEqualTo("KT");
        assertThat(game.getWinningScore()).isEqualTo(2);
        assertPredictionGameCachesEvicted(gameId, game.getGameDate());
        verify(gameInningScoreRepository).deleteAllByGameId(gameId);
        verify(gameInningScoreRepository).saveAll(anyList());
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("upsertInningScores - 10회 이상 row는 isExtra를 true로 정규화한다")
    void upsertInningScoresShouldNormalizeFalseExtraFlags() {
        String gameId = "GAME001_EXTRA_FLAG";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.deleteAllByGameId(gameId)).thenReturn(0);
        when(gameInningScoreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GameInningScoreRequestDto away10 = scoreRequest(10, "away", "KT", 1, false);
        GameInningScoreRequestDto home10 = scoreRequest(10, "home", "LG", 0, false);

        predictionService.upsertInningScores(gameId, List.of(away10, home10));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<GameInningScoreEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(gameInningScoreRepository).saveAll(captor.capture());
        assertThat(captor.getValue())
                .extracting(GameInningScoreEntity::getIsExtra)
                .containsExactly(true, true);
    }

    @Test
    @DisplayName("upsertInningScores - 당일 시작된 경기는 LIVE로 동기화")
    void upsertInningScoresShouldSyncLiveStatusForStartedTodayGame() {
        String gameId = "GAME002";
        GameEntity game = buildGame(gameId, LocalDate.now(), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.deleteAllByGameId(gameId)).thenReturn(0);
        when(gameInningScoreRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GameMetadataEntity metadata = mock(GameMetadataEntity.class);
        when(metadata.getStartTime()).thenReturn(LocalTime.now().minusHours(1));
        when(gameMetadataRepository.findByGameId(gameId)).thenReturn(Optional.of(metadata));

        GameInningScoreRequestDto away1 = scoreRequest(1, "away", "KT", 1, false);

        predictionService.upsertInningScores(gameId, List.of(away1));

        assertThat(game.getHomeScore()).isEqualTo(0);
        assertThat(game.getAwayScore()).isEqualTo(1);
        assertThat(game.getGameStatus()).isEqualTo("LIVE");
        assertThat(game.getWinningTeam()).isNull();
        assertThat(game.getWinningScore()).isNull();
    }

    @Test
    @DisplayName("upsertInningScores - 득점 정보가 없는 placeholder row는 거부한다")
    void upsertInningScoresShouldRejectPlaceholderRows() {
        String gameId = "GAME002_PLACEHOLDER";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));

        GameInningScoreRequestDto placeholder = scoreRequest(10, "away", "KT", null, null);

        assertThrows(IllegalArgumentException.class,
                () -> predictionService.upsertInningScores(gameId, List.of(placeholder)));

        verify(gameInningScoreRepository, never()).deleteAllByGameId(gameId);
        verify(gameInningScoreRepository, never()).saveAll(anyList());
        verify(gameRepository, never()).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 저장된 이닝 스코어 기준으로 경기 상태를 복구한다")
    void syncGameSnapshotShouldRepairFromStoredInningScores() {
        String gameId = "GAME003";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("away").runs(2).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(3).teamSide("away").runs(1).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(5).teamSide("home").runs(4).build()
        ));
        seedPredictionGameCaches(gameId, game.getGameDate());

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isTrue();
        assertThat(result.inningScoreCount()).isEqualTo(3);
        assertThat(result.homeScore()).isEqualTo(4);
        assertThat(result.awayScore()).isEqualTo(3);
        assertThat(result.gameStatus()).isEqualTo("COMPLETED");
        assertThat(result.winningTeam()).isEqualTo("LG");
        assertThat(result.winningScore()).isEqualTo(4);
        assertPredictionGameCachesEvicted(gameId, game.getGameDate());
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 이닝 데이터가 없어도 기존 총점으로 상태를 복구한다")
    void syncGameSnapshotShouldFallbackToExistingScores() {
        String gameId = "GAME004";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setHomeScore(1);
        game.setAwayScore(1);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of());

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isFalse();
        assertThat(result.gameStatus()).isEqualTo("DRAW");
        assertThat(result.winningTeam()).isNull();
        assertThat(result.winningScore()).isNull();
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - placeholder row는 합산 대상에서 제외한다")
    void syncGameSnapshotShouldIgnorePlaceholderRows() {
        String gameId = "GAME004_PLACEHOLDER";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setHomeScore(1);
        game.setAwayScore(1);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("away").runs(null).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("home").runs(null).build()
        ));

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isFalse();
        assertThat(result.inningScoreCount()).isEqualTo(0);
        assertThat(result.homeScore()).isEqualTo(1);
        assertThat(result.awayScore()).isEqualTo(1);
        assertThat(result.gameStatus()).isEqualTo("DRAW");
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 점수 미집계 경기의 0점 template row는 합산 대상에서 제외한다")
    void syncGameSnapshotShouldIgnoreZeroTemplateRowsWithoutKnownScore() {
        String gameId = "GAME004_ZERO_TEMPLATE";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "LG", "KT", false);
        game.setHomeScore(null);
        game.setAwayScore(null);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("home").runs(0).build()
        ));

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isFalse();
        assertThat(result.usedInningScores()).isFalse();
        assertThat(result.inningScoreCount()).isEqualTo(0);
        assertThat(result.gameStatus()).isEqualTo("SCHEDULED");
        verify(gameRepository, never()).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshot - 최종 점수가 없어도 승패 확정 뒤의 0점 extra inning template은 제외한다")
    void syncGameSnapshotShouldTrimZeroTemplateRowsAfterDecisiveInningWithoutKnownScore() {
        String gameId = "GAME004_ZERO_TEMPLATE_DECISIVE";
        GameEntity game = buildGame(gameId, LocalDate.of(2025, 5, 1), "SSG", "KIA", false);
        game.setHomeScore(null);
        game.setAwayScore(null);
        game.setGameStatus("SCHEDULED");
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.of(game));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc(gameId)).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(1).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(2).teamSide("home").runs(4).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(3).teamSide("home").runs(5).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(4).teamSide("away").runs(2).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(4).teamSide("home").runs(1).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(7).teamSide("away").runs(4).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(8).teamSide("home").runs(1).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(9).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(9).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(10).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(11).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(11).teamSide("home").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("away").runs(0).build(),
                GameInningScoreEntity.builder().gameId(gameId).inning(12).teamSide("home").runs(0).build()
        ));

        GameScoreSyncResultDto result = predictionService.syncGameSnapshot(gameId);

        assertThat(result.synced()).isTrue();
        assertThat(result.usedInningScores()).isTrue();
        assertThat(result.inningScoreCount()).isEqualTo(10);
        assertThat(result.homeScore()).isEqualTo(11);
        assertThat(result.awayScore()).isEqualTo(6);
        assertThat(result.gameStatus()).isEqualTo("COMPLETED");
        assertThat(result.winningTeam()).isEqualTo("SSG");
        verify(gameRepository).saveAndFlush(game);
    }

    @Test
    @DisplayName("syncGameSnapshotsByDateRange - 날짜 범위의 경기들을 일괄 동기화한다")
    void syncGameSnapshotsByDateRangeShouldSyncGames() {
        LocalDate startDate = LocalDate.of(2025, 5, 1);
        LocalDate endDate = LocalDate.of(2025, 5, 2);
        GameEntity first = buildGame("GAME101", LocalDate.of(2025, 5, 1), "LG", "KT", false);
        first.setGameStatus("SCHEDULED");
        GameEntity second = buildGame("GAME102", LocalDate.of(2025, 5, 2), "SSG", "KIA", false);
        second.setGameStatus("SCHEDULED");

        when(gameRepository.findAllByDateRange(startDate, endDate)).thenReturn(List.of(first, second));
        when(gameRepository.findByGameId("GAME101")).thenReturn(Optional.of(first));
        when(gameRepository.findByGameId("GAME102")).thenReturn(Optional.of(second));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME101")).thenReturn(List.of(
                GameInningScoreEntity.builder().gameId("GAME101").inning(1).teamSide("home").runs(2).build(),
                GameInningScoreEntity.builder().gameId("GAME101").inning(2).teamSide("away").runs(1).build()
        ));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME102")).thenReturn(List.of());
        second.setHomeScore(3);
        second.setAwayScore(3);

        GameScoreSyncBatchResultDto result = predictionService.syncGameSnapshotsByDateRange(startDate, endDate);

        assertThat(result.totalGames()).isEqualTo(2);
        assertThat(result.syncedGames()).isEqualTo(2);
        assertThat(result.skippedGames()).isEqualTo(0);
        assertThat(result.results()).hasSize(2);
        assertThat(first.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(second.getGameStatus()).isEqualTo("DRAW");
    }

    @Test
    @DisplayName("syncGameSnapshotsByDateRange - 31일 초과 범위는 예외 발생")
    void syncGameSnapshotsByDateRangeShouldRejectTooWideRange() {
        assertThrows(IllegalArgumentException.class,
                () -> predictionService.syncGameSnapshotsByDateRange(
                        LocalDate.of(2025, 5, 1),
                        LocalDate.of(2025, 6, 1)
                ));
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - stale status 경기만 진단 결과에 포함한다")
    void findGameStatusMismatchesByDateRangeShouldReturnOnlyMismatches() {
        LocalDate date = LocalDate.of(2025, 5, 1);
        GameEntity stale = buildGame("GAME201", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(4);
        stale.setAwayScore(2);
        GameEntity alreadyNormalized = buildGame("GAME202", date, "SSG", "KIA", false);
        alreadyNormalized.setGameStatus("COMPLETED");
        alreadyNormalized.setHomeScore(3);
        alreadyNormalized.setAwayScore(1);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale, alreadyNormalized));
        when(gameMetadataRepository.findByGameId("GAME201")).thenReturn(Optional.empty());
        when(gameMetadataRepository.findByGameId("GAME202")).thenReturn(Optional.empty());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME201")).thenReturn(List.of());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME202")).thenReturn(List.of());

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.totalGames()).isEqualTo(2);
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.mismatches()).hasSize(1);
        assertThat(result.mismatches().get(0).gameId()).isEqualTo("GAME201");
        assertThat(result.mismatches().get(0).normalizedRawStatus()).isEqualTo("SCHEDULED");
        assertThat(result.mismatches().get(0).effectiveStatus()).isEqualTo("COMPLETED");
        assertThat(result.mismatches().get(0).reasons()).contains("score_present");
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - placeholder row만 있으면 진행 데이터로 보지 않는다")
    void findGameStatusMismatchesByDateRangeShouldIgnorePlaceholderRows() {
        LocalDate date = LocalDate.now();
        GameEntity game = buildGame("GAME202_PLACEHOLDER", date, "LG", "KT", false);
        game.setGameStatus("SCHEDULED");
        game.setHomeScore(null);
        game.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(game));
        when(gameMetadataRepository.findByGameId("GAME202_PLACEHOLDER"))
                .thenReturn(Optional.of(GameMetadataEntity.builder()
                        .gameId("GAME202_PLACEHOLDER")
                        .startTime(LocalTime.MIDNIGHT)
                        .build()));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME202_PLACEHOLDER"))
                .thenReturn(List.of(
                        GameInningScoreEntity.builder().gameId("GAME202_PLACEHOLDER").inning(10).teamSide("away").runs(null).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_PLACEHOLDER").inning(10).teamSide("home").runs(null).build()
                ));

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.mismatchCount()).isEqualTo(0);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - 점수 미집계 경기의 0점 template row는 mismatch로 보지 않는다")
    void findGameStatusMismatchesByDateRangeShouldIgnoreZeroTemplateRows() {
        LocalDate date = LocalDate.now();
        GameEntity game = buildGame("GAME202_ZERO_TEMPLATE", date, "LG", "KT", false);
        game.setGameStatus("SCHEDULED");
        game.setHomeScore(null);
        game.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(game));
        when(gameMetadataRepository.findByGameId("GAME202_ZERO_TEMPLATE"))
                .thenReturn(Optional.of(GameMetadataEntity.builder()
                        .gameId("GAME202_ZERO_TEMPLATE")
                        .startTime(LocalTime.MIDNIGHT)
                        .build()));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME202_ZERO_TEMPLATE"))
                .thenReturn(List.of(
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(1).teamSide("away").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(1).teamSide("home").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(12).teamSide("away").runs(0).build(),
                        GameInningScoreEntity.builder().gameId("GAME202_ZERO_TEMPLATE").inning(12).teamSide("home").runs(0).build()
                ));

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.mismatchCount()).isEqualTo(0);
        assertThat(result.mismatches()).isEmpty();
    }

    @Test
    @DisplayName("findGameStatusMismatchesByDateRange - 비정상 팀 코드 raw row를 별도 목록으로 반환한다")
    void findGameStatusMismatchesByDateRangeShouldReturnNonCanonicalGames() {
        LocalDate date = LocalDate.of(2026, 4, 14);
        GameEntity malformed = buildGame("GAME204", date, "0LG", "롯데0", false);
        malformed.setGameStatus("SCHEDULED");
        malformed.setHomeScore(null);
        malformed.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(malformed));
        when(gameMetadataRepository.findByGameId("GAME204"))
                .thenReturn(Optional.of(GameMetadataEntity.builder()
                        .gameId("GAME204")
                        .startTime(LocalTime.of(18, 30))
                        .build()));

        GameStatusMismatchBatchResultDto result = predictionService.findGameStatusMismatchesByDateRange(date, date);

        assertThat(result.totalGames()).isEqualTo(1);
        assertThat(result.mismatchCount()).isEqualTo(0);
        assertThat(result.nonCanonicalCount()).isEqualTo(1);
        assertThat(result.nonCanonicalGames()).hasSize(1);
        assertThat(result.nonCanonicalGames().get(0).gameId()).isEqualTo("GAME204");
        assertThat(result.nonCanonicalGames().get(0).startTime()).isEqualTo(LocalTime.of(18, 30));
        assertThat(result.nonCanonicalGames().get(0).homeTeam()).isEqualTo("0LG");
        assertThat(result.nonCanonicalGames().get(0).awayTeam()).isEqualTo("롯데0");
        assertThat(result.nonCanonicalGames().get(0).reasons())
                .containsExactlyInAnyOrder("non_canonical_home_team", "non_canonical_away_team");
    }

    @Test
    @DisplayName("repairGameStatusMismatchesByDateRange - dryRun이면 실제 복구 없이 대상만 반환한다")
    void repairGameStatusMismatchesByDateRangeShouldSupportDryRun() {
        LocalDate date = LocalDate.of(2025, 5, 1);
        GameEntity stale = buildGame("GAME301", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(5);
        stale.setAwayScore(3);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale));
        when(gameMetadataRepository.findByGameId("GAME301")).thenReturn(Optional.empty());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME301")).thenReturn(List.of());

        GameStatusRepairBatchResultDto result = predictionService
                .repairGameStatusMismatchesByDateRange(date, date, true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.repairedCount()).isEqualTo(0);
        assertThat(result.repairedGames()).isEmpty();
        assertThat(stale.getGameStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    @DisplayName("repairGameStatusMismatchesByDateRange - apply면 mismatch 경기만 복구한다")
    void repairGameStatusMismatchesByDateRangeShouldRepairMismatches() {
        LocalDate date = LocalDate.of(2025, 5, 1);
        GameEntity stale = buildGame("GAME302", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(2);
        stale.setAwayScore(1);
        GameEntity healthy = buildGame("GAME303", date, "SSG", "KIA", false);
        healthy.setGameStatus("COMPLETED");
        healthy.setHomeScore(4);
        healthy.setAwayScore(3);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale, healthy));
        when(gameMetadataRepository.findByGameId("GAME302")).thenReturn(Optional.empty());
        when(gameMetadataRepository.findByGameId("GAME303")).thenReturn(Optional.empty());
        when(gameRepository.findByGameId("GAME302")).thenReturn(Optional.of(stale));
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME302")).thenReturn(List.of());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME303")).thenReturn(List.of());

        GameStatusRepairBatchResultDto result = predictionService
                .repairGameStatusMismatchesByDateRange(date, date, false);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.repairedCount()).isEqualTo(1);
        assertThat(result.repairedGames()).hasSize(1);
        assertThat(result.repairedGames().get(0).gameId()).isEqualTo("GAME302");
        assertThat(stale.getGameStatus()).isEqualTo("COMPLETED");
        assertThat(healthy.getGameStatus()).isEqualTo("COMPLETED");
        verify(gameRepository).saveAndFlush(stale);
    }

    @Test
    @DisplayName("repairGameStatusMismatchesByDateRange - 비정상 팀 코드 raw row는 복구하지 않고 그대로 반환한다")
    void repairGameStatusMismatchesByDateRangeShouldKeepNonCanonicalGames() {
        LocalDate date = LocalDate.of(2026, 4, 14);
        GameEntity stale = buildGame("GAME304", date, "LG", "KT", false);
        stale.setGameStatus("SCHEDULED");
        stale.setHomeScore(4);
        stale.setAwayScore(2);
        GameEntity malformed = buildGame("GAME305", date, "0LG", "롯데0", false);
        malformed.setGameStatus("SCHEDULED");
        malformed.setHomeScore(null);
        malformed.setAwayScore(null);

        when(gameRepository.findAllByDateRange(date, date)).thenReturn(List.of(stale, malformed));
        when(gameRepository.findByGameId("GAME304")).thenReturn(Optional.of(stale));
        when(gameMetadataRepository.findByGameId("GAME304")).thenReturn(Optional.empty());
        when(gameMetadataRepository.findByGameId("GAME305")).thenReturn(Optional.empty());
        when(gameInningScoreRepository.findAllByGameIdOrderByInningAscTeamSideAsc("GAME304")).thenReturn(List.of());

        GameStatusRepairBatchResultDto result = predictionService
                .repairGameStatusMismatchesByDateRange(date, date, false);

        assertThat(result.mismatchCount()).isEqualTo(1);
        assertThat(result.repairedCount()).isEqualTo(1);
        assertThat(result.nonCanonicalCount()).isEqualTo(1);
        assertThat(result.nonCanonicalGames()).hasSize(1);
        assertThat(result.nonCanonicalGames().get(0).gameId()).isEqualTo("GAME305");
        verify(gameRepository).saveAndFlush(stale);
    }

    @Test
    @DisplayName("upsertInningScores - 존재하지 않는 gameId는 예외 발생")
    void upsertInningScoresShouldThrowWhenGameNotFound() {
        String gameId = "NOTEXIST";
        when(gameRepository.findByGameId(gameId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> predictionService.upsertInningScores(gameId, List.of()));
    }
}
