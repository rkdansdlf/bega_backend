package com.example.BegaDiary.Service;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.repository.GameMetadataRepository;
import com.example.kbo.repository.GameRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BegaGameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameMetadataRepository gameMetadataRepository;

    @InjectMocks
    private BegaGameService begaGameService;

    @Test
    @DisplayName("OCR 팀명이 canonical/legacy 혼재여도 경기 ID를 매칭한다")
    void findGameIdByDateAndTeams_dualReadSuccess() {
        LocalDate date = LocalDate.of(2025, 4, 1);
        GameEntity game = GameEntity.builder()
                .id(1L)
                .gameId("20250401SKKT0")
                .gameDate(date)
                .homeTeam("SK")
                .awayTeam("KT")
                .stadium("문학")
                .build();

        when(gameRepository.findByGameDateAndTeamVariants(eq(date), anyList(), anyList()))
                .thenReturn(List.of(game), List.of());
        when(gameMetadataRepository.findByGameId("20250401SKKT0"))
                .thenReturn(Optional.of(GameMetadataEntity.builder()
                        .gameId("20250401SKKT0")
                        .startTime(LocalTime.of(18, 30))
                        .stadiumName("인천SSG랜더스필드")
                        .build()));

        Long matched = begaGameService.findGameIdByDateAndTeams(
                "2025-04-01",
                "SSG",
                "KT",
                "문학",
                "18:30");

        assertThat(matched).isEqualTo(1L);
    }

    @Test
    @DisplayName("동일 점수 후보가 복수면 모호 처리로 null을 반환한다")
    void findGameIdByDateAndTeams_ambiguousReturnsNull() {
        LocalDate date = LocalDate.of(2025, 4, 2);
        GameEntity game1 = GameEntity.builder()
                .id(10L)
                .gameId("20250402SKKT0")
                .gameDate(date)
                .homeTeam("SK")
                .awayTeam("KT")
                .build();
        GameEntity game2 = GameEntity.builder()
                .id(11L)
                .gameId("20250402SSGKT1")
                .gameDate(date)
                .homeTeam("SSG")
                .awayTeam("KT")
                .build();

        when(gameRepository.findByGameDateAndTeamVariants(eq(date), anyList(), anyList()))
                .thenReturn(List.of(game1, game2), List.of());
        when(gameMetadataRepository.findByGameId("20250402SKKT0")).thenReturn(Optional.empty());
        when(gameMetadataRepository.findByGameId("20250402SSGKT1")).thenReturn(Optional.empty());

        Long matched = begaGameService.findGameIdByDateAndTeams(
                "2025-04-02",
                "SSG",
                "KT",
                null,
                null);

        assertThat(matched).isNull();
    }
}
