package com.example.prediction;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PredictionRequestDto {

	@NotBlank
	@Pattern(regexp = "^[A-Za-z0-9_-]+$")
	private String gameId;          // 경기 id
	@Pattern(regexp = "(?i)^(home|away)$")
	private String votedTeam;    // home or away
}
