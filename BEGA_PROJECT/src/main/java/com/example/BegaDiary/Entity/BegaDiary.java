package com.example.BegaDiary.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.example.kbo.entity.GameEntity;
import com.example.auth.entity.UserEntity;

import jakarta.persistence.CollectionTable;
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
		ATTENDED, // 직관
		SCHEDULED // 예정
	}

	public static enum DiaryWinning {
		WIN,
		LOSE,
		DRAW
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "diarydate", nullable = false, unique = true)
	private LocalDate diaryDate; // 다이어리 날짜 중복 금지

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "game_id", nullable = false)
	private GameEntity game;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
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
	@CollectionTable(name = "bega_diary_photo_urls", joinColumns = @JoinColumn(name = "bega_diary_id"))
	@Column(name = "photo_urls", nullable = false, length = 2048)
	private List<String> photoUrls = new ArrayList<>(); // 사진 URL 목록

	@Column(name = "createdat", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Column(name = "updatedat", nullable = false)
	private LocalDateTime updatedAt;

	@Column(name = "ticket_verified", nullable = false)
	private boolean ticketVerified;

	@Column(name = "ticket_verified_at")
	private LocalDateTime ticketVerifiedAt;

	// 좌석 정보 필드 추가
	@Column(length = 50)
	private String section; // 예: "블루석", "1루 내야"

	@Column(length = 50)
	private String block; // 예: "101구역", "A열"

	@Column(name = "seat_row", length = 50)
	private String seatRow; // "row"는 SQL 예약어일 가능성 있음

	@Column(name = "seat_number", length = 50)
	private String seatNumber;

	@Builder
	public BegaDiary(LocalDate diaryDate, GameEntity game,
			String memo, DiaryEmoji mood, DiaryType type, DiaryWinning winning,
			List<String> photoUrls, UserEntity user, String team, String stadium,
			String section, String block, String seatRow, String seatNumber,
			boolean ticketVerified, LocalDateTime ticketVerifiedAt) {
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
		this.section = section;
		this.block = block;
		this.seatRow = seatRow;
		this.seatNumber = seatNumber;
		this.ticketVerified = ticketVerified;
		this.ticketVerifiedAt = ticketVerifiedAt;
		this.createdAt = LocalDateTime.now();
		this.updatedAt = LocalDateTime.now();
	}

	// 다이어리 수정 메서드
	public void updateDiary(
			String memo,
			DiaryEmoji mood,
			List<String> photoUrls,
			GameEntity game,
			String team,
			String stadium,
			DiaryWinning winning,
			String section,
			String block,
			String seatRow,
			String seatNumber) {
		this.memo = memo;
		this.mood = mood;
		if (photoUrls != null) {
			this.photoUrls = photoUrls;
		}
		if (game != null) {
			this.game = game;
		}
		if (team != null) {
			this.team = team;
		}
		if (stadium != null) {
			this.stadium = stadium;
		}
		if (winning != null) {
			this.winning = winning;
		}
		this.section = section;
		this.block = block;
		this.seatRow = seatRow;
		this.seatNumber = seatNumber;
		this.updatedAt = LocalDateTime.now();
	}

	public void markTicketVerified(LocalDateTime verifiedAt) {
		this.ticketVerified = true;
		this.ticketVerifiedAt = verifiedAt;
		this.updatedAt = LocalDateTime.now();
	}

	public void clearTicketVerification() {
		this.ticketVerified = false;
		this.ticketVerifiedAt = null;
		this.updatedAt = LocalDateTime.now();
	}
}
