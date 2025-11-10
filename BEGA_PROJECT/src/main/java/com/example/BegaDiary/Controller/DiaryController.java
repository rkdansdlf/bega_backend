package com.example.BegaDiary.Controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Entity.GameResponseDto;
import com.example.BegaDiary.Service.BegaDiaryService;
import com.example.BegaDiary.Service.BegaGameService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
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
		List<DiaryResponseDto> diaries = this.diaryService.getAllDiaries(principal.getName());
		return ResponseEntity.ok(diaries);
	}
	
	@PreAuthorize("isAuthenticated()")
	@PostMapping("/save")
    public ResponseEntity<DiaryResponseDto> saveDiary(@RequestBody DiaryRequestDto requestDto) {
        BegaDiary savedDiary = this.diaryService.save(requestDto);
        DiaryResponseDto response = DiaryResponseDto.from(savedDiary);
        return ResponseEntity.ok(response);
    }
    
    // 특정 다이어리 조회
	@PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<DiaryResponseDto> getDiary(@PathVariable Long id) {
        DiaryResponseDto diary = this.diaryService.getDiaryById(id);
        return ResponseEntity.ok(diary);
    }
    
    // 다이어리 수정
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/modify")
    public ResponseEntity<DiaryResponseDto> updateDiary(
            @PathVariable Long id, 
            @RequestBody DiaryRequestDto requestDto) {
        BegaDiary updatedDiary = this.diaryService.update(id, requestDto);
        DiaryResponseDto response = DiaryResponseDto.from(updatedDiary);
        return ResponseEntity.ok(response);
    }
    
    // 다이어리 삭제
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteDiary(@PathVariable Long id) {
        this.diaryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
