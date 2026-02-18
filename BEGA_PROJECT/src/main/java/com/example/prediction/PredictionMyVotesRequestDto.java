package com.example.prediction;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PredictionMyVotesRequestDto {

	private List<String> gameIds;
}

