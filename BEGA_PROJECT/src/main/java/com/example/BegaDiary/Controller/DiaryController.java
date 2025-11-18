package com.example.BegaDiary.Controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Entity.DiaryStatisticsDto;
import com.example.BegaDiary.Entity.GameResponseDto;
import com.example.BegaDiary.Service.BegaDiaryService;
import com.example.BegaDiary.Service.BegaGameService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
@Slf4j
public class DiaryController {
	
	private final BegaDiaryService diaryService;
	private final BegaGameService gameService;
	
	@GetMapping("/games")
	public ResponseEntity<List<GameResponseDto>> getGamesByDate(
			@RequestParam(value = "date") String date
			) {
		LocalDate localDate = LocalDate.parse(date);
		System.out.println(localDate);
		List<GameResponseDto> games = gameService.getGamesByDate(localDate);
		return ResponseEntity.ok(games);
	}
	
	@PreAuthorize("isAuthenticated()")
	@GetMapping("/entries")
	public ResponseEntity<List<DiaryResponseDto>> getDiary(Principal principal) {
		Long userId = Long.valueOf(principal.getName());
		List<DiaryResponseDto> diaries = this.diaryService.getAllDiaries(userId);
		return ResponseEntity.ok(diaries);
	}
	
	@PreAuthorize("isAuthenticated()")
	@PostMapping("/save")
    public ResponseEntity<?> saveDiary(
    		@RequestBody DiaryRequestDto requestDto,
    		Principal principal
    		) {
		
		if (principal == null) {
            System.err.println("❌ userDetails가 null입니다!");
            return ResponseEntity.status(401).body("인증되지 않은 사용자");
        }
		
		Long userId = Long.valueOf(principal.getName());
        BegaDiary savedDiary = this.diaryService.save(userId, requestDto);
        DiaryResponseDto response = DiaryResponseDto.from(savedDiary);
        return ResponseEntity.ok(response);
    }
    
	@PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/images")
    public ResponseEntity<?> uploadImages(
            @PathVariable("id") Long diaryId,
            @RequestPart("images") List<MultipartFile> images,
            Principal principal) {
        
        Long userId = Long.valueOf(principal.getName());

        // 비동기로 처리되므로 즉시 응답
        CompletableFuture<List<String>> future = diaryService.addImages(diaryId, userId, images);
        
        // 비동기 작업 완료 시 로그 (선택사항)
        future.thenAccept(paths -> 
            log.info("이미지 업로드 완료: diaryId={}, 업로드된 이미지 수={}", diaryId, paths.size())
        ).exceptionally(ex -> {
            log.error("이미지 업로드 실패: diaryId={}", diaryId, ex);
            return null;
        });
        
        return ResponseEntity.accepted().body(Map.of(
            "message", "이미지 업로드가 진행 중입니다.",
            "diaryId", diaryId
        ));
    }
	
    // 특정 다이어리 조회
	@PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<DiaryResponseDto> getDiary(@PathVariable("id") Long id) {
        DiaryResponseDto diary = this.diaryService.getDiaryById(id);
        return ResponseEntity.ok(diary);
    }
    
    // 다이어리 수정
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/modify")
    public ResponseEntity<DiaryResponseDto> updateDiary(
            @PathVariable("id") Long id, 
            @RequestBody DiaryRequestDto requestDto) {
        BegaDiary updatedDiary = this.diaryService.update(id, requestDto);
        DiaryResponseDto response = DiaryResponseDto.from(updatedDiary);
        return ResponseEntity.ok(response);
    }
    
    // 다이어리 삭제
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteDiary(@PathVariable("id") Long id) {
        this.diaryService.delete(id);
        return ResponseEntity.noContent().build();
    }
    
    // 다이어리 통계
    @PreAuthorize("isAuthenticated()")
	@GetMapping("/statistics")
	public ResponseEntity<DiaryStatisticsDto> showStatistics(Principal principal) {
		Long userId = Long.valueOf(principal.getName());
		DiaryStatisticsDto statistics = this.diaryService.getStatistics(userId);
		return ResponseEntity.ok(statistics);
	}
}
