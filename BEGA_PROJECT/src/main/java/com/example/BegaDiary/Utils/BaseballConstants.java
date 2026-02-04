package com.example.BegaDiary.Utils;

import java.util.Map;

import com.example.kbo.util.TeamCodeNormalizer;

public class BaseballConstants {
    
    public static final Map<String, String> TEAM_KOREAN_NAME_MAP = Map.ofEntries(
        Map.entry("HH", "한화 이글스"),
        Map.entry("HT", "기아 타이거즈"),
        Map.entry("KT", "KT 위즈"),
        Map.entry("LG", "LG 트윈스"),
        Map.entry("LT", "롯데 자이언츠"),
        Map.entry("NC", "NC 다이노스"),
        Map.entry("OB", "두산 베어스"),
        Map.entry("SS", "삼성 라이온즈"),
        Map.entry("SSG", "SSG 랜더스"),
        Map.entry("SK", "SSG 랜더스"),
        Map.entry("WO", "키움 히어로즈")
    );
    
    public static final Map<String, String> STADIUM_FULL_NAME_MAP = Map.ofEntries(
        Map.entry("고척", "고척스카이돔"),
        Map.entry("잠실", "잠실 야구장"),
        Map.entry("광주", "광주-기아 챔피언스 필드"),
        Map.entry("대전", "대전 한화생명 이글스 파크"),
        Map.entry("청주", "청주 야구장"),
        Map.entry("울산", "울산 문수 야구장"),
        Map.entry("수원", "수원 kt wiz 파크"),
        Map.entry("대구", "대구 삼성 라이온즈 파크"),
        Map.entry("포항", "포항 야구장"),
        Map.entry("창원", "창원NC파크"),
        Map.entry("사직", "부산 사직 야구장"),
        Map.entry("문학", "인천SSG랜더스필드")
    );
    
    public static String getFullStadiumName(String shortName) {
        return STADIUM_FULL_NAME_MAP.getOrDefault(shortName, shortName);
    }
    
    public static String getTeamKoreanName(String teamCode) {
        String normalized = TeamCodeNormalizer.normalize(teamCode);
        return TEAM_KOREAN_NAME_MAP.getOrDefault(normalized, normalized);
    }
    
    private BaseballConstants() {
    }
}
