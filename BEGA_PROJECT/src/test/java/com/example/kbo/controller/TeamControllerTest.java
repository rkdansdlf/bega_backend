package com.example.kbo.controller;

import com.example.kbo.dto.TeamSummaryDto;
import com.example.kbo.service.TeamLookupService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamControllerTest {

    @Mock
    private TeamLookupService teamLookupService;

    @InjectMocks
    private TeamController controller;

    @Test
    @DisplayName("includeInactive=true이면 전체 팀을 반환한다")
    void getTeams_includeInactiveTrue_returnsAll() {
        TeamSummaryDto dto = new TeamSummaryDto("KIA", "기아 타이거즈", "기아", true);
        when(teamLookupService.getAllTeamSummaries()).thenReturn(List.of(dto));

        ResponseEntity<List<TeamSummaryDto>> result = controller.getTeams(true);

        assertThat(result.getBody()).hasSize(1);
        verify(teamLookupService).getAllTeamSummaries();
    }

    @Test
    @DisplayName("includeInactive=false이면 활성 팀만 반환한다")
    void getTeams_includeInactiveFalse_returnsActive() {
        when(teamLookupService.getActiveTeamSummaries()).thenReturn(List.of());

        ResponseEntity<List<TeamSummaryDto>> result = controller.getTeams(false);

        assertThat(result.getBody()).isEmpty();
        verify(teamLookupService).getActiveTeamSummaries();
    }

    @Test
    @DisplayName("활성 팀 전용 엔드포인트가 올바른 결과를 반환한다")
    void getActiveTeams_returnsActiveOnly() {
        when(teamLookupService.getActiveTeamSummaries()).thenReturn(List.of());

        ResponseEntity<List<TeamSummaryDto>> result = controller.getActiveTeams();

        assertThat(result.getBody()).isEmpty();
        verify(teamLookupService).getActiveTeamSummaries();
    }
}
