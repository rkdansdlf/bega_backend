package com.example.homepage;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeScopedNavigationDto {

    private String resolvedDate;
    private String prevGameDate;
    private String nextGameDate;
    private boolean hasPrev;
    private boolean hasNext;
}
