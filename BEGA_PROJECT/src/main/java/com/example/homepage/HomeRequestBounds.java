package com.example.homepage;

import com.example.common.exception.BadRequestBusinessException;
import java.time.LocalDate;

final class HomeRequestBounds {

    static final int MIN_SUPPORTED_SEASON_YEAR = 1982;
    static final int MAX_SUPPORTED_SEASON_YEAR = 2030;
    static final LocalDate MIN_SUPPORTED_DATE = LocalDate.of(MIN_SUPPORTED_SEASON_YEAR, 1, 1);
    static final LocalDate MAX_SUPPORTED_DATE = LocalDate.of(MAX_SUPPORTED_SEASON_YEAR, 12, 31);

    private HomeRequestBounds() {
    }

    static LocalDate requireSupportedDate(LocalDate date) {
        LocalDate selectedDate = date == null ? LocalDate.now() : date;
        if (selectedDate.isBefore(MIN_SUPPORTED_DATE) || selectedDate.isAfter(MAX_SUPPORTED_DATE)) {
            throw new BadRequestBusinessException(
                    "HOME_DATE_OUT_OF_RANGE",
                    "홈 경기 조회 날짜는 1982-01-01부터 2030-12-31까지 입력할 수 있습니다.");
        }
        return selectedDate;
    }

    static void requireSupportedSeasonYear(Integer seasonYear) {
        if (seasonYear != null
                && (seasonYear < MIN_SUPPORTED_SEASON_YEAR || seasonYear > MAX_SUPPORTED_SEASON_YEAR)) {
            throw new BadRequestBusinessException(
                    "HOME_SEASON_OUT_OF_RANGE",
                    "시즌 연도는 1982년부터 2030년까지 입력할 수 있습니다.");
        }
    }
}
