package com.example.kbo.util;

import java.util.List;
import java.util.Set;

public final class KboTeamCodePolicy {

    public static final Set<String> CANONICAL_CODES = Set.of(
            "SS", "LT", "LG", "DB", "KIA", "KH", "HH", "SSG", "NC", "KT");

    /**
     * 현재 10개 구단의 canonical 팀 코드(정렬 고정).
     */
    public static final List<String> CANONICAL_CODE_LIST = List.of(
            "SS", "LT", "LG", "DB", "KIA", "KH", "HH", "SSG", "NC", "KT");

    /**
     * game 테이블 조회용 팀 코드 목록.
     *
     * <p>크롤러가 옛 팀 코드(HT/OB/SK/WO 등)로 저장한 경기도 홈/예측 화면에서 같은 기준으로
     * 조회되도록 canonical 코드와 그 별칭(legacy 코드)을 모두 포함한다. 응답에 내보낼 때는
     * {@link TeamCodeNormalizer#normalize(String)}로 canonical 코드로 바꿔야 한다.</p>
     */
    public static final List<String> QUERYABLE_TEAM_CODES = CANONICAL_CODE_LIST.stream()
            .flatMap(teamCode -> TeamCodeResolver.resolveVariants(teamCode).stream())
            .distinct()
            .toList();

    private KboTeamCodePolicy() {
    }

    public static boolean isCanonicalTeamCode(String teamCode) {
        String normalized = TeamCodeResolver.resolveCanonical(teamCode);
        return normalized != null && CANONICAL_CODES.contains(normalized);
    }
}
