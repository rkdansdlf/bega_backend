package teamRecommendationTest;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TeamResultDto {
	
    private String name;	// 최종 추천된 팀의 이름
    private String color;	// 팀 색상
}
