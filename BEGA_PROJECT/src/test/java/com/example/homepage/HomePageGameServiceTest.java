package com.example.homepage;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomePageGameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private HomePageTeamRepository homePageTeamRepository;

    @InjectMocks
    private HomePageGameService homePageGameService;

    @Test
    @DisplayName("league_type_code=5 인 경우 KOREAN_SERIES로 매핑된다")
    void getGamesByDate_koreanSeriesFromSeasonType() {
        LocalDate date = LocalDate.of(2025, 10, 28);
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId("20251028SSGLG0")
                .gameDate(date)
                .homeTeam("SSG")
                .awayTeam("LG")
                .seasonId(9005)
                .gameStatus("SCHEDULED")
                .stadium("문학")
                .build();

        when(homePageTeamRepository.findAll()).thenReturn(List.of());
        when(gameRepository.findByGameDate(date)).thenReturn(List.of(game));
        when(gameRepository.findLeagueTypeCodeBySeasonId(9005)).thenReturn(Optional.of(5));

        List<HomePageGameDto> games = homePageGameService.getGamesByDate(date);

        assertThat(games).hasSize(1);
        assertThat(games.get(0).getLeagueType()).isEqualTo("KOREAN_SERIES");
        assertThat(games.get(0).getGameInfo()).isEqualTo("한국시리즈");
    }
}
