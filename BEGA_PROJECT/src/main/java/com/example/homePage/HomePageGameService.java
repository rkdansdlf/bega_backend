package com.example.homePage;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.repository.GameRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;

import static com.example.common.config.CacheConfig.*;

@Service
@RequiredArgsConstructor
public class HomePageGameService {

    private final GameRepository gameRepository;
    private final HomePageTeamRepository homePageTeamRepository;

    private final Map<String, HomePageTeam> teamMap = new ConcurrentHashMap<>();

    @PostConstruct
    @Transactional(readOnly = true)
    public void init() {
        try {
            homePageTeamRepository.findAll().forEach(team -> teamMap.put(team.getTeamId(), team));
        } catch (Exception e) {
            // 에러 발생해도 애플리케이션은 계속 실행
        }
    }

    private HomePageTeam getTeam(String teamId) {
        if (teamMap.isEmpty()) {
            init();
        }
        return teamMap.getOrDefault(teamId, new HomePageTeam());
    }

    @Cacheable(value = GAME_SCHEDULE, key = "#date.toString()")
    public List<HomePageGameDto> getGamesByDate(LocalDate date) {
        List<GameEntity> games = gameRepository.findByGameDate(date);

        return games.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    private HomePageGameDto convertToDto(GameEntity game) {
        HomePageTeam homeTeam = getTeam(game.getHomeTeam());
        HomePageTeam awayTeam = getTeam(game.getAwayTeam());

        String leagueType = determineLeagueType(game.getGameDate());
        String gameInfo = "";

        // 한국시리즈 경기 정보
        if ("KOREAN_SERIES".equals(leagueType)) {
            gameInfo = "한국시리즈";
        }
        // 포스트시즌 특정 경기 정보
        else if ("POSTSEASON".equals(leagueType) &&
                game.getAwayTeam().equals("삼성") &&
                game.getHomeTeam().equals("한화")) {
            gameInfo = "PO 4차전";
        }

        String gameStatusKr = convertGameStatus(game.getGameStatus());

        // 점수 데이터 처리 with 상태별 검증
        Integer homeScore = game.getHomeScore();
        Integer awayScore = game.getAwayScore();

        // 경기 종료 상태인데 점수가 없으면 0으로 초기화 (데이터 정합성 보장)
        if ("COMPLETED".equals(game.getGameStatus())) {
            homeScore = (homeScore != null) ? homeScore : 0;
            awayScore = (awayScore != null) ? awayScore : 0;
        }

        return HomePageGameDto.builder()
                .gameId(game.getGameId())
                .stadium(game.getStadium())
                .gameStatus(game.getGameStatus())
                .gameStatusKr(gameStatusKr)
                .homeTeam(homeTeam.getTeamId())
                .homeTeamFull(homeTeam.getTeamName())
                .awayTeam(awayTeam.getTeamId())
                .awayTeamFull(awayTeam.getTeamName())
                .homeScore(homeScore)
                .awayScore(awayScore)
                .time("18:30")
                .leagueType(leagueType)
                .gameInfo(gameInfo)
                .build();
    }

    private String convertGameStatus(String status) {
        if (status == null)
            return "정보 없음";

        switch (status) {
            case "SCHEDULED":
                return "경기 예정";
            case "COMPLETED":
                return "경기 종료";
            case "CANCELLED":
                return "경기 취소";
            case "POSTPONED":
                return "경기 연기";
            case "DRAW":
                return "무승부";
            default:
                return status;
        }
    }

    private String determineLeagueType(LocalDate date) {
        int month = date.getMonthValue();
        int day = date.getDayOfMonth();

        // 한국시리즈: 10월 26일 ~ 10월 31일
        if (month == 10 && day >= 26 && day <= 31) {
            return "KOREAN_SERIES";
        }
        // 비시즌
        else if (month >= 11 || month <= 2 || (month == 3 && day < 22)) {
            return "OFFSEASON";
        }
        // 포스트시즌 (한국시리즈 제외): 10월 6일 ~ 10월 25일
        else if (month == 10 && day >= 6 && day <= 25) {
            return "POSTSEASON";
        }
        // 정규시즌
        else if ((month == 3 && day >= 22) || (month >= 4 && month <= 9) || (month == 10 && day <= 5)) {
            return "REGULAR";
        }
        // 기타 (OFFSEASON으로 처리)
        else {
            return "OFFSEASON";
        }
    }

    // v_team_rank_all 뷰에서 순위 데이터를 가져오도록 수정
    @Cacheable(value = TEAM_RANKINGS, key = "#seasonYear")
    @Transactional(readOnly = true)
    public List<HomePageTeamRankingDto> getTeamRankings(int seasonYear) {
        List<Object[]> results = gameRepository.findTeamRankingsBySeason(seasonYear);

        return results.stream()
                .map(row -> HomePageTeamRankingDto.builder()
                        .rank(((Number) row[0]).intValue()) // season_rank (bigint)
                        .teamId((String) row[1]) // team_id
                        .teamName((String) row[2]) // team_name
                        .wins(((Number) row[3]).intValue()) // wins (bigint)
                        .losses(((Number) row[4]).intValue()) // losses (bigint)
                        .draws(((Number) row[5]).intValue()) // draws (bigint)
                        .winRate(row[6].toString()) // win_pct (numeric)
                        .games(((Number) row[7]).intValue()) // games_played (bigint)
                        .build())
                .collect(Collectors.toList());
    }

    // 리그 시작 날짜 조회
    @Cacheable(value = LEAGUE_DATES, key = "T(java.time.LocalDate).now().getYear()")
    @Transactional(readOnly = true)
    public LeagueStartDatesDto getLeagueStartDates() {
        LocalDate now = LocalDate.now();
        // int currentYear = now.getYear();
        int seasonYear = now.getYear();

        // DB에서 각 리그의 첫 경기 날짜 조회
        LocalDate regularStart = gameRepository
                .findFirstRegularSeasonDate(seasonYear)
                .orElse(LocalDate.of(seasonYear, 3, 22));

        LocalDate postseasonStart = gameRepository
                .findFirstPostseasonDate(seasonYear)
                .orElse(LocalDate.of(seasonYear, 10, 6));

        LocalDate koreanSeriesStart = gameRepository
                .findFirstKoreanSeriesDate(seasonYear)
                .orElse(LocalDate.of(seasonYear, 10, 26));

        return LeagueStartDatesDto.builder()
                .regularSeasonStart(regularStart.toString())
                .postseasonStart(postseasonStart.toString())
                .koreanSeriesStart(koreanSeriesStart.toString())
                .build();
    }

    // 날짜 네비게이션 정보 조회
    public ScheduleNavigationDto getScheduleNavigation(LocalDate date) {
        LocalDate prev = gameRepository.findPrevGameDate(date).orElse(null);
        LocalDate next = gameRepository.findNextGameDate(date).orElse(null);

        return ScheduleNavigationDto.builder()
                .prevGameDate(prev)
                .nextGameDate(next)
                .hasPrev(prev != null)
                .hasNext(next != null)
                .build();
    }
}