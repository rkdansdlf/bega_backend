package com.example.kbo.service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LeagueStageResolver {

    /** 현재/미래 시즌에서 아직 시작일이 없는 단계의 빈 결과를 재사용하는 시간. */
    static final Duration EMPTY_STAGE_START_DATE_TTL = Duration.ofMinutes(2);

    private final GameRepository gameRepository;
    private final Clock clock;

    private final Map<Integer, Optional<Integer>> rawLeagueTypeCache = new ConcurrentHashMap<>();
    /**
     * (시즌 연도, 단계) 별 시작일 캐시.
     * <ul>
     *   <li>확인된 시작일: 영구 캐시</li>
     *   <li>지난 시즌의 빈 결과: 더 이상 바뀌지 않으므로 영구 캐시</li>
     *   <li>현재/미래 시즌의 빈 결과: 첫 경기가 진행되면 바로 반영되도록 짧은 TTL 동안만 캐시</li>
     * </ul>
     */
    private final Map<Long, CachedStageStartDate> postseasonStartDateCache = new ConcurrentHashMap<>();

    @Autowired
    public LeagueStageResolver(GameRepository gameRepository) {
        this(gameRepository, Clock.systemDefaultZone());
    }

    LeagueStageResolver(GameRepository gameRepository, Clock clock) {
        this.gameRepository = gameRepository;
        this.clock = clock == null ? Clock.systemDefaultZone() : clock;
    }

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
        PostseasonStartDates startDates = loadPostseasonStartDates(gameDate.getYear());
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
        long cacheKey = seasonYear * 10L + leagueTypeCode;
        Instant now = clock.instant();
        CachedStageStartDate cached = postseasonStartDateCache.get(cacheKey);
        if (cached != null && cached.isValidAt(now)) {
            return cached.startDate();
        }
        LocalDate fetched = gameRepository.findConfiguredStartDateByTypeFromSeasonYear(leagueTypeCode, seasonYear)
                .or(() -> gameRepository.findFirstStartDateByTypeFromSeasonYear(leagueTypeCode, seasonYear))
                .orElse(null);
        boolean permanent = fetched != null || seasonYear < LocalDate.now(clock).getYear();
        postseasonStartDateCache.put(
                cacheKey,
                new CachedStageStartDate(fetched, permanent ? null : now.plus(EMPTY_STAGE_START_DATE_TTL)));
        return fetched;
    }

    /** expiresAt이 null이면 만료되지 않는다. */
    private record CachedStageStartDate(LocalDate startDate, Instant expiresAt) {
        private boolean isValidAt(Instant now) {
            return expiresAt == null || now.isBefore(expiresAt);
        }
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
