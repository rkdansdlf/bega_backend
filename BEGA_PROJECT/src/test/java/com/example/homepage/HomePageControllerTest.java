package com.example.homepage;

import com.example.common.exception.GlobalExceptionHandler;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HomePageControllerTest {

    private MockMvc mockMvc;
    private HomePageGameService homePageGameService;

    @BeforeEach
    void setUp() {
        homePageGameService = mock(HomePageGameService.class);
        HomePageController controller = new HomePageController(homePageGameService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("홈 일정 조회 실패 시 빈 배열 fallback을 반환한다")
    void getGamesByDateReturnsEmptyListFallback() throws Exception {
        given(homePageGameService.getGamesByDate(eq(LocalDate.of(2026, 3, 13))))
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/schedule")
                        .param("date", "2026-03-13"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    @DisplayName("리그 시작일 조회 실패 시 오늘 날짜 fallback을 반환한다")
    void getLeagueStartDatesReturnsTodayFallback() throws Exception {
        String today = LocalDate.now().toString();
        given(homePageGameService.getLeagueStartDates())
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/league-start-dates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.regularSeasonStart").value(today))
                .andExpect(jsonPath("$.postseasonStart").value(today))
                .andExpect(jsonPath("$.koreanSeriesStart").value(today));
    }

    @Test
    @DisplayName("일정 네비게이션 조회 실패 시 빈 네비게이션 fallback을 반환한다")
    void getScheduleNavigationReturnsEmptyFallback() throws Exception {
        given(homePageGameService.getScheduleNavigation(eq(LocalDate.of(2026, 3, 13))))
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/schedule/navigation")
                        .param("date", "2026-03-13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prevGameDate").doesNotExist())
                .andExpect(jsonPath("$.nextGameDate").doesNotExist())
                .andExpect(jsonPath("$.hasPrev").value(false))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    @DisplayName("잘못된 날짜 파라미터는 validation 에러로 표준화된다")
    void invalidDateReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/kbo/schedule")
                        .param("date", "bad-date"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors.date").value("date 값 형식이 올바르지 않습니다."));
    }

    @Test
    @DisplayName("팀 순위 조회 실패 시 빈 배열 fallback을 반환한다")
    void getTeamRankingsReturnsEmptyListFallback() throws Exception {
        given(homePageGameService.getTeamRankings(eq(2026)))
                .willThrow(new RuntimeException("db unavailable"));

        mockMvc.perform(get("/api/kbo/rankings/2026"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
