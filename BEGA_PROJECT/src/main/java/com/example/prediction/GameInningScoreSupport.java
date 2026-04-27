package com.example.prediction;

import com.example.kbo.entity.GameInningScoreEntity;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class GameInningScoreSupport {

    private GameInningScoreSupport() {
    }

    static boolean isMeaningful(GameInningScoreEntity score) {
        return score != null
                && score.getInning() != null
                && score.getRuns() != null;
    }

    static Boolean normalizeIsExtraFlag(Integer inning, Boolean rawIsExtra) {
        if (inning == null) {
            return rawIsExtra;
        }
        return inning > 9;
    }

    static List<GameInningScoreEntity> filterMeaningful(List<GameInningScoreEntity> inningScores) {
        if (inningScores == null || inningScores.isEmpty()) {
            return List.of();
        }

        return inningScores.stream()
                .filter(GameInningScoreSupport::isMeaningful)
                .collect(Collectors.toList());
    }

    static List<GameInningScoreEntity> normalizeMeaningful(
            List<GameInningScoreEntity> inningScores,
            Integer homeScore,
            Integer awayScore
    ) {
        List<GameInningScoreEntity> meaningful = filterMeaningful(inningScores);
        if (meaningful.isEmpty()) {
            return meaningful;
        }

        boolean hasKnownScore = homeScore != null && awayScore != null;
        int totalRuns = meaningful.stream()
                .mapToInt(score -> score.getRuns() == null ? 0 : score.getRuns())
                .sum();

        if (!hasKnownScore && totalRuns == 0) {
            return List.of();
        }

        if (hasKnownScore && !homeScore.equals(awayScore)) {
            Integer capInning = resolveFinalInningCap(meaningful, homeScore, awayScore);
            if (capInning != null) {
                return meaningful.stream()
                        .filter(score -> score.getInning() != null && score.getInning() <= capInning)
                        .collect(Collectors.toList());
            }
        }

        if (!hasKnownScore) {
            Integer capInning = resolveDecisiveInningCapWithoutKnownScore(meaningful);
            if (capInning != null) {
                return meaningful.stream()
                        .filter(score -> score.getInning() != null && score.getInning() <= capInning)
                        .collect(Collectors.toList());
            }
        }

        return meaningful;
    }

    private static Integer resolveFinalInningCap(
            List<GameInningScoreEntity> inningScores,
            int homeScore,
            int awayScore
    ) {
        Map<Integer, InningTotals> totalsByInning = buildTotalsByInning(inningScores);
        int cumulativeHome = 0;
        int cumulativeAway = 0;
        for (Map.Entry<Integer, InningTotals> entry : totalsByInning.entrySet()) {
            cumulativeHome += entry.getValue().homeRuns();
            cumulativeAway += entry.getValue().awayRuns();
            if (entry.getKey() >= 9 && cumulativeHome == homeScore && cumulativeAway == awayScore) {
                return entry.getKey();
            }
        }

        return null;
    }

    private static Integer resolveDecisiveInningCapWithoutKnownScore(
            List<GameInningScoreEntity> inningScores
    ) {
        Map<Integer, InningTotals> totalsByInning = buildTotalsByInning(inningScores);
        int cumulativeHome = 0;
        int cumulativeAway = 0;
        for (Map.Entry<Integer, InningTotals> entry : totalsByInning.entrySet()) {
            cumulativeHome += entry.getValue().homeRuns();
            cumulativeAway += entry.getValue().awayRuns();
            if (entry.getKey() < 9 || cumulativeHome == cumulativeAway) {
                continue;
            }

            boolean hasLaterTemplateOnlyRows = totalsByInning.entrySet().stream()
                    .filter(candidate -> candidate.getKey() > entry.getKey())
                    .allMatch(candidate -> candidate.getValue().homeRuns() == 0 && candidate.getValue().awayRuns() == 0);
            boolean hasLaterRows = totalsByInning.keySet().stream().anyMatch(candidate -> candidate > entry.getKey());
            if (hasLaterRows && hasLaterTemplateOnlyRows) {
                return entry.getKey();
            }
        }

        return null;
    }

    private static Map<Integer, InningTotals> buildTotalsByInning(List<GameInningScoreEntity> inningScores) {
        Map<Integer, InningTotals> totalsByInning = inningScores.stream()
                .filter(score -> score.getInning() != null)
                .sorted(Comparator.comparingInt(GameInningScoreEntity::getInning))
                .collect(Collectors.toMap(
                        GameInningScoreEntity::getInning,
                        score -> new InningTotals(
                                "home".equalsIgnoreCase(score.getTeamSide()) ? valueOrZero(score.getRuns()) : 0,
                                "away".equalsIgnoreCase(score.getTeamSide()) ? valueOrZero(score.getRuns()) : 0
                        ),
                        InningTotals::merge,
                        LinkedHashMap::new
                ));
        return totalsByInning;
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private record InningTotals(int homeRuns, int awayRuns) {
        private InningTotals merge(InningTotals other) {
            return new InningTotals(homeRuns + other.homeRuns, awayRuns + other.awayRuns);
        }
    }
}
