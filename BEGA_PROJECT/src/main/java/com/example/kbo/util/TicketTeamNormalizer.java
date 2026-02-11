package com.example.kbo.util;

import java.util.Map;

/**
 * OCR에서 추출된 팀 이름(한글/영문/약어)과 프론트엔드 ID를 표준 canonical ID로 정규화합니다.
 * 정규화 결과는 프론트엔드에서 사용하는 소문자 ID (doosan, lg, kia 등)입니다.
 * 이는 Party 엔티티의 homeTeam/awayTeam에 저장되는 형식과 일치합니다.
 */
public final class TicketTeamNormalizer {

    /**
     * 모든 알려진 팀 이름 → 프론트엔드 canonical ID 매핑.
     * 프론트엔드 ID는 Party 저장값과 일치합니다.
     */
    private static final Map<String, String> TEAM_NAME_MAP = Map.ofEntries(
            // 한글 풀네임
            Map.entry("삼성 라이온즈", "samsung"),
            Map.entry("삼성", "samsung"),
            Map.entry("롯데 자이언츠", "lotte"),
            Map.entry("롯데", "lotte"),
            Map.entry("LG 트윈스", "lg"),
            Map.entry("엘지", "lg"),
            Map.entry("SSG", "ssg"),
            Map.entry("SK", "ssg"),
            Map.entry("두산 베어스", "doosan"),
            Map.entry("두산", "doosan"),
            Map.entry("키움 히어로즈", "kiwoom"),
            Map.entry("키움", "kiwoom"),
            Map.entry("넥센 히어로즈", "kiwoom"),
            Map.entry("넥센", "kiwoom"),
            Map.entry("히어로즈", "kiwoom"),
            Map.entry("한화 이글스", "hanwha"),
            Map.entry("한화", "hanwha"),
            Map.entry("SSG 랜더스", "ssg"),
            Map.entry("SK 와이번스", "ssg"),
            Map.entry("NC 다이노스", "nc"),
            Map.entry("KT 위즈", "kt"),
            Map.entry("기아 타이거즈", "kia"),
            Map.entry("KIA 타이거즈", "kia"),
            Map.entry("기아", "kia"),

            // 영문 팀명 (대소문자 혼합 대응을 위해 대문자로 등록)
            Map.entry("SAMSUNG", "samsung"),
            Map.entry("LOTTE", "lotte"),
            Map.entry("DOOSAN", "doosan"),
            Map.entry("KIWOOM", "kiwoom"),
            Map.entry("HANWHA", "hanwha"),
            Map.entry("KIA", "kia"),

            // KBO 표준 코드 → 프론트엔드 ID
            Map.entry("SS", "samsung"),
            Map.entry("LT", "lotte"),
            Map.entry("DB", "doosan"),
            Map.entry("OB", "doosan"),
            Map.entry("DO", "doosan"),
            Map.entry("KH", "kiwoom"),
            Map.entry("WO", "kiwoom"),
            Map.entry("KI", "kiwoom"),
            Map.entry("NX", "kiwoom"),
            Map.entry("KW", "kiwoom"),
            Map.entry("HH", "hanwha"),
            Map.entry("HT", "kia"),

            // 이미 프론트엔드 ID인 경우 (자기 자신 매핑)
            Map.entry("DOOSAN_FE", "doosan"), // placeholder — 소문자는 아래서 처리
            Map.entry("SAMSUNG_FE", "samsung"));

    /**
     * 프론트엔드 소문자 ID 셋 — 이미 canonical이면 바로 반환
     */
    private static final java.util.Set<String> FRONTEND_IDS = java.util.Set.of(
            "samsung", "lotte", "lg", "doosan", "kiwoom",
            "hanwha", "ssg", "nc", "kt", "kia");

    private TicketTeamNormalizer() {
    }

    /**
     * 팀 이름/코드를 프론트엔드 canonical ID로 변환합니다.
     * Party에 저장된 값, OCR 원본, KBO 표준 코드를 모두 처리합니다.
     *
     * @param teamName 팀 이름/코드 (한글, 영문, 약어, 프론트엔드 ID 등)
     * @return 정규화된 프론트엔드 ID (매핑 실패 시 원본을 소문자로 반환)
     */
    public static String normalize(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return null;
        }

        String trimmed = teamName.trim();
        String lower = trimmed.toLowerCase();

        // 1. 이미 프론트엔드 canonical ID이면 바로 반환
        if (FRONTEND_IDS.contains(lower)) {
            return lower;
        }

        // 2. 대소문자 무시 + 정확한 매칭
        String result = TEAM_NAME_MAP.get(trimmed);
        if (result != null)
            return result;

        // 3. 대문자로 변환 후 시도 (영문/코드 매핑)
        result = TEAM_NAME_MAP.get(trimmed.toUpperCase());
        if (result != null)
            return result;

        // 4. TeamCodeNormalizer를 통한 기존 코드 정규화 후 재시도
        String codeNormalized = TeamCodeNormalizer.normalize(trimmed);
        result = TEAM_NAME_MAP.get(codeNormalized);
        if (result != null)
            return result;

        // 매핑 실패 시 소문자로 반환 (최선의 추측)
        return lower;
    }
}
