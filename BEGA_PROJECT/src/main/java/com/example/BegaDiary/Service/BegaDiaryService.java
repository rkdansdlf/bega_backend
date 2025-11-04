package com.example.BegaDiary.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.BegaDiary.DiaryEmoji;
import com.example.BegaDiary.Entity.BegaDiary.DiaryType;
import com.example.BegaDiary.Entity.BegaGame;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Exception.DiaryAlreadyExistsException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.BegaGameRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BegaDiaryService {
    
    private final BegaDiaryRepository diaryRepository;
    private final BegaGameRepository gameRepository;
    private final BegaGameService gameService;
    
    // 전체 다이어리 조회
    public List<DiaryResponseDto> getAllDiaries() {
        List<BegaDiary> diaries = this.diaryRepository.findAll();
        
        // Entity List → DTO List 변환
        return diaries.stream()
            .map(DiaryResponseDto::from)
            .collect(Collectors.toList());
    }
    
    // 특정 다이어리 조회
    public DiaryResponseDto getDiaryById(Long id) {
        BegaDiary diary = this.diaryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("해당 다이어리를 찾을 수 없습니다. id: " + id));
        
        return DiaryResponseDto.from(diary);
    }
    
    // 다이어리 저장
    @Transactional
    public BegaDiary save(DiaryRequestDto requestDto) {
        // 1. 날짜 파싱
        LocalDate diaryDate = LocalDate.parse(requestDto.getDate());
        
        // 2. 중복 체크
        if (diaryRepository.existsByDiaryDate(diaryDate)) {
            throw new DiaryAlreadyExistsException(
                diaryDate + "에 이미 다이어리가 작성되어 있습니다."
            );
        }
        
        // 3. Enum 변환
        DiaryEmoji mood = DiaryEmoji.fromKoreanName(requestDto.getEmojiName());
        
        // 4. 빌더로 엔티티 생성
        BegaGame game = null;
    	if(requestDto.getGameId() != null) {
    		game = gameService.getGameById(requestDto.getGameId());
    	}
    	
        BegaDiary diary = BegaDiary.builder()
            .diaryDate(diaryDate)
            .memo(requestDto.getMemo())
            .mood(mood)
            .type(DiaryType.ATTENDED)
            .photoUrls(requestDto.getPhotos())
            .game(game)
            .build();
        
        // 5. DB 저장
        return diaryRepository.save(diary);
    }
    
    // 다이어리 수정
    @Transactional
    public BegaDiary update(Long id, DiaryRequestDto requestDto) {
        // 1. 기존 다이어리 조회
        BegaDiary diary = this.diaryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("해당 다이어리를 찾을 수 없습니다. id: " + id));
        
        // 2. Enum 변환
        DiaryEmoji mood = DiaryEmoji.fromKoreanName(requestDto.getEmojiName());
        
        // 3. 다이어리 수정
        diary.updateDiary(
            requestDto.getMemo(),
            mood,
            requestDto.getPhotos()
        );
        
        return diary;
    }
    
    // 다이어리 삭제
    @Transactional
    public void delete(Long id) {
        BegaDiary diary = this.diaryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("해당 다이어리를 찾을 수 없습니다. id: " + id));
        
        this.diaryRepository.delete(diary);
    }
}