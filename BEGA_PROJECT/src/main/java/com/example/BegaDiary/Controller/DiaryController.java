package com.example.BegaDiary.Controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

import com.example.BegaDiary.Exception.ImageProcessingException;
import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.DiaryImageUploadResponse;
import com.example.BegaDiary.Entity.DiaryRequestDto;
import com.example.BegaDiary.Entity.DiaryResponseDto;
import com.example.BegaDiary.Entity.DiaryStatisticsDto;
import com.example.BegaDiary.Entity.GameResponseDto;
import com.example.BegaDiary.Entity.SeatViewCandidateDto;
import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Entity.SeatViewPhotoDto;
import com.example.BegaDiary.Entity.SeatViewSelectionRequest;
import com.example.BegaDiary.Service.BegaDiaryService;
import com.example.BegaDiary.Service.BegaGameService;
import com.example.BegaDiary.Service.SeatViewService;
import com.example.cheerboard.storage.service.ImageService;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.BusinessException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/diary")
@RequiredArgsConstructor
@Slf4j
public class DiaryController {

    private final BegaDiaryService diaryService;
    private final BegaGameService gameService;
    private final ImageService imageService;
    private final SeatViewService seatViewService;

    @GetMapping("/games")
    public ResponseEntity<List<GameResponseDto>> getGamesByDate(
            @RequestParam(value = "date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        log.debug("Fetching games for date: {}", date);
        List<GameResponseDto> games = gameService.getGamesByDate(date);
        return ResponseEntity.ok(games);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/entries")
    public ResponseEntity<List<DiaryResponseDto>> getDiary(Principal principal) {
        Long userId = requireUserId(principal);
        List<DiaryResponseDto> diaries = this.diaryService.getAllDiaries(userId);
        return ResponseEntity.ok(diaries);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/save")
    public ResponseEntity<?> saveDiary(
            @RequestBody DiaryRequestDto requestDto,
            Principal principal) {
        Long userId = requireUserId(principal);
        BegaDiary savedDiary = this.diaryService.save(userId, requestDto);
        DiaryResponseDto response = DiaryResponseDto.from(savedDiary);

        return ResponseEntity.ok(response);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/images")
    public ResponseEntity<?> uploadImages(
            @PathVariable("id") Long diaryId,
            @RequestPart("images") List<MultipartFile> images,
            @RequestParam(value = "sourceTypes", required = false) List<String> sourceTypes,
            Principal principal) {
        Long userId = requireUserId(principal);

        // 소유자 검증: 해당 일기가 현재 사용자 소유인지 확인
        BegaDiary ownerCheck = this.diaryService.getDiaryEntityById(diaryId);
        if (!ownerCheck.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("본인의 일기에만 이미지를 업로드할 수 있습니다.");
        }

        List<String> storagePaths;
        try {
            storagePaths = imageService.uploadDiaryImages(userId, diaryId, images)
                    .block();
        } catch (RuntimeException ex) {
            throw translateImageProcessingException(diaryId, ex);
        }

        if (storagePaths == null || storagePaths.isEmpty()) {
            return ResponseEntity.ok().body(DiaryImageUploadResponse.builder()
                    .message("업로드할 이미지가 없습니다.")
                    .diaryId(diaryId)
                    .photos(List.of())
                    .candidates(List.of())
                    .build());
        }

        List<SeatViewPhoto.SourceType> parsedSourceTypes = parseSourceTypes(sourceTypes);
        try {
            List<SeatViewCandidateDto> candidates = seatViewService.createCandidates(
                    diaryId,
                    userId,
                    images,
                    storagePaths,
                    parsedSourceTypes);

            return ResponseEntity.ok().body(DiaryImageUploadResponse.builder()
                    .message("이미지 업로드가 완료되었습니다.")
                    .diaryId(diaryId)
                    .photos(storagePaths)
                    .candidates(candidates)
                    .build());
        } catch (RuntimeException ex) {
            throw translateImageProcessingException(diaryId, ex);
        }
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/seat-view-selections")
    public ResponseEntity<?> submitSeatViewSelections(
            @PathVariable("id") Long diaryId,
            @RequestBody SeatViewSelectionRequest request,
            Principal principal) {
        Long userId = requireUserId(principal);
        var selected = seatViewService.submitSelections(
                diaryId,
                userId,
                request != null ? request.getCandidateIds() : List.of());
        return ResponseEntity.ok(java.util.Map.of(
                "message", "시야뷰 제출이 완료되었습니다.",
                "candidates", selected));
    }

    // 특정 다이어리 조회
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/{id}")
    public ResponseEntity<DiaryResponseDto> getDiary(@PathVariable("id") Long id, Principal principal) {
        Long userId = requireUserId(principal);
        DiaryResponseDto diary = this.diaryService.getDiaryById(id, userId);
        return ResponseEntity.ok(diary);
    }

    // 다이어리 수정
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/modify")
    public ResponseEntity<DiaryResponseDto> updateDiary(
            @PathVariable("id") Long id,
            @RequestBody DiaryRequestDto requestDto,
            Principal principal) {
        Long userId = requireUserId(principal);
        BegaDiary updatedDiary = this.diaryService.update(id, userId, requestDto);
        DiaryResponseDto response = DiaryResponseDto.from(updatedDiary);
        return ResponseEntity.ok(response);
    }

    // 다이어리 삭제
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteDiary(@PathVariable("id") Long id, Principal principal) {
        Long userId = requireUserId(principal);
        this.diaryService.delete(id, userId);
        return ResponseEntity.noContent().build();
    }

    // 좌석 시야 사진 목록 조회 (공개 API, 비로그인 허용)
    @GetMapping("/seat-views")
    public ResponseEntity<List<SeatViewPhotoDto>> getSeatViewPhotos(
            @RequestParam String stadium,
            @RequestParam(required = false) String section,
            @RequestParam(defaultValue = "9") int limit) {
        List<SeatViewPhotoDto> photos = diaryService.getSeatViewPhotos(stadium, section, limit);
        return ResponseEntity.ok(photos);
    }

    // 다이어리 통계
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/statistics")
    public ResponseEntity<DiaryStatisticsDto> showStatistics(Principal principal) {
        Long userId = requireUserId(principal);
        DiaryStatisticsDto statistics = this.diaryService.getStatistics(userId);
        return ResponseEntity.ok(statistics);
    }

    private List<SeatViewPhoto.SourceType> parseSourceTypes(List<String> sourceTypes) {
        if (sourceTypes == null || sourceTypes.isEmpty()) {
            return List.of();
        }
        return sourceTypes.stream()
                .filter(Objects::nonNull)
                .map(this::parseSourceType)
                .toList();
    }

    private Long requireUserId(Principal principal) {
        if (principal == null) {
            log.warn("Diary request without authenticated principal");
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        return Long.valueOf(principal.getName());
    }

    private SeatViewPhoto.SourceType parseSourceType(String value) {
        String normalized = value.trim();
        try {
            return SeatViewPhoto.SourceType.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestBusinessException(
                    "INVALID_SEAT_VIEW_SOURCE_TYPE",
                    "sourceTypes 값이 올바르지 않습니다: " + normalized);
        }
    }

    private RuntimeException translateImageProcessingException(Long diaryId, Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof BusinessException businessException) {
                return businessException;
            }
            if (current instanceof IllegalArgumentException illegalArgumentException) {
                return illegalArgumentException;
            }
            if (current instanceof AccessDeniedException accessDeniedException) {
                return accessDeniedException;
            }
            current = current.getCause() == current ? null : current.getCause();
        }

        Throwable rootCause = resolveRootCause(throwable);
        String detail = rootCause != null && rootCause.getMessage() != null && !rootCause.getMessage().isBlank()
                ? rootCause.getMessage()
                : "알 수 없는 오류";
        log.error("Unexpected diary image processing failure: diaryId={}", diaryId, throwable);
        return new ImageProcessingException(detail);
    }

    private Throwable resolveRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
