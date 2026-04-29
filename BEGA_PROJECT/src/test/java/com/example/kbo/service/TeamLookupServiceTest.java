package com.example.kbo.service;

import com.example.kbo.dto.TeamSummaryDto;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamLookupServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private TeamLookupService teamLookupService;

    @Test
    @DisplayName("활성 팀 조회는 findAllActiveTeams 결과를 DTO로 매핑한다")
    void getActiveTeamSummaries_mapsToDto() {
        TeamEntity team = new TeamEntity();
        when(teamRepository.findAllActiveTeams()).thenReturn(List.of(team));

        List<TeamSummaryDto> result = teamLookupService.getActiveTeamSummaries();

        assertThat(result).hasSize(1);
        verify(teamRepository).findAllActiveTeams();
    }

    @Test
    @DisplayName("전체 팀 조회는 findAll 결과를 DTO로 매핑한다")
    void getAllTeamSummaries_mapsToDto() {
        TeamEntity team = new TeamEntity();
        when(teamRepository.findAll()).thenReturn(List.of(team));

        List<TeamSummaryDto> result = teamLookupService.getAllTeamSummaries();

        assertThat(result).hasSize(1);
        verify(teamRepository).findAll();
    }
}
