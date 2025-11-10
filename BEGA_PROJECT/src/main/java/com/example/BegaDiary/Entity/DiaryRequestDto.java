package com.example.BegaDiary.Entity;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DiaryRequestDto {
    private String date;        // "2025-10-10" 형식
    private String type;
    private Long gameId;
    private String memo;        // 메모
    private List<String> photos; // 사진 URL 목록
    private String emojiName;   // 이모지 한글명
    private String winningName;
}