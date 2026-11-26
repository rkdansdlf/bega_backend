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

    /** 값이 모두 확정된 조회 결과를 보관하는 시간. */
    static final Duration RESOLVED_TTL = Duration.ofMinutes(30);

    /**
     * 비어 있거나 일부만 확정된 조회 결과를 보관하는 시간.
     * 시즌 도중에는 아직 시작하지 않은 단계의 시작일이 비어 있는 것이 정상이고, 최초 조회 시점의 빈 값이
     * 서버가 살아 있는 동안 계속 재사용되면 안 되므로 짧게 둔다.
     */
    static final Duration UNRESOLVED_TTL = Duration.ofMinutes(5);

    private final GameRepository gameRepository;
    private final Clock clock;

    private final Map<Integer, CacheEntry<Optional<Integer>>> rawLeagueTypeCache = new ConcurrentHashMap<>();
    private final Map<Integer, CacheEntry<PostseasonStartDates>> postseasonStartDateCache = new ConcurrentHashMap<>();

    @Autowired
    public LeagueStageResolver(GameRepository gameRepository) {
        this(gameRepository, Clock.systemDefaultZone());
    }

    LeagueStageResolver(GameRepository gameRepository, Clock clock) {
        this.gameRepository = gameRepository;
        this.clock = clock;
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
        Instant now = clock.instant();
        CacheEntry<Optional<Integer>> cached = rawLeagueTypeCache.get(seasonId);
        if (cached != null && cached.isFresh(now)) {
            return cached.value().orElse(null);
        }

        Optional<Integer> fetched = Optional.empty();
        Optional<Integer> repositoryValue = gameRepository.findLeagueTypeCodeBySeasonId(seasonId);
        if (repositoryValue != null) {
            fetched = repositoryValue;
        }
        Duration ttl = fetched.isPresent() ? RESOLVED_TTL : UNRESOLVED_TTL;
        rawLeagueTypeCache.put(seasonId, new CacheEntry<>(fetched, now.plus(ttl)));
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
        return resolvePostseasonStartDates(gameDate.getYear()).resolveLeagueTypeCode(gameDate, rawLeagueTypeCode);
    }

    private PostseasonStartDates resolvePostseasonStartDates(int seasonYear) {
        Instant now = clock.instant();
        CacheEntry<PostseasonStartDates> cached = postseasonStartDateCache.get(seasonYear);
        if (cached != null && cached.isFresh(now)) {
            return cached.value();
        }

        PostseasonStartDates loaded = loadPostseasonStartDates(seasonYear);
        Duration ttl = loaded.isComplete() ? RESOLVED_TTL : UNRESOLVED_TTL;
        postseasonStartDateCache.put(seasonYear, new CacheEntry<>(loaded, now.plus(ttl)));
        return loaded;
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

    private record CacheEntry<T>(T value, Instant expiresAt) {

        private boolean isFresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    private record PostseasonStartDates(
            LocalDate wildCardStart,
            LocalDate semiPlayoffStart,
            LocalDate playoffStart,
            LocalDate koreanSeriesStart) {

        private boolean isComplete() {
            return wildCardStart != null
                    && semiPlayoffStart != null
                    && playoffStart != null
                    && koreanSeriesStart != null;
        }

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
