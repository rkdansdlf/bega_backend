package com.example.prediction;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PredictionMyVotesRequestDto {

	@NotNull
	@Size(max = 250)
	private List<@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]+$") String> gameIds;
}
