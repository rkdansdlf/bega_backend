package com.example.cheerboard.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.BegaDiary.Entity.BegaDiary;
import com.example.BegaDiary.Entity.BegaDiary.DiaryType;
import com.example.BegaDiary.Repository.BegaDiaryRepository;
import com.example.auth.entity.UserEntity;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.domain.CheerPost.ShareMode;
import com.example.cheerboard.domain.PostType;
import com.example.cheerboard.dto.CheckinLinkedContentRes;
import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.LinkedContentKind;
import com.example.cheerboard.dto.LinkedContentRes;
import com.example.cheerboard.dto.LinkedContentUnavailableReason;
import com.example.cheerboard.dto.LinkedPostLookupRes;
import com.example.cheerboard.dto.RecruitmentLinkedContentRes;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.common.exception.BadRequestBusinessException;
import com.example.common.exception.ConflictBusinessException;
import com.example.common.exception.ForbiddenBusinessException;
import com.example.common.exception.NotFoundBusinessException;
import com.example.kbo.entity.GameEntity;
import com.example.kbo.validation.ManualBaseballDataMissingItem;
import com.example.kbo.validation.ManualBaseballDataRequest;
import com.example.kbo.validation.ManualBaseballDataRequiredException;
import com.example.mate.entity.Party;
import com.example.mate.entity.Party.PartyStatus;
import com.example.mate.repository.PartyRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CheerLinkedPostService {

    private static final String INVALID_REQUEST_CODE = "INVALID_LINKED_POST_REQUEST";
    private static final String OPERATOR_MESSAGE =
            "내부 game/bega_diary/parties 데이터 또는 운영자 제공 수동 데이터가 필요합니다.";

    private final BegaDiaryRepository diaryRepository;
    private final PartyRepository partyRepository;
    private final CheerPostRepo postRepository;

    public record ValidatedTarget(PostType postType, Long diaryId, Long partyId) {
    }

    public ValidatedTarget validateCreate(PostType effectiveType, CreatePostReq req, UserEntity actor) {
        if (effectiveType == null || req == null || actor == null || actor.getId() == null) {
            throw invalidRequest();
        }
        return switch (effectiveType) {
            case CHECKIN -> validateCheckin(req, actor);
            case RECRUITMENT -> validateRecruitment(req, actor);
            case NORMAL, NOTICE -> validateUnlinked(effectiveType, req);
        };
    }

    public LinkedPostLookupRes lookup(Long diaryId, Long partyId, UserEntity actor) {
        if ((diaryId == null) == (partyId == null) || actor == null || actor.getId() == null) {
            throw invalidRequest();
        }
        if (diaryId != null) {
            BegaDiary diary = requireShareableDiary(diaryId, actor);
            return new LinkedPostLookupRes(
                    postRepository.findFirstByDiaryIdAndDeletedFalse(diaryId)
                            .map(CheerPost::getId)
                            .orElse(null),
                    LinkedContentRes.availableCheckin(toCheckinPreview(diary)));
        }

        Party party = requireRecruitingParty(partyId, actor);
        return new LinkedPostLookupRes(
                postRepository.findFirstByPartyIdAndDeletedFalse(partyId)
                        .map(CheerPost::getId)
                        .orElse(null),
                LinkedContentRes.availableRecruitment(toRecruitmentPreview(party)));
    }

    public Optional<CheerPost> findActivePost(ValidatedTarget target) {
        if (target == null || target.postType() == null) {
            return Optional.empty();
        }
        return switch (target.postType()) {
            case CHECKIN -> target.diaryId() == null
                    ? Optional.empty()
                    : postRepository.findFirstByDiaryIdAndDeletedFalse(target.diaryId());
            case RECRUITMENT -> target.partyId() == null
                    ? Optional.empty()
                    : postRepository.findFirstByPartyIdAndDeletedFalse(target.partyId());
            case NORMAL, NOTICE -> Optional.empty();
        };
    }

    public LinkedContentRes resolveOne(CheerPost post) {
        if (post == null || post.getPostType() == null || !isLinked(post.getPostType())) {
            return null;
        }
        return resolveForPosts(List.of(post)).get(post.getId());
    }

    public Map<Long, LinkedContentRes> resolveForPosts(Collection<CheerPost> suppliedPosts) {
        List<CheerPost> linkedPosts = collectLinkedPosts(suppliedPosts);
        if (linkedPosts.isEmpty()) {
            return Map.of();
        }

        Set<Long> diaryIds = linkedPosts.stream()
                .filter(post -> post.getPostType() == PostType.CHECKIN)
                .map(CheerPost::getDiaryId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> partyIds = linkedPosts.stream()
                .filter(post -> post.getPostType() == PostType.RECRUITMENT)
                .map(CheerPost::getPartyId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<Long, BegaDiary> diariesById = diaryIds.isEmpty()
                ? Map.of()
                : diaryRepository.findAllByIdInWithOwnerAndGame(diaryIds).stream()
                        .collect(Collectors.toMap(BegaDiary::getId, Function.identity()));
        Map<Long, Party> partiesById = partyIds.isEmpty()
                ? Map.of()
                : partyRepository.findByIdIn(partyIds).stream()
                        .collect(Collectors.toMap(Party::getId, Function.identity()));

        Map<Long, LinkedContentRes> resolved = new LinkedHashMap<>();
        for (CheerPost post : linkedPosts) {
            LinkedContentRes content = post.getPostType() == PostType.CHECKIN
                    ? resolveCheckin(post.getDiaryId(), diariesById)
                    : resolveRecruitment(post.getPartyId(), partiesById);
            resolved.put(post.getId(), content);
        }
        return Collections.unmodifiableMap(resolved);
    }

    private ValidatedTarget validateCheckin(CreatePostReq req, UserEntity actor) {
        validateLinkedRequest(req, PostType.CHECKIN);
        requireShareableDiary(req.diaryId(), actor);
        return new ValidatedTarget(PostType.CHECKIN, req.diaryId(), null);
    }

    private ValidatedTarget validateRecruitment(CreatePostReq req, UserEntity actor) {
        validateLinkedRequest(req, PostType.RECRUITMENT);
        requireRecruitingParty(req.partyId(), actor);
        return new ValidatedTarget(PostType.RECRUITMENT, null, req.partyId());
    }

    private ValidatedTarget validateUnlinked(PostType effectiveType, CreatePostReq req) {
        if (req.diaryId() != null || req.partyId() != null) {
            throw invalidRequest();
        }
        return new ValidatedTarget(effectiveType, null, null);
    }

    private void validateLinkedRequest(CreatePostReq req, PostType type) {
        boolean sourceIdsValid = type == PostType.CHECKIN
                ? req.diaryId() != null && req.partyId() == null
                : req.diaryId() == null && req.partyId() != null;
        if (!sourceIdsValid
                || (req.shareMode() != null && req.shareMode() != ShareMode.INTERNAL_REPOST)
                || hasExternalSourceMetadata(req)) {
            throw invalidRequest();
        }
    }

    private static boolean hasExternalSourceMetadata(CreatePostReq req) {
        return req.sourceUrl() != null
                || req.sourceTitle() != null
                || req.sourceAuthor() != null
                || req.sourceLicense() != null
                || req.sourceLicenseUrl() != null
                || req.sourceChangedNote() != null
                || req.sourceSnapshotType() != null;
    }

    private BegaDiary requireShareableDiary(Long diaryId, UserEntity actor) {
        BegaDiary diary = diaryRepository.findByIdWithOwnerGameAndPhotos(diaryId)
                .filter(found -> found.getUser() != null)
                .filter(found -> actor.getId().equals(found.getUser().getId()))
                .orElseThrow(() -> new NotFoundBusinessException(
                        "DIARY_NOT_FOUND", "다이어리를 찾을 수 없습니다."));
        if (diary.getType() != DiaryType.ATTENDED || !diary.isTicketVerified()) {
            throw new ConflictBusinessException(
                    "CHECKIN_NOT_SHAREABLE", "직관 및 티켓 인증이 완료된 다이어리만 공유할 수 있습니다.");
        }
        throwIfMissingDiaryData(diaryId, diary);
        return diary;
    }

    private Party requireRecruitingParty(Long partyId, UserEntity actor) {
        Party party = partyRepository.findById(partyId)
                .orElseThrow(() -> new NotFoundBusinessException(
                        "PARTY_NOT_FOUND", "파티를 찾을 수 없습니다."));
        if (!actor.getId().equals(party.getHostId())) {
            throw new ForbiddenBusinessException(
                    "PARTY_HOST_REQUIRED", "파티 호스트만 모집 글을 공유할 수 있습니다.");
        }
        if (party.getStatus() != PartyStatus.PENDING) {
            throw new ConflictBusinessException(
                    "PARTY_NOT_RECRUITING", "현재 모집 중인 파티만 공유할 수 있습니다.");
        }
        throwIfMissingPartyData(partyId, party);
        return party;
    }

    private LinkedContentRes resolveCheckin(Long diaryId, Map<Long, BegaDiary> diariesById) {
        if (diaryId == null) {
            return unavailable(LinkedContentKind.CHECKIN, LinkedContentUnavailableReason.SOURCE_MISSING);
        }
        BegaDiary diary = diariesById.get(diaryId);
        if (diary == null) {
            return unavailable(LinkedContentKind.CHECKIN, LinkedContentUnavailableReason.SOURCE_MISSING);
        }
        if (diary.getType() != DiaryType.ATTENDED || !diary.isTicketVerified()) {
            return unavailable(LinkedContentKind.CHECKIN, LinkedContentUnavailableReason.SOURCE_INELIGIBLE);
        }
        if (!missingDiaryData(diary).isEmpty()) {
            return unavailable(
                    LinkedContentKind.CHECKIN,
                    LinkedContentUnavailableReason.MANUAL_BASEBALL_DATA_REQUIRED);
        }
        return LinkedContentRes.availableCheckin(toCheckinPreview(diary));
    }

    private LinkedContentRes resolveRecruitment(Long partyId, Map<Long, Party> partiesById) {
        if (partyId == null) {
            return unavailable(LinkedContentKind.RECRUITMENT, LinkedContentUnavailableReason.SOURCE_MISSING);
        }
        Party party = partiesById.get(partyId);
        if (party == null) {
            return unavailable(LinkedContentKind.RECRUITMENT, LinkedContentUnavailableReason.SOURCE_MISSING);
        }
        if (party.getStatus() == PartyStatus.FAILED) {
            return unavailable(LinkedContentKind.RECRUITMENT, LinkedContentUnavailableReason.SOURCE_INELIGIBLE);
        }
        if (!missingPartyData(party).isEmpty()) {
            return unavailable(
                    LinkedContentKind.RECRUITMENT,
                    LinkedContentUnavailableReason.MANUAL_BASEBALL_DATA_REQUIRED);
        }
        return LinkedContentRes.availableRecruitment(toRecruitmentPreview(party));
    }

    private static CheckinLinkedContentRes toCheckinPreview(BegaDiary diary) {
        GameEntity game = diary.getGame();
        return new CheckinLinkedContentRes(
                game.getGameDate(),
                game.getHomeTeam(),
                game.getAwayTeam(),
                diary.getTeam(),
                diary.getStadium(),
                true);
    }

    private static RecruitmentLinkedContentRes toRecruitmentPreview(Party party) {
        return new RecruitmentLinkedContentRes(
                party.getId(),
                party.getGameDate(),
                party.getGameTime(),
                party.getHomeTeam(),
                party.getAwayTeam(),
                party.getStadium(),
                party.getSection(),
                party.getCurrentParticipants(),
                party.getMaxParticipants(),
                party.getStatus().name(),
                party.getStatus() == PartyStatus.PENDING,
                party.getDescription(),
                party.getPrice(),
                party.getTicketPrice(),
                party.getReservationDepositAmount());
    }

    private static List<CheerPost> collectLinkedPosts(Collection<CheerPost> suppliedPosts) {
        if (suppliedPosts == null || suppliedPosts.isEmpty()) {
            return List.of();
        }
        ArrayDeque<CheerPost> pending = new ArrayDeque<>();
        suppliedPosts.stream().filter(java.util.Objects::nonNull).forEach(pending::addLast);
        Set<CheerPost> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        List<CheerPost> linked = new ArrayList<>();
        while (!pending.isEmpty()) {
            CheerPost post = pending.removeFirst();
            if (!visited.add(post)) {
                continue;
            }
            if (post.getRepostOf() != null) {
                pending.addLast(post.getRepostOf());
            }
            if (post.getPostType() != null && isLinked(post.getPostType())) {
                linked.add(post);
            }
        }
        return linked;
    }

    private static boolean isLinked(PostType type) {
        return type == PostType.CHECKIN || type == PostType.RECRUITMENT;
    }

    private static LinkedContentRes unavailable(
            LinkedContentKind kind,
            LinkedContentUnavailableReason reason) {
        return LinkedContentRes.unavailable(kind, reason);
    }

    private static void throwIfMissingDiaryData(Long diaryId, BegaDiary diary) {
        List<ManualBaseballDataMissingItem> missingItems = missingDiaryData(diary);
        if (!missingItems.isEmpty()) {
            throw manualDataRequired("CHECKIN", diaryId, missingItems);
        }
    }

    private static void throwIfMissingPartyData(Long partyId, Party party) {
        List<ManualBaseballDataMissingItem> missingItems = missingPartyData(party);
        if (!missingItems.isEmpty()) {
            throw manualDataRequired("RECRUITMENT", partyId, missingItems);
        }
    }

    private static List<ManualBaseballDataMissingItem> missingDiaryData(BegaDiary diary) {
        List<ManualBaseballDataMissingItem> missingItems = new ArrayList<>();
        GameEntity game = diary.getGame();
        if (game == null) {
            missingItems.add(missing("game", "경기", "내부 game 연결이 없습니다.", "game.id"));
            return missingItems;
        }
        addIfMissing(missingItems, "gameDate", "경기일", game.getGameDate(), "YYYY-MM-DD");
        addIfBlank(missingItems, "homeTeam", "홈 팀", game.getHomeTeam(), "KBO team id/name");
        addIfBlank(missingItems, "awayTeam", "원정 팀", game.getAwayTeam(), "KBO team id/name");
        addIfBlank(missingItems, "cheeringTeam", "응원 팀", diary.getTeam(), "KBO team id/name");
        addIfBlank(missingItems, "stadium", "구장", diary.getStadium(), "stadium name");
        return missingItems;
    }

    private static List<ManualBaseballDataMissingItem> missingPartyData(Party party) {
        List<ManualBaseballDataMissingItem> missingItems = new ArrayList<>();
        addIfMissing(missingItems, "gameDate", "경기일", party.getGameDate(), "YYYY-MM-DD");
        addIfMissing(missingItems, "gameTime", "경기 시간", party.getGameTime(), "HH:mm:ss");
        addIfBlank(missingItems, "homeTeam", "홈 팀", party.getHomeTeam(), "KBO team id/name");
        addIfBlank(missingItems, "awayTeam", "원정 팀", party.getAwayTeam(), "KBO team id/name");
        addIfBlank(missingItems, "stadium", "구장", party.getStadium(), "stadium name");
        return missingItems;
    }

    private static void addIfMissing(
            List<ManualBaseballDataMissingItem> items,
            String key,
            String label,
            Object value,
            String expectedFormat) {
        if (value == null) {
            items.add(missing(key, label, "필수 내부 데이터가 없습니다.", expectedFormat));
        }
    }

    private static void addIfBlank(
            List<ManualBaseballDataMissingItem> items,
            String key,
            String label,
            String value,
            String expectedFormat) {
        if (value == null || value.isBlank()) {
            items.add(missing(key, label, "필수 내부 데이터가 없습니다.", expectedFormat));
        }
    }

    private static ManualBaseballDataMissingItem missing(
            String key,
            String label,
            String reason,
            String expectedFormat) {
        return new ManualBaseballDataMissingItem(key, label, reason, expectedFormat);
    }

    private static ManualBaseballDataRequiredException manualDataRequired(
            String sourceType,
            Long sourceId,
            List<ManualBaseballDataMissingItem> missingItems) {
        return new ManualBaseballDataRequiredException(new ManualBaseballDataRequest(
                "cheer-linked:" + sourceType + ":" + sourceId,
                List.copyOf(missingItems),
                OPERATOR_MESSAGE,
                true));
    }

    private static BadRequestBusinessException invalidRequest() {
        return new BadRequestBusinessException(
                INVALID_REQUEST_CODE,
                "게시글 유형과 연결 원본을 확인해 주세요.");
    }
}
