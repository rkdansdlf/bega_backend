package com.example.BegaDiary.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.admin.dto.AdminSeatViewActionReq;
import com.example.admin.dto.AdminSeatViewDto;
import com.example.cheerboard.storage.service.ImageService;
import com.example.leaderboard.dto.SeatViewRewardDto;
import com.example.leaderboard.service.ScoringService;
import com.example.media.support.ByteArrayMultipartFile;

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

        BegaDiary diary = requireOwnedDiary(diaryId, userId);

        return createCandidatesInternal(diary, userId, images, storagePaths, sourceTypes);
    }

    @Transactional
    public List<SeatViewCandidateDto> createCandidatesFromStoragePaths(
            Long diaryId,
            Long userId,
            List<String> storagePaths,
            List<SourceType> sourceTypes) {
        if (storagePaths == null || storagePaths.isEmpty()) {
            return List.of();
        }

        BegaDiary diary = requireOwnedDiary(diaryId, userId);
        List<String> normalizedStoragePaths = imageService.normalizeDiaryStoragePathsForWrite(
                storagePaths,
                userId,
                diaryId,
                true);

        List<MultipartFile> images = new ArrayList<>(normalizedStoragePaths.size());
        for (String storagePath : normalizedStoragePaths) {
            byte[] bytes = imageService.downloadDiaryImageBytesForUser(storagePath, userId, diaryId);
            String filename = storagePath != null && storagePath.contains("/")
                    ? storagePath.substring(storagePath.lastIndexOf('/') + 1)
                    : "seat-view.jpg";
            images.add(new ByteArrayMultipartFile(filename, "image/jpeg", bytes));
        }

        return createCandidatesInternal(diary, userId, images, normalizedStoragePaths, sourceTypes);
    }

    @Transactional
    public List<SeatViewCandidateDto> submitSelections(Long diaryId, Long userId, List<Long> candidateIds) {
        BegaDiary diary = requireOwnedDiary(diaryId, userId);
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
        return candidates.stream()
                .map(candidate -> SeatViewCandidateDto.builder()
                        .id(candidate.getId())
                        .storagePath(candidate.getStoragePath())
                        .previewUrl(signPath(candidate))
                        .sourceType(candidate.getSourceType().name())
                        .aiSuggestedLabel(candidate.getAiSuggestedLabel() != null ? candidate.getAiSuggestedLabel().name() : null)
                        .aiConfidence(candidate.getAiConfidence())
                        .shareEligible(candidate.getSourceType() == SourceType.DIARY_UPLOAD)
                        .build())
                .toList();
    }

    private BegaDiary requireOwnedDiary(Long diaryId, Long userId) {
        if (diaryId == null) {
            throw new IllegalArgumentException("Diary ID cannot be null");
        }
        if (userId == null) {
            throw new DiaryNotFoundException(diaryId);
        }
        return diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new DiaryNotFoundException(diaryId));
    }

    private List<SeatViewPhotoDto> toPublicDtos(List<SeatViewPhoto> approved, int limit) {
        return approved.stream()
                .limit(limit)
                .map(photo -> SeatViewPhotoDto.builder()
                        .photoUrl(signPath(photo))
                        .stadium(photo.getDiary().getStadium())
                        .section(photo.getDiary().getSection())
                        .block(photo.getDiary().getBlock())
                        .diaryDate(photo.getDiary().getDiaryDate() != null ? photo.getDiary().getDiaryDate().toString() : null)
                        .build())
                .toList();
    }

    private List<SeatViewPhotoDto> toLegacyPublicDtos(List<BegaDiary> diaries, int limit) {
        List<SeatViewPhotoDto> result = new ArrayList<>();
        for (BegaDiary diary : diaries) {
            if (diary.getPhotoUrls() == null) {
                continue;
            }
            Long ownerUserId = diary.getUser() != null ? diary.getUser().getId() : null;
            for (String path : diary.getPhotoUrls()) {
                String signedUrl = signPath(path, ownerUserId, diary.getId());
                if (signedUrl == null) {
                    continue;
                }
                result.add(SeatViewPhotoDto.builder()
                        .photoUrl(signedUrl)
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
        String signedUrl = signPath(photo);
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

    private String signPath(SeatViewPhoto photo) {
        if (photo == null) {
            return null;
        }
        BegaDiary diary = photo.getDiary();
        Long diaryId = diary != null ? diary.getId() : null;
        return signPath(photo.getStoragePath(), photo.getUserId(), diaryId);
    }

    private String signPath(String storagePath, Long userId, Long diaryId) {
        if (!StringUtils.hasText(storagePath)) {
            return null;
        }
        try {
            List<String> signedUrls = imageService
                    .getDiaryImageSignedUrls(List.of(storagePath), userId, diaryId)
                    .block();
            if (signedUrls == null || signedUrls.isEmpty()) {
                return null;
            }
            return signedUrls.get(0);
        } catch (Exception ex) {
            log.warn("Seat-view signed URL generation skipped. path={} cause={}", storagePath, ex.getMessage());
            return null;
        }
    }

    private SourceType resolveSourceType(List<SourceType> sourceTypes, int index) {
        if (sourceTypes == null || index >= sourceTypes.size() || sourceTypes.get(index) == null) {
            return SourceType.DIARY_UPLOAD;
        }
        return sourceTypes.get(index);
    }

    private List<SeatViewCandidateDto> createCandidatesInternal(
            BegaDiary diary,
            Long userId,
            List<MultipartFile> images,
            List<String> storagePaths,
            List<SourceType> sourceTypes) {
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
                            diary.getId(), storagePath, ex.getMessage());
                }
            }

            savedCandidates.add(seatViewPhotoRepository.save(candidate));
        }

        return toCandidateDtos(savedCandidates);
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
