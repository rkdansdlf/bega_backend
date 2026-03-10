package com.example.BegaDiary.Entity;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SeatViewSelectionRequest {
    private List<Long> candidateIds;
}
