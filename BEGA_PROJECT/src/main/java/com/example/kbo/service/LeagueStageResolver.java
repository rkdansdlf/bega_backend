package com.example.kbo.service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class LeagueStageResolver {

    private final GameRepository gameRepository;

    private final Map<Integer, Optional<Integer>> rawLeagueTypeCache = new ConcurrentHashMap<>();
    private final Map<Integer, PostseasonStartDates> postseasonStartDateCache = new ConcurrentHashMap<>();

    public Integer resolveEffectiveLeagueTypeCode(GameEntity game) {
        if (game == null) {
            return null;
        }

        return resolveEffectiveLeagueTypeCode(
                null,
                game.getGameDate(),
                game.getSeasonId(),
                game.getGameId());
    }

    public Integer resolveEffectiveLeagueTypeCode(
            Integer rawLeagueTypeCode,
            LocalDate gameDate,
            Integer seasonId,
            String gameId) {
        Integer resolvedRawLeagueTypeCode = rawLeagueTypeCode != null
                ? rawLeagueTypeCode
                : resolveRawLeagueTypeCode(seasonId);
        if (resolvedRawLeagueTypeCode == null) {
            resolvedRawLeagueTypeCode = inferRegularSeasonLeagueTypeCode(gameDate, seasonId);
        }
        if (!isPostseasonCode(resolvedRawLeagueTypeCode)) {
            return resolvedRawLeagueTypeCode;
        }

        Integer inferredLeagueTypeCode = inferPostseasonLeagueTypeCode(gameDate, resolvedRawLeagueTypeCode);
        if (!isPostseasonCode(inferredLeagueTypeCode)) {
            return resolvedRawLeagueTypeCode;
        }
        if (resolvedRawLeagueTypeCode.equals(inferredLeagueTypeCode)) {
            return resolvedRawLeagueTypeCode;
        }

        log.warn(
                "[LeagueStageResolver] Overriding postseason stage gameId={} seasonId={} raw={} inferred={} gameDate={}",
                gameId,
                seasonId,
                resolvedRawLeagueTypeCode,
                inferredLeagueTypeCode,
                gameDate);
        return inferredLeagueTypeCode;
    }

    public Integer resolveRawLeagueTypeCode(Integer seasonId) {
        if (seasonId == null) {
            return null;
        }
        Optional<Integer> cached = rawLeagueTypeCache.get(seasonId);
        if (cached != null) {
            return cached.orElse(null);
        }

        Optional<Integer> fetched = Optional.empty();
        Optional<Integer> repositoryValue = gameRepository.findLeagueTypeCodeBySeasonId(seasonId);
        if (repositoryValue != null) {
            fetched = repositoryValue;
        }
        rawLeagueTypeCache.put(seasonId, fetched);
        return fetched.orElse(null);
    }

    private Integer inferRegularSeasonLeagueTypeCode(LocalDate gameDate, Integer seasonId) {
        if (gameDate == null || seasonId == null) {
            return null;
        }
        return seasonId.equals(gameDate.getYear()) ? 0 : null;
    }

    private Integer inferPostseasonLeagueTypeCode(LocalDate gameDate, Integer rawLeagueTypeCode) {
        if (gameDate == null) {
            return null;
        }
        PostseasonStartDates startDates = postseasonStartDateCache.computeIfAbsent(
                gameDate.getYear(),
                this::loadPostseasonStartDates);
        return startDates.resolveLeagueTypeCode(gameDate, rawLeagueTypeCode);
    }

    private PostseasonStartDates loadPostseasonStartDates(int seasonYear) {
        return new PostseasonStartDates(
                loadPostseasonStartDate(2, seasonYear),
                loadPostseasonStartDate(3, seasonYear),
                loadPostseasonStartDate(4, seasonYear),
                loadPostseasonStartDate(5, seasonYear));
    }

    private LocalDate loadPostseasonStartDate(int leagueTypeCode, int seasonYear) {
        return gameRepository.findConfiguredStartDateByTypeFromSeasonYear(leagueTypeCode, seasonYear)
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(leagueTypeCode, seasonYear))
                .orElse(null);
    }

    private boolean isPostseasonCode(Integer leagueTypeCode) {
        return leagueTypeCode != null && leagueTypeCode >= 2 && leagueTypeCode <= 5;
    }

    private record PostseasonStartDates(
            LocalDate wildCardStart,
            LocalDate semiPlayoffStart,
            LocalDate playoffStart,
            LocalDate koreanSeriesStart) {

        private Integer resolveLeagueTypeCode(LocalDate gameDate, Integer rawLeagueTypeCode) {
            if (gameDate == null) {
                return null;
            }
            if (rawLeagueTypeCode == null) {
                return inferHighestKnownStartedStage(gameDate);
            }

            Integer highestKnownStartedStage = inferHighestKnownStartedStage(gameDate);
            if (highestKnownStartedStage != null && highestKnownStartedStage > rawLeagueTypeCode) {
                return highestKnownStartedStage;
            }
            if (highestKnownStartedStage == null || highestKnownStartedStage.equals(rawLeagueTypeCode)) {
                return rawLeagueTypeCode;
            }
            if (canSafelyDowngrade(rawLeagueTypeCode, highestKnownStartedStage, gameDate)) {
                return highestKnownStartedStage;
            }
            return rawLeagueTypeCode;
        }

        private Integer inferHighestKnownStartedStage(LocalDate gameDate) {
            if (koreanSeriesStart != null && !gameDate.isBefore(koreanSeriesStart)) {
                return 5;
            }
            if (playoffStart != null && !gameDate.isBefore(playoffStart)) {
                return 4;
            }
            if (semiPlayoffStart != null && !gameDate.isBefore(semiPlayoffStart)) {
                return 3;
            }
            if (wildCardStart != null && !gameDate.isBefore(wildCardStart)) {
                return 2;
            }
            return null;
        }

        private boolean canSafelyDowngrade(int rawLeagueTypeCode, int targetLeagueTypeCode, LocalDate gameDate) {
            for (int stage = rawLeagueTypeCode; stage > targetLeagueTypeCode; stage--) {
                LocalDate stageStart = stageStart(stage);
                if (stageStart == null || !gameDate.isBefore(stageStart)) {
                    return false;
                }
            }
            return true;
        }

        private LocalDate stageStart(int leagueTypeCode) {
            return switch (leagueTypeCode) {
                case 2 -> wildCardStart;
                case 3 -> semiPlayoffStart;
                case 4 -> playoffStart;
                case 5 -> koreanSeriesStart;
                default -> null;
            };
        }
    }
}
