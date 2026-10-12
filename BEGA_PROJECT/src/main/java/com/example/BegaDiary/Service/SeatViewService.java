package com.example.BegaDiary.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.Page;
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
import com.example.BegaDiary.Entity.SeatViewSubmissionDto;
import com.example.BegaDiary.Entity.SeatViewSubmissionRequest;
import com.example.BegaDiary.Exception.DiaryNotFoundException;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.BegaDiary.Repository.SeatViewPhotoRepository;
import com.example.common.exception.NotFoundBusinessException;
import com.example.admin.dto.AdminSeatViewActionReq;
import com.example.admin.dto.AdminSeatViewDto;
import com.example.cheerboard.storage.service.ImageService;
import com.example.leaderboard.dto.SeatViewRewardDto;
import com.example.leaderboard.service.ScoringService;
import com.example.media.support.ByteArrayMultipartFile;
import com.example.media.service.MediaLinkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SeatViewService {

    private static final int ADMIN_MEMO_MAX_LENGTH = 1000;
    private static final int AUTHENTICATED_PREVIEW_URL_TTL_SECONDS = 300;
    private static final int ADMIN_SEAT_VIEW_PAGE_SIZE_MAX = 100;
    private static final Set<String> ALLOWED_TAGS = Set.of(
            "탁 트임", "응원석 가까움", "그늘", "비/햇빛 가림", "통로 가까움", "화장실 가까움",
            "매점 가까움", "전광판 잘 보임", "포수 뒤", "외야 뷰", "가성비", "아이와 보기 좋음");
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SeatViewPhotoRepository seatViewPhotoRepository;
    private final BegaDiaryRepository diaryRepository;
    private final ImageService imageService;
    private final SeatViewClassificationService seatViewClassificationService;
    private final ScoringService scoringService;
    private final MediaLinkService mediaLinkService;

    @Transactional
    public SeatViewSubmissionDto createDirectSubmission(Long userId, SeatViewSubmissionRequest request) {
        if (userId == null || request == null) {
            throw new IllegalArgumentException("로그인 후 시야뷰를 등록해주세요.");
        }
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) {
            throw new IllegalArgumentException("별점은 1에서 5 사이여야 합니다.");
        }
        String stadium = normalizeRequired(request.stadium(), "구장을 입력해주세요.");
        String storagePath = normalizeRequired(request.storagePath(), "업로드한 사진을 확인할 수 없습니다.");
        List<String> tags = normalizeTags(request.tags());

        SeatViewPhoto photo = SeatViewPhoto.builder()
                .userId(userId)
                .storagePath(storagePath)
                .sourceType(SourceType.SEATMAP_UPLOAD)
                .stadium(stadium)
                .section(normalizeOptional(request.section()))
                .block(normalizeOptional(request.block()))
                .seatRow(normalizeOptional(request.seatRow()))
                .seatNumber(normalizeOptional(request.seatNumber()))
                .rating(request.rating())
                .commentText(normalizeOptional(request.comment()))
                .tagsJson(serializeTags(tags))
                .userSelected(false)
                .rewardGranted(false)
                .build();
        photo.updateUserSelection(true);
        SeatViewPhoto saved = seatViewPhotoRepository.saveAndFlush(photo);
        mediaLinkService.linkSeatViewAsset(userId, storagePath, saved.getId());
        return toSubmissionDto(saved, tags);
    }

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

        List<SeatViewPhoto> approved = seatViewPhotoRepository.findApprovedPublicSeatViews(
                stadium,
                section,
                ModerationStatus.APPROVED,
                ClassificationLabel.SEAT_VIEW,
                pageable);

        return toPublicDtos(approved, safeLimit);
    }

    public Page<AdminSeatViewDto> getAdminSeatViews(
            String moderationStatus,
            String stadium,
            String aiSuggestedLabel,
            String adminLabel,
            Boolean ticketVerified,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > ADMIN_SEAT_VIEW_PAGE_SIZE_MAX) {
            throw new IllegalArgumentException("시야뷰 페이지는 0 이상, 크기는 1에서 100 사이여야 합니다.");
        }
        Pageable pageable = PageRequest.of(page, size);
        return seatViewPhotoRepository.findForAdmin(
                parseModerationStatus(moderationStatus),
                stadium,
                parseClassificationLabel(aiSuggestedLabel),
                parseClassificationLabel(adminLabel),
                ticketVerified,
                ModerationStatus.PENDING,
                pageable)
                .map(this::toAdminDto);
    }

    public AdminSeatViewDto getAdminSeatView(Long seatViewId) {
        SeatViewPhoto photo = seatViewPhotoRepository.findDetailById(seatViewId)
                .orElseThrow(() -> new IllegalArgumentException("시야뷰 후보를 찾을 수 없습니다."));
        return toAdminDto(photo);
    }

    public String getOwnerPreviewUrl(Long userId, Long seatViewId) {
        if (userId == null) {
            throw new IllegalArgumentException("로그인 후 시야뷰를 확인해주세요.");
        }
        SeatViewPhoto photo = seatViewPhotoRepository.findDetailById(seatViewId)
                .orElseThrow(() -> new NotFoundBusinessException("SEAT_VIEW_NOT_FOUND", "시야뷰를 찾을 수 없습니다."));
        if (!userId.equals(photo.getUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("시야뷰 사진을 확인할 권한이 없습니다.");
        }
        String previewUrl = signPreviewPath(photo);
        if (!StringUtils.hasText(previewUrl)) {
            throw new NotFoundBusinessException("SEAT_VIEW_IMAGE_NOT_FOUND", "시야뷰 사진을 확인할 수 없습니다.");
        }
        return previewUrl;
    }

    @Transactional
    public AdminSeatViewDto reviewSeatView(Long seatViewId, Long adminId, AdminSeatViewActionReq req) {
        if (req != null && req.adminMemo() != null && req.adminMemo().length() > ADMIN_MEMO_MAX_LENGTH) {
            throw new IllegalArgumentException("시야뷰 검수 메모는 1000자 이하여야 합니다.");
        }

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
                        .previewUrl(signPreviewPath(candidate))
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
                .filter(photo -> photo.isUserSelected()
                        && (photo.getSourceType() == SourceType.DIARY_UPLOAD
                                || photo.getSourceType() == SourceType.SEATMAP_UPLOAD)
                        && photo.getModerationStatus() == ModerationStatus.APPROVED
                        && photo.getAdminLabel() == ClassificationLabel.SEAT_VIEW)
                .limit(limit)
                .map(photo -> SeatViewPhotoDto.builder()
                        .photoUrl(signPath(photo))
                        .stadium(resolveValue(photo.getStadium(), photo.getDiary() != null ? photo.getDiary().getStadium() : null))
                        .section(resolveValue(photo.getSection(), photo.getDiary() != null ? photo.getDiary().getSection() : null))
                        .block(resolveValue(photo.getBlock(), photo.getDiary() != null ? photo.getDiary().getBlock() : null))
                        .diaryDate(photo.getDiary() != null && photo.getDiary().getDiaryDate() != null
                                ? photo.getDiary().getDiaryDate().toString()
                                : null)
                        .rating(photo.getRating())
                        .comment(photo.getCommentText())
                        .tags(parseTags(photo.getTagsJson()))
                        .build())
                .toList();
    }

    private AdminSeatViewDto toAdminDto(SeatViewPhoto photo) {
        String signedUrl = signPreviewPath(photo);
        BegaDiary diary = photo.getDiary();

        return AdminSeatViewDto.builder()
                .id(photo.getId())
                .diaryId(diary != null ? diary.getId() : null)
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
                .stadium(resolveValue(photo.getStadium(), diary != null ? diary.getStadium() : null))
                .section(resolveValue(photo.getSection(), diary != null ? diary.getSection() : null))
                .block(resolveValue(photo.getBlock(), diary != null ? diary.getBlock() : null))
                .seatRow(resolveValue(photo.getSeatRow(), diary != null ? diary.getSeatRow() : null))
                .seatNumber(resolveValue(photo.getSeatNumber(), diary != null ? diary.getSeatNumber() : null))
                .diaryDate(diary != null && diary.getDiaryDate() != null ? diary.getDiaryDate().toString() : null)
                .ticketVerified(diary != null && diary.isTicketVerified())
                .ticketVerifiedAt(diary != null && diary.getTicketVerifiedAt() != null
                        ? diary.getTicketVerifiedAt().toString()
                        : null)
                .rating(photo.getRating())
                .comment(photo.getCommentText())
                .tags(parseTags(photo.getTagsJson()))
                .build();
    }

    private String signPath(SeatViewPhoto photo) {
        if (photo == null) {
            return null;
        }
        BegaDiary diary = photo.getDiary();
        Long diaryId = diary != null ? diary.getId() : null;
        if (diary == null) {
            try {
                List<String> signedUrls = imageService
                        .getSeatViewImageSignedUrls(List.of(photo.getStoragePath()), photo.getUserId(), photo.getId())
                        .block();
                return signedUrls == null || signedUrls.isEmpty() ? null : signedUrls.get(0);
            } catch (Exception ex) {
                log.warn("Seat-view signed URL generation skipped. path={} cause={}", photo.getStoragePath(), ex.getMessage());
                return null;
            }
        }
        return signPath(photo.getStoragePath(), photo.getUserId(), diaryId);
    }

    private String signPreviewPath(SeatViewPhoto photo) {
        if (photo == null || !StringUtils.hasText(photo.getStoragePath())) {
            return null;
        }
        BegaDiary diary = photo.getDiary();
        try {
            List<String> signedUrls = diary == null
                    ? imageService.getSeatViewImageSignedUrls(
                            List.of(photo.getStoragePath()), photo.getUserId(), photo.getId(),
                            AUTHENTICATED_PREVIEW_URL_TTL_SECONDS).block()
                    : imageService.getDiaryImageSignedUrls(
                            List.of(photo.getStoragePath()), photo.getUserId(), diary.getId(),
                            AUTHENTICATED_PREVIEW_URL_TTL_SECONDS).block();
            return signedUrls == null || signedUrls.isEmpty() ? null : signedUrls.get(0);
        } catch (Exception ex) {
            log.warn("Seat-view preview URL generation skipped. seatViewId={} cause={}",
                    photo.getId(), ex.getMessage());
            return null;
        }
    }

    private SeatViewSubmissionDto toSubmissionDto(SeatViewPhoto photo, List<String> tags) {
        return new SeatViewSubmissionDto(
                photo.getId(),
                photo.getStoragePath(),
                null,
                photo.getSourceType().name(),
                photo.getModerationStatus() != null ? photo.getModerationStatus().name() : null,
                photo.getAiSuggestedLabel() != null ? photo.getAiSuggestedLabel().name() : null,
                photo.getAiConfidence(),
                photo.getStadium(),
                photo.getSection(),
                photo.getBlock(),
                photo.getSeatRow(),
                photo.getSeatNumber(),
                photo.getRating(),
                photo.getCommentText(),
                tags);
    }

    private List<String> normalizeTags(List<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : requestedTags) {
            if (tag == null || !ALLOWED_TAGS.contains(tag.strip())) {
                throw new IllegalArgumentException("지원하지 않는 시야뷰 태그입니다.");
            }
            normalized.add(tag.strip());
        }
        if (normalized.size() > 12) {
            throw new IllegalArgumentException("시야뷰 태그는 12개까지 등록할 수 있습니다.");
        }
        return List.copyOf(normalized);
    }

    private String serializeTags(List<String> tags) {
        try {
            return OBJECT_MAPPER.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("시야뷰 태그를 저장할 수 없습니다.", ex);
        }
    }

    private List<String> parseTags(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(tagsJson, new TypeReference<>() { });
        } catch (JsonProcessingException ex) {
            log.warn("Invalid seat-view tags JSON ignored: {}", ex.getMessage());
            return List.of();
        }
    }

    private String normalizeRequired(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private String resolveValue(String directValue, String diaryValue) {
        return directValue != null ? directValue : diaryValue;
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
            if (sourceType == SourceType.SEATMAP_UPLOAD) {
                throw new IllegalArgumentException("SEATMAP_UPLOAD은 독립 시야뷰 등록 경로에서만 사용할 수 있습니다.");
            }

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

}
