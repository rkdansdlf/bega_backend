package com.example.demo.prediction;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PredictionRequestDto {

	private String gameId;		  // 경기 id
	private String votedTeam;    // home or away
}
