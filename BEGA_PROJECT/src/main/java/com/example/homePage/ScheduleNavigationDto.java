package com.example.homePage;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleNavigationDto {
    private LocalDate prevGameDate;
    private LocalDate nextGameDate;
    private boolean hasPrev;
    private boolean hasNext;
}
