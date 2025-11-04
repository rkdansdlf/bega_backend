package com.example.BegaDiary.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class BegaDiary {
	public static enum DiaryEmoji {
	    HAPPY("즐거움"),
	    FULL("배부름"),
	    BEST("최고"),
	    ANGRY("분노"),
	    WORST("최악");
	    
	    private final String koreanName;
	    
	    DiaryEmoji(String koreanName) {
	        this.koreanName = koreanName;
	    }
	    
	    public String getKoreanName() {
	        return koreanName;
	    }
	    
	    public static DiaryEmoji fromKoreanName(String koreanName) {
	        for (DiaryEmoji emoji : DiaryEmoji.values()) {
	            if (emoji.getKoreanName().equals(koreanName)) {
	                return emoji;
	            }
	        }
	        throw new IllegalArgumentException("Invalid emoji name: " + koreanName);
	    }
	}

	// 다이어리 타입 Enum
	public static enum DiaryType {
	    ATTENDED,   // 직관
	    SCHEDULED   // 예정
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, unique = true)
	private LocalDate diaryDate;  // 다이어리 날짜 중복 금지
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="game_id")
	private BegaGame game;
	
	@Column(length = 500)
	private String memo;  // 메모
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiaryEmoji mood;  // 기분
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiaryType type;  // 다이어리 타입
	
	@ElementCollection
	private List<String> photoUrls = new ArrayList<>();  // 사진 URL 목록
	
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;  // 생성 시간
	
	@Column(nullable = false)
	private LocalDateTime updatedAt;  // 수정 시간
	
	@Builder
	public BegaDiary(LocalDate diaryDate, BegaGame game, 
	                 String memo, DiaryEmoji mood, DiaryType type, List<String> photoUrls) {
	    this.diaryDate = diaryDate;
	    
	    this.game = game;
	    this.memo = memo;
	    this.mood = mood;
	    this.type = type;
	    this.photoUrls = photoUrls != null ? photoUrls : new ArrayList<>();
	    this.createdAt = LocalDateTime.now();
	    this.updatedAt = LocalDateTime.now();
	}
	
	// 다이어리 수정 메서드
	public void updateDiary(String memo, DiaryEmoji mood, List<String> photoUrls) {
	    
	    this.memo = memo;
	    this.mood = mood;
	    if (photoUrls != null) {
	        this.photoUrls = photoUrls;
	    }
	    this.updatedAt = LocalDateTime.now();
	}
}
