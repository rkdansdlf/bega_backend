package com.example.prediction;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class GameInningScoreRequestDto {

    @NotNull
    @Min(1)
    @Max(20)
    private Integer inning;

    @NotBlank
    @Pattern(regexp = "^(home|away)$", message = "teamSide는 'home' 또는 'away'이어야 합니다.")
    private String teamSide;

    private String teamCode;

    @NotNull
    @Min(0)
    private Integer runs;

    private Boolean isExtra;
}
