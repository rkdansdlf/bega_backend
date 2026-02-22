package com.example.prediction;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PredictionMyVotesRequestDto {

	@NotNull
	private List<String> gameIds;
}
