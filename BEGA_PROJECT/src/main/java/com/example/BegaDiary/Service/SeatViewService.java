package com.example.BegaDiary.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.SeatViewCandidateDto;
import com.example.BegaDiary.Entity.SeatViewPhoto;
import com.example.BegaDiary.Entity.SeatViewPhoto.ClassificationLabel;
import com.example.BegaDiary.Entity.SeatViewPhoto.ModerationStatus;
import com.example.BegaDiary.Entity.SeatViewPhoto.SourceType;
import com.example.BegaDiary.Entity.SeatViewPhotoDto;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.admin.dto.AdminSeatViewActionReq;
import com.example.admin.dto.AdminSeatViewDto;
import com.example.cheerboard.storage.service.ImageService;
import com.example.leaderboard.dto.SeatViewRewardDto;
import com.example.leaderboard.service.ScoringService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SeatViewService {

    private final SeatViewPhotoRepository seatViewPhotoRepository;
    private final BegaDiaryRepository diaryRepository;
    private final ImageService imageService;
    private final SeatViewClassificationService seatViewClassificationService;
    private final ScoringService scoringService;

    @Transactional
    public List<SeatViewCandidateDto> createCandidates(
            Long diaryId,
            Long userId,
            List<MultipartFile> images,
            List<String> storagePaths,
            List<SourceType> sourceTypes) {
        if (images == null || storagePaths == null || images.size() != storagePaths.size()) {
            throw new IllegalArgumentException("업로드한 이미지와 저장 경로 수가 일치하지 않습니다.");
        }

        BegaDiary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new IllegalArgumentException("다이어리를 찾을 수 없습니다."));

        List<SeatViewPhoto> savedCandidates = new ArrayList<>();
        for (int index = 0; index < images.size(); index++) {
            MultipartFile image = images.get(index);
            String storagePath = storagePaths.get(index);
            SourceType sourceType = resolveSourceType(sourceTypes, index);

            SeatViewPhoto candidate = SeatViewPhoto.builder()
                    .diary(diary)
                    .userId(userId)
                    .storagePath(storagePath)
                    .sourceType(sourceType)
                    .userSelected(false)
                    .rewardGranted(false)
                    .build();

            if (sourceType == SourceType.TICKET_SCAN) {
                candidate.updateSuggestion(ClassificationLabel.TICKET, 1.0d, "티켓 스캔 이미지로 업로드되었습니다.");
            } else {
                try {
                    var classification = seatViewClassificationService.classify(image);
                    ClassificationLabel label = parseClassificationLabel(classification.getLabel());
                    candidate.updateSuggestion(label, classification.getConfidence(), classification.getReason());
                } catch (Exception ex) {
                    log.warn("Seat-view AI classification skipped for diaryId={} path={} cause={}",
                            diaryId, storagePath, ex.getMessage());
                }
            }

            savedCandidates.add(seatViewPhotoRepository.save(candidate));
        }

        return toCandidateDtos(savedCandidates);
    }

    @Transactional
    public List<SeatViewCandidateDto> submitSelections(Long diaryId, Long userId, List<Long> candidateIds) {
        BegaDiary diary = diaryRepository.findById(diaryId)
                .orElseThrow(() -> new IllegalArgumentException("다이어리를 찾을 수 없습니다."));

        if (!Objects.equals(diary.getUser().getId(), userId)) {
            throw new AccessDeniedException("본인의 시야뷰 후보만 제출할 수 있습니다.");
        }
        if (!hasSeatMetadata(diary)) {
            throw new IllegalArgumentException("좌석 정보를 입력한 뒤 시야뷰를 제출해주세요.");
        }

        Set<Long> selectedIds = candidateIds == null ? Set.of() : Set.copyOf(candidateIds);
        List<SeatViewPhoto> candidates = seatViewPhotoRepository.findByDiaryIdAndUserId(diaryId, userId);

        for (SeatViewPhoto candidate : candidates) {
            boolean shouldSelect = selectedIds.contains(candidate.getId()) && candidate.getSourceType() == SourceType.DIARY_UPLOAD;
            candidate.updateUserSelection(shouldSelect);
            seatViewPhotoRepository.save(candidate);
        }

        return toCandidateDtos(candidates);
    }

    public List<SeatViewPhotoDto> getPublicSeatViews(String stadium, String section, int limit) {
        int safeLimit = Math.max(1, limit);
        Pageable pageable = PageRequest.of(0, safeLimit);

        List<SeatViewPhotoDto> result = new ArrayList<>();
        List<SeatViewPhoto> approved = seatViewPhotoRepository.findApprovedPublicSeatViews(
                stadium,
                section,
                ModerationStatus.APPROVED,
                ClassificationLabel.SEAT_VIEW,
                pageable);

        result.addAll(toPublicDtos(approved, safeLimit));
        if (result.size() >= safeLimit) {
            return result.subList(0, safeLimit);
        }

        List<BegaDiary> legacyDiaries = diaryRepository.findLegacySeatViewPhotos(
                stadium,
                section,
                BegaDiary.DiaryType.ATTENDED,
                pageable);

        result.addAll(toLegacyPublicDtos(legacyDiaries, safeLimit - result.size()));
        return result.stream().limit(safeLimit).toList();
    }

    public List<AdminSeatViewDto> getAdminSeatViews(
            String moderationStatus,
            String stadium,
            String aiSuggestedLabel,
            String adminLabel,
            Boolean ticketVerified) {
        List<AdminSeatViewDto> dtos = seatViewPhotoRepository.findForAdmin(
                parseModerationStatus(moderationStatus),
                stadium,
                parseClassificationLabel(aiSuggestedLabel),
                parseClassificationLabel(adminLabel),
                ticketVerified)
                .stream()
                .map(this::toAdminDto)
                .sorted(Comparator
                        .comparing((AdminSeatViewDto dto) -> adminQueuePriority(dto.getModerationStatus()))
                        .thenComparing(AdminSeatViewDto::getDiaryDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(AdminSeatViewDto::getId, Comparator.reverseOrder()))
                .toList();
        return dtos;
    }

    public AdminSeatViewDto getAdminSeatView(Long seatViewId) {
        SeatViewPhoto photo = seatViewPhotoRepository.findDetailById(seatViewId)
                .orElseThrow(() -> new IllegalArgumentException("시야뷰 후보를 찾을 수 없습니다."));
        return toAdminDto(photo);
    }

    @Transactional
    public AdminSeatViewDto reviewSeatView(Long seatViewId, Long adminId, AdminSeatViewActionReq req) {
        SeatViewPhoto photo = seatViewPhotoRepository.findDetailById(seatViewId)
                .orElseThrow(() -> new IllegalArgumentException("시야뷰 후보를 찾을 수 없습니다."));

        ClassificationLabel nextAdminLabel = parseClassificationLabel(req.adminLabel());
        ModerationStatus nextStatus = parseModerationStatus(req.moderationStatus());

        validateReviewTransition(nextAdminLabel, nextStatus);
        photo.review(nextAdminLabel, nextStatus, adminId, req.adminMemo());
        seatViewPhotoRepository.save(photo);

        if (nextStatus == ModerationStatus.APPROVED && nextAdminLabel == ClassificationLabel.SEAT_VIEW) {
            processDiaryRewardIfEligible(photo.getDiary());
        }

        return toAdminDto(photo);
    }

    @Transactional
    public SeatViewRewardDto processDiaryRewardIfEligible(BegaDiary diary) {
        if (diary == null || !diary.isTicketVerified()) {
            return null;
        }

        List<SeatViewPhoto> approved = seatViewPhotoRepository.findApprovedByDiaryId(
                diary.getId(),
                ModerationStatus.APPROVED,
                ClassificationLabel.SEAT_VIEW);

        if (approved.isEmpty()) {
            return null;
        }

        SeatViewPhoto rewardTarget = approved.stream()
                .filter(candidate -> !candidate.isRewardGranted())
                .findFirst()
                .orElse(null);
        if (rewardTarget == null) {
            return null;
        }

        SeatViewRewardDto reward = scoringService.processSeatViewReward(
                diary.getUser().getId(),
                diary.getId(),
                diary.getStadium());
        if (reward != null) {
            rewardTarget.markRewardGranted();
            seatViewPhotoRepository.save(rewardTarget);
        }
        return reward;
    }

    @Transactional
    public void deleteByDiaryId(Long diaryId) {
        seatViewPhotoRepository.deleteByDiary_Id(diaryId);
    }

    private List<SeatViewCandidateDto> toCandidateDtos(List<SeatViewPhoto> candidates) {
        List<String> paths = candidates.stream()
                .map(SeatViewPhoto::getStoragePath)
                .toList();
        Map<String, String> signedUrlMap = signPaths(paths);

        return candidates.stream()
                .map(candidate -> SeatViewCandidateDto.builder()
                        .id(candidate.getId())
                        .storagePath(candidate.getStoragePath())
                        .previewUrl(signedUrlMap.getOrDefault(candidate.getStoragePath(), candidate.getStoragePath()))
                        .sourceType(candidate.getSourceType().name())
                        .aiSuggestedLabel(candidate.getAiSuggestedLabel() != null ? candidate.getAiSuggestedLabel().name() : null)
                        .aiConfidence(candidate.getAiConfidence())
                        .shareEligible(candidate.getSourceType() == SourceType.DIARY_UPLOAD)
                        .build())
                .toList();
    }

    private List<SeatViewPhotoDto> toPublicDtos(List<SeatViewPhoto> approved, int limit) {
        List<String> paths = approved.stream()
                .map(SeatViewPhoto::getStoragePath)
                .toList();
        Map<String, String> signedUrlMap = signPaths(paths);

        return approved.stream()
                .limit(limit)
                .map(photo -> SeatViewPhotoDto.builder()
                        .photoUrl(signedUrlMap.getOrDefault(photo.getStoragePath(), photo.getStoragePath()))
                        .stadium(photo.getDiary().getStadium())
                        .section(photo.getDiary().getSection())
                        .block(photo.getDiary().getBlock())
                        .diaryDate(photo.getDiary().getDiaryDate() != null ? photo.getDiary().getDiaryDate().toString() : null)
                        .build())
                .toList();
    }

    private List<SeatViewPhotoDto> toLegacyPublicDtos(List<BegaDiary> diaries, int limit) {
        List<String> flattenedPaths = diaries.stream()
                .filter(diary -> diary.getPhotoUrls() != null)
                .flatMap(diary -> diary.getPhotoUrls().stream())
                .limit(limit)
                .toList();
        Map<String, String> signedUrlMap = signPaths(flattenedPaths);

        List<SeatViewPhotoDto> result = new ArrayList<>();
        for (BegaDiary diary : diaries) {
            if (diary.getPhotoUrls() == null) {
                continue;
            }
            for (String path : diary.getPhotoUrls()) {
                result.add(SeatViewPhotoDto.builder()
                        .photoUrl(signedUrlMap.getOrDefault(path, path))
                        .stadium(diary.getStadium())
                        .section(diary.getSection())
                        .block(diary.getBlock())
                        .diaryDate(diary.getDiaryDate() != null ? diary.getDiaryDate().toString() : null)
                        .build());
                if (result.size() >= limit) {
                    return result;
                }
            }
        }
        return result;
    }

    private AdminSeatViewDto toAdminDto(SeatViewPhoto photo) {
        String signedUrl = signPaths(List.of(photo.getStoragePath()))
                .getOrDefault(photo.getStoragePath(), photo.getStoragePath());
        BegaDiary diary = photo.getDiary();

        return AdminSeatViewDto.builder()
                .id(photo.getId())
                .diaryId(diary.getId())
                .userId(photo.getUserId())
                .photoUrl(signedUrl)
                .storagePath(photo.getStoragePath())
                .sourceType(photo.getSourceType().name())
                .aiSuggestedLabel(photo.getAiSuggestedLabel() != null ? photo.getAiSuggestedLabel().name() : null)
                .aiConfidence(photo.getAiConfidence())
                .aiReason(photo.getAiReason())
                .userSelected(photo.isUserSelected())
                .moderationStatus(photo.getModerationStatus() != null ? photo.getModerationStatus().name() : null)
                .adminLabel(photo.getAdminLabel() != null ? photo.getAdminLabel().name() : null)
                .adminMemo(photo.getAdminMemo())
                .reviewedBy(photo.getReviewedBy())
                .reviewedAt(photo.getReviewedAt() != null ? photo.getReviewedAt().toString() : null)
                .rewardGranted(photo.isRewardGranted())
                .stadium(diary.getStadium())
                .section(diary.getSection())
                .block(diary.getBlock())
                .seatRow(diary.getSeatRow())
                .seatNumber(diary.getSeatNumber())
                .diaryDate(diary.getDiaryDate() != null ? diary.getDiaryDate().toString() : null)
                .ticketVerified(diary.isTicketVerified())
                .ticketVerifiedAt(diary.getTicketVerifiedAt() != null ? diary.getTicketVerifiedAt().toString() : null)
                .build();
    }

    private Map<String, String> signPaths(List<String> storagePaths) {
        if (storagePaths == null || storagePaths.isEmpty()) {
            return Map.of();
        }

        List<String> signedUrls;
        try {
            signedUrls = imageService.getDiaryImageSignedUrls(storagePaths).block();
        } catch (Exception ex) {
            log.warn("Seat-view signed URL generation failed. cause={}", ex.getMessage());
            signedUrls = null;
        }
        if (signedUrls == null || signedUrls.isEmpty()) {
            return storagePaths.stream()
                    .collect(Collectors.toMap(path -> path, path -> path, (left, right) -> left, LinkedHashMap::new));
        }

        Map<String, String> signedUrlMap = new HashMap<>();
        for (int index = 0; index < storagePaths.size() && index < signedUrls.size(); index++) {
            signedUrlMap.put(storagePaths.get(index), signedUrls.get(index));
        }
        for (String path : storagePaths) {
            signedUrlMap.putIfAbsent(path, path);
        }
        return signedUrlMap;
    }

    private SourceType resolveSourceType(List<SourceType> sourceTypes, int index) {
        if (sourceTypes == null || index >= sourceTypes.size() || sourceTypes.get(index) == null) {
            return SourceType.DIARY_UPLOAD;
        }
        return sourceTypes.get(index);
    }

    private ClassificationLabel parseClassificationLabel(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return ClassificationLabel.valueOf(value.trim().toUpperCase());
    }

    private ModerationStatus parseModerationStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return ModerationStatus.valueOf(value.trim().toUpperCase());
    }

    private void validateReviewTransition(ClassificationLabel adminLabel, ModerationStatus moderationStatus) {
        if (adminLabel == null || moderationStatus == null) {
            throw new IllegalArgumentException("관리자 라벨과 검수 상태는 필수입니다.");
        }
        if (moderationStatus == ModerationStatus.APPROVED && adminLabel != ClassificationLabel.SEAT_VIEW) {
            throw new IllegalArgumentException("승인 상태는 SEAT_VIEW 라벨에만 사용할 수 있습니다.");
        }
        if (moderationStatus == ModerationStatus.REJECTED && adminLabel == ClassificationLabel.SEAT_VIEW) {
            throw new IllegalArgumentException("SEAT_VIEW 라벨은 승인 상태와 함께 사용해야 합니다.");
        }
    }

    private boolean hasSeatMetadata(BegaDiary diary) {
        return StringUtils.hasText(diary.getSection())
                || StringUtils.hasText(diary.getBlock())
                || StringUtils.hasText(diary.getSeatRow())
                || StringUtils.hasText(diary.getSeatNumber());
    }

    private int adminQueuePriority(String moderationStatus) {
        if ("PENDING".equals(moderationStatus)) {
            return 0;
        }
        if (moderationStatus == null) {
            return 1;
        }
        return 2;
    }
}
