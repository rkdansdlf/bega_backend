package com.example.kbo.validation;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.repository.GameRepository;
import com.example.kbo.repository.MatchRangeProjection;
import com.example.kbo.repository.SeasonInfoProjection;
import com.example.kbo.util.GameSummaryDisplayPolicy;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BaseballDataIntegrityGuard {

    private final GameRepository gameRepository;

    public void ensurePredictionDateMatches(
            String scope,
            LocalDate date,
            List<MatchRangeProjection> matches) {
        if (matches == null || matches.isEmpty()) {
            throw newException(
                    scope,
                    null,
                    date,
                    null,
                    List.of(
                            missingItem(
                                    "game_date",
                                    "경기 날짜",
                                    "요청한 날짜의 경기 row가 없어 일정을 확인할 수 없습니다.",
                                    "YYYY-MM-DD"),
                            missingItem(
                                    "season_league_context",
                                    "시즌/리그 구분",
                                    "해당 날짜 일정의 시즌/리그 기준 데이터가 필요합니다.",
                                    "season_id, season_year, league_type_code")));
        }

        matches.forEach(match -> validateMatchProjection(scope, match));
    }

    public void ensurePredictionRangeMatches(
            String scope,
            List<MatchRangeProjection> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        matches.forEach(match -> validateMatchProjection(scope, match));
    }

    public GameEntity requireValidGame(String scope, String gameId) {
        GameEntity game = gameRepository.findByGameId(gameId)
                .orElseThrow(() -> newException(
                        scope,
                        gameId,
                        null,
                        null,
                        List.of(
                                missingItem(
                                        "game_id",
                                        "경기 ID",
                                        "요청한 경기의 game row가 없어 상세 데이터를 확인할 수 없습니다.",
                                        "예: 20260405HHOB0"),
                                missingItem(
                                        "season_league_context",
                                        "시즌/리그 구분",
                                        "경기 식별용 시즌/리그 기준 데이터가 필요합니다.",
                                        "season_id, season_year, league_type_code"))));
        validateGameEntity(scope, game);
        return game;
    }

    public void ensurePredictionGameSummaryRecords(
            String scope,
            String gameId,
            LocalDate gameDate,
            String rawGameStatus,
            Integer homeScore,
            Integer awayScore,
            List<GameSummaryEntity> summaries) {
        if (!requiresGameSummaryRecords(gameDate, rawGameStatus, homeScore, awayScore)) {
            return;
        }

        if (GameSummaryDisplayPolicy.hasDisplayableSummary(summaries)) {
            return;
        }

        String reason = summaries == null || summaries.isEmpty()
                ? "완료 경기의 주요 기록 row가 없습니다."
                : "주요 기록 row가 표시 가능한 형식이 아니거나 내부 구조화 데이터만 포함합니다.";
        throw newException(
                scope,
                gameId,
                gameDate,
                null,
                List.of(missingItem(
                        "game_summary",
                        "경기 주요 기록",
                        reason,
                        "game_summary.summary_type, player_name, detail_text")));
    }

    public void ensureHomeGamesByDate(
            String scope,
            LocalDate date,
            List<GameEntity> games) {
        if (games == null || games.isEmpty()) {
            throw newException(
                    scope,
                    null,
                    date,
                    null,
                    List.of(
                            missingItem(
                                    "game_date",
                                    "경기 날짜",
                                    "요청한 날짜의 홈 일정 row가 없습니다.",
                                    "YYYY-MM-DD"),
                            missingItem(
                                    "season_league_context",
                                    "시즌/리그 구분",
                                    "홈 일정 해석에 필요한 시즌/리그 데이터가 없습니다.",
                                    "season_id, season_year, league_type_code")));
        }

        games.forEach(game -> validateGameEntity(scope, game));
    }

    public void ensureHomeScheduledWindow(
            String scope,
            LocalDate startDate,
            LocalDate endDate,
            List<GameEntity> games) {
        if (games == null || games.isEmpty()) {
            throw newException(
                    scope,
                    null,
                    startDate,
                    endDate,
                    List.of(
                            missingItem(
                                    "game_date",
                                    "경기 날짜",
                                    "예정 경기 구간의 row가 없어 일정 윈도를 구성할 수 없습니다.",
                                    "YYYY-MM-DD"),
                            missingItem(
                                    "season_league_context",
                                    "시즌/리그 구분",
                                    "예정 경기 구간의 시즌/리그 기준 데이터가 필요합니다.",
                                    "season_id, season_year, league_type_code")));
        }

        games.forEach(game -> validateGameEntity(scope, game));
    }

    private void validateMatchProjection(String scope, MatchRangeProjection match) {
        LinkedHashMap<String, ManualBaseballDataMissingItem> missingItems = new LinkedHashMap<>();
        String gameId = match.getGameId();
        LocalDate gameDate = match.getGameDate();

        if (isBlank(gameId)) {
            putMissing(
                    missingItems,
                    missingItem(
                            "game_id",
                            "경기 ID",
                            "경기 식별용 game_id가 비어 있습니다.",
                            "예: 20260405HHOB0"));
        }
        if (gameDate == null) {
            putMissing(
                    missingItems,
                    missingItem(
                            "game_date",
                            "경기 날짜",
                            "경기 날짜가 비어 있습니다.",
                            "YYYY-MM-DD"));
        }
        if (isBlank(match.getHomeTeam()) || isBlank(match.getAwayTeam())) {
            putMissing(
                    missingItems,
                    missingItem(
                            "team_codes",
                            "홈/원정 팀 코드",
                            "홈팀 또는 원정팀 코드가 비어 있어 매치업을 식별할 수 없습니다.",
                            "home_team, away_team"));
        }

        validateSeasonAndStage(
                missingItems,
                gameId,
                gameDate,
                match.getSeasonId(),
                match.getRawLeagueTypeCode());
        validatePastGameCompletion(
                missingItems,
                gameDate,
                match.getGameStatus(),
                match.getHomeScore(),
                match.getAwayScore());

        if (!missingItems.isEmpty()) {
            throw newException(scope, gameId, gameDate, null, new ArrayList<>(missingItems.values()));
        }
    }

    private void validateGameEntity(String scope, GameEntity game) {
        LinkedHashMap<String, ManualBaseballDataMissingItem> missingItems = new LinkedHashMap<>();
        String gameId = game.getGameId();
        LocalDate gameDate = game.getGameDate();

        if (isBlank(gameId)) {
            putMissing(
                    missingItems,
                    missingItem(
                            "game_id",
                            "경기 ID",
                            "경기 식별용 game_id가 비어 있습니다.",
                            "예: 20260405HHOB0"));
        }
        if (gameDate == null) {
            putMissing(
                    missingItems,
                    missingItem(
                            "game_date",
                            "경기 날짜",
                            "경기 날짜가 비어 있습니다.",
                            "YYYY-MM-DD"));
        }
        if (isBlank(game.getHomeTeam()) || isBlank(game.getAwayTeam())) {
            putMissing(
                    missingItems,
                    missingItem(
                            "team_codes",
                            "홈/원정 팀 코드",
                            "홈팀 또는 원정팀 코드가 비어 있어 매치업을 식별할 수 없습니다.",
                            "home_team, away_team"));
        }

        validateSeasonAndStage(
                missingItems,
                gameId,
                gameDate,
                game.getSeasonId(),
                null);
        validatePastGameCompletion(
                missingItems,
                gameDate,
                game.getGameStatus(),
                game.getHomeScore(),
                game.getAwayScore());

        if (!missingItems.isEmpty()) {
            throw newException(scope, gameId, gameDate, null, new ArrayList<>(missingItems.values()));
        }
    }

    private void validateSeasonAndStage(
            Map<String, ManualBaseballDataMissingItem> missingItems,
            String gameId,
            LocalDate gameDate,
            Integer seasonId,
            Integer rawLeagueTypeCode) {
        if (seasonId == null && rawLeagueTypeCode == null) {
            putMissing(
                    missingItems,
                    missingItem(
                            "season_league_context",
                            "시즌/리그 구분",
                            "season_id 또는 league_type_code가 비어 있어 시즌 문맥을 해석할 수 없습니다.",
                            "season_id, season_year, league_type_code"));
            return;
        }

        SeasonResolution seasonResolution = resolveSeasonResolution(seasonId, gameDate);
        if (seasonId != null && seasonResolution.seasonInfo() == null) {
            putMissing(
                    missingItems,
                    missingItem(
                            "season_league_context",
                            "시즌/리그 구분",
                            "season_id가 가리키는 kbo_seasons row가 없습니다.",
                            "season_id, season_year, league_type_code"));
            return;
        }

        Integer resolvedSeasonYear = seasonResolution.seasonYear();
        Integer resolvedLeagueTypeCode = rawLeagueTypeCode != null
                ? rawLeagueTypeCode
                : seasonResolution.leagueTypeCode();

        if (gameDate != null
                && resolvedSeasonYear != null
                && !Objects.equals(resolvedSeasonYear, gameDate.getYear())) {
            putMissing(
                    missingItems,
                    missingItem(
                            "season_league_context",
                            "시즌/리그 구분",
                            "season_id가 가리키는 시즌 연도가 경기 날짜와 일치하지 않습니다.",
                            "season_id, season_year, league_type_code"));
            return;
        }

        if (gameDate != null
                && resolvedSeasonYear != null
                && isClearlyPostseasonMismatch(resolvedLeagueTypeCode, gameDate, resolvedSeasonYear)) {
            putMissing(
                    missingItems,
                    missingItem(
                            "season_league_context",
                            "시즌/리그 구분",
                            "정규시즌 시점의 경기가 포스트시즌 단계로 해석되고 있습니다.",
                            "season_id, season_year, league_type_code, start_date"));
            log.warn(
                    "event=manual_baseball_data_stage_mismatch scope_detected=true gameId={} gameDate={} seasonId={} seasonYear={} leagueTypeCode={}",
                    gameId,
                    gameDate,
                    seasonId,
                    resolvedSeasonYear,
                    resolvedLeagueTypeCode);
        }
    }

    private void validatePastGameCompletion(
            Map<String, ManualBaseballDataMissingItem> missingItems,
            LocalDate gameDate,
            String rawGameStatus,
            Integer homeScore,
            Integer awayScore) {
        if (gameDate == null) {
            return;
        }

        String normalizedStatus = normalizeStatus(rawGameStatus);
        boolean hasKnownScore = homeScore != null && awayScore != null;
        boolean isPastDate = gameDate.isBefore(LocalDate.now());
        boolean scoreRequiredStatus = switch (normalizedStatus) {
            case "COMPLETED", "DRAW" -> true;
            default -> false;
        };
        boolean unresolvedPastStatus = isPastDate && switch (normalizedStatus) {
            case "", "UNKNOWN", "SCHEDULED", "LIVE", "IN_PROGRESS", "INPROGRESS", "DELAYED", "SUSPENDED" -> true;
            default -> false;
        };

        if (!hasKnownScore && (scoreRequiredStatus || unresolvedPastStatus)) {
            putMissing(
                    missingItems,
                    missingItem(
                            "game_status",
                            "경기 상태",
                            "과거 경기 상태가 종료 기준으로 확정되지 않았습니다.",
                            "SCHEDULED, COMPLETED, CANCELLED 등"));
            putMissing(
                    missingItems,
                    missingItem(
                            "final_score",
                            "최종 점수",
                            "과거 경기의 최종 점수가 비어 있습니다.",
                            "home_score, away_score"));
        }
    }

    private boolean requiresGameSummaryRecords(
            LocalDate gameDate,
            String rawGameStatus,
            Integer homeScore,
            Integer awayScore) {
        String normalizedStatus = normalizeStatus(rawGameStatus);
        boolean noSummaryExpectedStatus = switch (normalizedStatus) {
            case "CANCELLED", "POSTPONED", "SUSPENDED", "DELAYED" -> true;
            default -> false;
        };
        if (noSummaryExpectedStatus) {
            return false;
        }

        boolean hasKnownScore = homeScore != null && awayScore != null;
        boolean isFinishedStatus = switch (normalizedStatus) {
            case "COMPLETED", "DRAW", "FINAL" -> true;
            default -> false;
        };
        boolean isResolvedPastGame = gameDate != null && gameDate.isBefore(LocalDate.now()) && hasKnownScore;
        return isFinishedStatus || isResolvedPastGame;
    }

    private SeasonResolution resolveSeasonResolution(Integer seasonId, LocalDate gameDate) {
        SeasonInfoProjection seasonInfo = seasonId == null
                ? null
                : gameRepository.findSeasonInfoBySeasonId(seasonId).orElse(null);
        Integer seasonYear = seasonInfo != null
                ? seasonInfo.getSeasonYear()
                : (gameDate == null ? null : gameDate.getYear());
        Integer leagueTypeCode = seasonInfo != null
                ? seasonInfo.getLeagueTypeCode()
                : (seasonId != null && gameDate != null && Objects.equals(seasonId, gameDate.getYear()) ? 0 : null);
        return new SeasonResolution(seasonInfo, seasonYear, leagueTypeCode);
    }

    private boolean isClearlyPostseasonMismatch(
            Integer leagueTypeCode,
            LocalDate gameDate,
            Integer seasonYear) {
        if (leagueTypeCode == null
                || leagueTypeCode < 2
                || leagueTypeCode > 5
                || gameDate == null
                || seasonYear == null) {
            return false;
        }

        LocalDate stageStart = gameRepository.findConfiguredStartDateByTypeFromSeasonYear(leagueTypeCode, seasonYear)
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(leagueTypeCode, seasonYear))
                .or(() -> gameRepository.findLatestStartDateByTypeAsOf(leagueTypeCode, gameDate))
                .orElse(null);
        if (stageStart != null) {
            return gameDate.isBefore(stageStart);
        }
        return gameDate.getMonthValue() < 9;
    }

    private ManualBaseballDataRequiredException newException(
            String scope,
            String gameId,
            LocalDate primaryDate,
            LocalDate secondaryDate,
            List<ManualBaseballDataMissingItem> missingItems) {
        List<ManualBaseballDataMissingItem> dedupedItems = List.copyOf(missingItems);
        String operatorMessage = buildOperatorMessage(dedupedItems, gameId, primaryDate, secondaryDate);
        log.warn(
                "event=manual_baseball_data_required scope={} game_id={} primary_date={} secondary_date={} missing_keys={}",
                scope,
                gameId,
                primaryDate,
                secondaryDate,
                dedupedItems.stream().map(ManualBaseballDataMissingItem::key).toList());
        return new ManualBaseballDataRequiredException(
                new ManualBaseballDataRequest(scope, dedupedItems, operatorMessage, true));
    }

    private String buildOperatorMessage(
            List<ManualBaseballDataMissingItem> missingItems,
            String gameId,
            LocalDate primaryDate,
            LocalDate secondaryDate) {
        List<String> segments = new ArrayList<>();
        if (gameId != null && !gameId.isBlank()) {
            segments.add("경기 ID=" + gameId);
        }
        if (primaryDate != null && secondaryDate != null && !primaryDate.equals(secondaryDate)) {
            segments.add("날짜 구간=" + primaryDate + "~" + secondaryDate);
        } else if (primaryDate != null) {
            segments.add("날짜=" + primaryDate);
        }
        segments.addAll(missingItems.stream()
                .map(item -> item.label() + "(" + item.reason() + ")")
                .toList());
        return "다음 야구 데이터가 필요합니다: " + String.join(", ", segments);
    }

    private void putMissing(
            Map<String, ManualBaseballDataMissingItem> missingItems,
            ManualBaseballDataMissingItem item) {
        missingItems.putIfAbsent(item.key(), item);
    }

    private ManualBaseballDataMissingItem missingItem(
            String key,
            String label,
            String reason,
            String expectedFormat) {
        return new ManualBaseballDataMissingItem(key, label, reason, expectedFormat);
    }

    private String normalizeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record SeasonResolution(
            SeasonInfoProjection seasonInfo,
            Integer seasonYear,
            Integer leagueTypeCode) {}
}
