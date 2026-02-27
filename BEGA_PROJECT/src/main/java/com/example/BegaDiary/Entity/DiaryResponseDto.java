package com.example.BegaDiary.Entity;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import com.example.BegaDiary.Utils.BaseballConstants;
import com.example.kbo.entity.GameEntity;
import com.example.leaderboard.dto.SeatViewRewardDto;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryResponseDto {
    private Long id;
    private String date;
    private Long gameId;
    private String team;
    private String stadium; 
    private String emojiName;
    private String winningName;
    private String memo;
    private List<String> photos;
    private String type;

    // 좌석 정보
    private String section;
    private String block;
    private String seatRow;
    private String seatNumber;

    // 시야 사진 기여 리워드 (저장 직후에만 포함, 조회 시 null)
    private SeatViewRewardDto seatViewReward;

    public static DiaryResponseDto from(BegaDiary diary) {
    	GameEntity game = diary.getGame();
        return DiaryResponseDto.builder()
            .id(diary.getId())
            .date(diary.getDiaryDate().toString())
            .gameId(game != null ? game.getId() : null)
            .team(diary.getTeam())
            .stadium(diary.getStadium())
            .emojiName(diary.getMood().getKoreanName())
            .winningName(diary.getWinning() != null ? diary.getWinning().name() : null)
            .memo(diary.getMemo())
            .photos(diary.getPhotoUrls())
            .type(diary.getType().name().toLowerCase())
            .section(diary.getSection())
            .block(diary.getBlock())
            .seatRow(diary.getSeatRow())
            .seatNumber(diary.getSeatNumber())
            .build();
    }
    
    public static DiaryResponseDto from(BegaDiary diary, List<String> signedUrls) {
        GameEntity game = diary.getGame();
        String team = diary.getTeam();
        String stadium = diary.getStadium();
        if (team != null && team.contains("-") && team.length() <= 10) {
            String[] teams = team.split("-");
            if (teams.length == 2) {
                String homeTeam = BaseballConstants.getTeamKoreanName(teams[0]);
                String awayTeam = BaseballConstants.getTeamKoreanName(teams[1]);
                team = homeTeam + " vs " + awayTeam;
            }
        }
        if (stadium != null && stadium.length() <= 4) {
            stadium = BaseballConstants.getFullStadiumName(stadium);
        }

        return DiaryResponseDto.builder()
            .id(diary.getId())
            .date(diary.getDiaryDate().toString())
            .gameId(game != null ? game.getId() : null)
            .team(team)
            .stadium(stadium)
            .emojiName(diary.getMood().getKoreanName())
            .winningName(diary.getWinning() != null ? diary.getWinning().name() : null)
            .memo(diary.getMemo())
            .photos(signedUrls)
            .type(diary.getType().name().toLowerCase())
            .section(diary.getSection())
            .block(diary.getBlock())
            .seatRow(diary.getSeatRow())
            .seatNumber(diary.getSeatNumber())
            .build();
    }
}
