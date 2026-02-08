package com.example.cheerboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheerBattleStatusRes {
    private Map<String, Integer> stats;
    private String myVote; // teamId or null
}
