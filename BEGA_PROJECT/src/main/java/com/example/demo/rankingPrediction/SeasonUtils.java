package com.example.demo.rankingPrediction;

import java.time.LocalDate;

// 순위예측 기간 2025.11.01-2026.05.31 순위 로직

public class SeasonUtils {

    public static int getCurrentPredictionSeason() {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        // 11월 ~ 12월: 다음 해 시즌
        if (month >= 11) {
            return year + 1;
        }
        // 1월 ~ 5월: 현재 해 시즌
        else if (month >= 1 && month <= 5) {
            return year;
        }
        // 6월 ~ 10월: 예측 불가
        else {
            throw new IllegalStateException(
                    "현재는 순위 예측 기간이 아닙니다. (예측 가능 기간: 11월 1일 ~ 5월 31일)");
        }
    }

    public static boolean isPredictionPeriod() {
        int month = LocalDate.now().getMonthValue();
        return (month >= 11 || month <= 5);
    }
}
