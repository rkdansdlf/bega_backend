package com.example.cheerboard.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Builder
public class CheerVoteId implements Serializable {
    private String gameId;
    private String teamId;
}
