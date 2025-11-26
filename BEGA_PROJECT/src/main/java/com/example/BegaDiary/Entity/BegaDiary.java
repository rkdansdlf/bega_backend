package com.example.BegaDiary.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.entity.UserEntity;

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
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@Table(name = "bega_diary")
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
	    	if (koreanName == null || koreanName.trim().isEmpty()) { // 👈 null 또는 빈 문자열인 경우
	            throw new IllegalArgumentException("이모지 이름은 필수 입력 항목입니다."); // 400 Bad Request 유도
	        }
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
	
	public static enum DiaryWinning {
		WIN,
		LOSE,
		DRAW
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	
	@Column(nullable = false, unique = true)
	private LocalDate diaryDate;  // 다이어리 날짜 중복 금지
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="game_id", nullable=false)
	private BegaGame game;
	
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name="user_id", nullable=false)
	private UserEntity user;
	
	@Column(length = 500)
	private String memo;
	
	@Column(nullable = false)
	private String team;

	@Column(nullable = false)
	private String stadium;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiaryEmoji mood;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiaryType type;
	
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DiaryWinning winning;

	@ElementCollection
	private List<String> photoUrls = new ArrayList<>();  // 사진 URL 목록
	
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt; 
	
	@Column(nullable = false)
	private LocalDateTime updatedAt;  
	
	@Builder
	public BegaDiary(LocalDate diaryDate, BegaGame game, 
	                 String memo, DiaryEmoji mood, DiaryType type, DiaryWinning winning,
	                 List<String> photoUrls, UserEntity user, String team, String stadium) {
	    this.diaryDate = diaryDate;
	    this.game = game;
	    this.memo = memo;
	    this.mood = mood;
	    this.type = type;
	    this.winning = winning;
	    this.photoUrls = photoUrls != null ? photoUrls : new ArrayList<>();
	    this.user = user;
	    this.team = team;
	    this.stadium = stadium;
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
