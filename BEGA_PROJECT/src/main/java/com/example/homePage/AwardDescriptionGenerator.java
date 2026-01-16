package com.example.homePage;

import com.example.demo.entity.AwardEntity;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AwardDescriptionGenerator {

    // Mock stats database (In reality, fetch from SeasonStatsRepository)
    private final Map<String, Map<String, Object>> playerStats = new HashMap<>();

    public AwardDescriptionGenerator() {
        // Initialize with some mock data for 2025 winners
        Map<String, Object> ponceStats = new HashMap<>();
        ponceStats.put("wins", 17);
        ponceStats.put("losses", 1);
        ponceStats.put("era", 1.89);
        playerStats.put("코디 폰세", ponceStats);

        Map<String, Object> ahnStats = new HashMap<>();
        ahnStats.put("avg", 0.334);
        ahnStats.put("hr", 22);
        ahnStats.put("rbi", 80);
        playerStats.put("안현민", ahnStats);

        Map<String, Object> diazStats = new HashMap<>();
        diazStats.put("hr", 50);
        diazStats.put("rbi", 158);
        diazStats.put("special", "history_hr_rbi"); // Special flag for "First Ever"
        playerStats.put("르윈 디아즈", diazStats);

        Map<String, Object> nohStats = new HashMap<>();
        nohStats.put("holds", 35);
        nohStats.put("games", 77);
        nohStats.put("age", 40); // For "Oldest" check
        playerStats.put("노경은", nohStats);

        Map<String, Object> parkStats = new HashMap<>();
        parkStats.put("sb", 20); // stolen bases
        parkStats.put("consecutive_seasons", 12);
        playerStats.put("박해민", parkStats);

        Map<String, Object> yangStats = new HashMap<>();
        yangStats.put("avg", 0.337);
        yangStats.put("category", "Catcher");
        playerStats.put("양의지", yangStats);
    }

    public String generateDescription(AwardEntity award) {
        String player = award.getPlayerName();
        Map<String, Object> stats = playerStats.get(player);

        if (stats == null) {
            return "시즌 기록 정보 없음";
        }

        String type = award.getAwardType();

        // 1. MVP or Pitcher Awards
        if (type.contains("MVP") || type.contains("투수") || player.equals("코디 폰세")) {
            if (stats.containsKey("wins")) {
                return String.format("%d승 %d패 평균자책점 %.2f",
                        stats.get("wins"), stats.get("losses"), stats.get("era"));
            }
        }

        // 2. Rookie or Batter Awards
        if (type.contains("신인") || type.contains("타자") || player.equals("안현민")) {
            return String.format("타율 %.3f %d홈런 %d타점",
                    stats.get("avg"), stats.get("hr"), stats.get("rbi"));
        }

        // 3. Special Record Checks (Rule Based)

        // HR/RBI Award (Diaz)
        if (player.equals("르윈 디아즈")) {
            int hr = (int) stats.get("hr");
            int rbi = (int) stats.get("rbi");
            if (hr >= 50 && rbi >= 150) {
                return String.format("역대 최초 %d홈런 %d타점", hr, rbi);
            }
        }

        // Hold Award (Noh)
        if (player.equals("노경은")) {
            int age = (int) stats.get("age");
            if (age >= 40) {
                return String.format("%d경기 %d홀드 최고령 홀드 갱신", stats.get("games"), stats.get("holds"));
            }
        }

        // Stolen Base (Park)
        if (player.equals("박해민")) {
            return String.format("6월 역대 최초 %d시즌 연속 %d도루", stats.get("consecutive_seasons"), stats.get("sb"));
        }

        // Batting Avg (Yang)
        if (player.equals("양의지")) {
            return String.format("타율 %.3f 3번째 포수 타격왕", stats.get("avg"));
        }

        return "기록 집계 중";
    }
}
