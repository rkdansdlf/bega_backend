package com.example.BegaDiary.Entity;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DiaryResponseDto {
    private Long id;
    private String date;
    private Long gameId;
    private String emojiName;
    private String memo;
    private List<String> photos;
    private String type;
    
    public static DiaryResponseDto from(BegaDiary diary) {
    	BegaGame game = diary.getGame();
        return DiaryResponseDto.builder()
            .id(diary.getId())
            .date(diary.getDiaryDate().toString())
            .gameId(game != null ? game.getId() : null)
            .emojiName(diary.getMood().getKoreanName())
            .memo(diary.getMemo())
            .photos(diary.getPhotoUrls())
            .type(diary.getType().name().toLowerCase())
            .build();
    }
}
