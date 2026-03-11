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

        Integer rawLeagueTypeCode = resolveRawLeagueTypeCode(game.getSeasonId());
        Integer inferredLeagueTypeCode = inferPostseasonLeagueTypeCode(game.getGameDate());
        if (!isPostseasonCode(rawLeagueTypeCode) || !isPostseasonCode(inferredLeagueTypeCode)) {
            return rawLeagueTypeCode;
        }
        if (rawLeagueTypeCode.equals(inferredLeagueTypeCode)) {
            return rawLeagueTypeCode;
        }

        log.warn(
                "[LeagueStageResolver] Overriding postseason stage gameId={} seasonId={} raw={} inferred={} gameDate={}",
                game.getGameId(),
                game.getSeasonId(),
                rawLeagueTypeCode,
                inferredLeagueTypeCode,
                game.getGameDate());
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

    private Integer inferPostseasonLeagueTypeCode(LocalDate gameDate) {
        if (gameDate == null) {
            return null;
        }
        PostseasonStartDates startDates = postseasonStartDateCache.computeIfAbsent(
                gameDate.getYear(),
                this::loadPostseasonStartDates);
        return startDates.inferLeagueTypeCode(gameDate);
    }

    private PostseasonStartDates loadPostseasonStartDates(int seasonYear) {
        return new PostseasonStartDates(
                gameRepository.findConfiguredStartDateByTypeFromSeasonYear(2, seasonYear).orElse(null),
                gameRepository.findConfiguredStartDateByTypeFromSeasonYear(3, seasonYear).orElse(null),
                gameRepository.findConfiguredStartDateByTypeFromSeasonYear(4, seasonYear).orElse(null),
                gameRepository.findConfiguredStartDateByTypeFromSeasonYear(5, seasonYear).orElse(null));
    }

    private boolean isPostseasonCode(Integer leagueTypeCode) {
        return leagueTypeCode != null && leagueTypeCode >= 2 && leagueTypeCode <= 5;
    }

    private record PostseasonStartDates(
            LocalDate wildCardStart,
            LocalDate semiPlayoffStart,
            LocalDate playoffStart,
            LocalDate koreanSeriesStart) {

        private Integer inferLeagueTypeCode(LocalDate gameDate) {
            if (gameDate == null) {
                return null;
            }
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
    }
}
