package com.example.homepage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeScheduleNavigationDto {

    private String prevGameDate;
    private String nextGameDate;
    private boolean hasPrev;
    private boolean hasNext;
}
