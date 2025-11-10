package com.example.teamRecommendationTest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;


import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamRecommendationTestService {

    private final TeamRecommendationTestRepository teamRecommendationTestRepository;

    public TeamResultDto CalculateBestTeam(Long userId, TeamUserAnswersDto teamUserAnswersDto) {
    	
    	
    	// DB에 저장된 모든 팀의 정보를 리스트 형태로 불러옴
        List<TeamRecommendationTest> allTeams = teamRecommendationTestRepository.findAll();
        
        // 각 팀의 점수를 저장할 Map 생성 및 0으로 초기화
        Map<String, Integer> scores = new HashMap<>();
        allTeams.forEach(teamRecommendationTest -> scores.put(teamRecommendationTest.getTeamId(), 0));

        // 질문별 점수
        Map<String, Integer> pointWeights = Map.of(
            "hometown", 15, 
            "stadium", 20, 
            "firstTeam", 25,
            "gameStyle", 10, 
            "brand", 20, 
            "playerStyle", 5, 
            "teamImage", 5
        );

        // 사용자가 제출한 답변들을 가져옴
        Map<String, String> answers = teamUserAnswersDto.getAnswers();

        // 사용자의 각 답변을 순회하며 점수를 계산
        answers.forEach((questionId, selectedValue) -> {
 
        	// 현재 질문에 해당하는 점수를 가져옴, 없으면 0
            int points = pointWeights.getOrDefault(questionId, 0);
            
            // 모든 팀 중에서 사용자의 답변 값을 프로필에 포함하는 팀들을 찾아 점수 부여
            allTeams.stream()
                .filter(teamRecommendationTest -> teamRecommendationTest.getProfiles().contains(selectedValue))
                .forEach(teamRecommendationTest -> scores.computeIfPresent(
                		teamRecommendationTest.getTeamId(), 
                    (key, currentScore) -> currentScore + points
                ));
        });
        
        // 가장 높은 점수를 받은 팀의 ID를 찾음
        String bestTeamId = scores.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("lg");

        // 찾은 bestTeamId를 이용해 최종 팀 정보를 가져옴 
        TeamRecommendationTest resultTeam = allTeams.stream()
            .filter(teamRecommendationTest -> teamRecommendationTest.getTeamId().equals(bestTeamId))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("팀을 찾을 수 없습니다."));

        // 최종적으로 결정된 팀의 이름과 색상 정보를 반환
        return new TeamResultDto(resultTeam.getTeamName(), resultTeam.getColor());
    }
}

