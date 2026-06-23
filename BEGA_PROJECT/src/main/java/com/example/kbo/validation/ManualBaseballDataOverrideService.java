package com.example.kbo.validation;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ManualBaseballDataOverrideService {

    private final Set<LocalDate> requiredDates;

    @Autowired
    public ManualBaseballDataOverrideService(
            @Value("${app.baseball.manual-data.required-dates:}") String requiredDates) {
        this(parseRequiredDates(requiredDates));
    }

    public ManualBaseballDataOverrideService(Set<LocalDate> requiredDates) {
        this.requiredDates = requiredDates == null
                ? Set.of()
                : Set.copyOf(requiredDates);
    }

    public static ManualBaseballDataOverrideService disabled() {
        return new ManualBaseballDataOverrideService(Set.of());
    }

    public boolean isDateRequiresManualData(LocalDate date) {
        return date != null && requiredDates.contains(date);
    }

    public void throwIfDateRequiresManualData(String scope, LocalDate date) {
        if (!isDateRequiresManualData(date)) {
            return;
        }
        throw new ManualBaseballDataRequiredException(newRequest(scope, date));
    }

    public void throwIfRangeRequiresManualData(String scope, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || requiredDates.isEmpty()) {
            return;
        }

        requiredDates.stream()
                .filter(date -> !date.isBefore(startDate) && !date.isAfter(endDate))
                .min(Comparator.naturalOrder())
                .ifPresent(date -> {
                    throw new ManualBaseballDataRequiredException(newRequest(scope, date));
                });
    }

    private ManualBaseballDataRequest newRequest(String scope, LocalDate date) {
        return new ManualBaseballDataRequest(
                scope,
                List.of(
                        new ManualBaseballDataMissingItem(
                                "game_status",
                                "경기 상태",
                                "운영자가 해당 날짜를 수동 데이터 필요 상태로 지정했습니다.",
                                "SCHEDULED, COMPLETED, CANCELLED 등"),
                        new ManualBaseballDataMissingItem(
                                "final_score",
                                "최종 점수",
                                "운영자 승인 전까지 해당 날짜의 최종 점수 또는 취소 여부를 자동 보정하지 않습니다.",
                                "home_score, away_score 또는 CANCELLED/POSTPONED")),
                "다음 야구 데이터가 필요합니다: 날짜=" + date,
                true);
    }

    private static Set<LocalDate> parseRequiredDates(String rawDates) {
        if (rawDates == null || rawDates.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(rawDates.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(LocalDate::parse)
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
