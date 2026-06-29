package com.example.mate.service;

// Force IDE re-index

import com.example.mate.dto.PartyReviewDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PartyReview;
import com.example.common.exception.AuthenticationRequiredException;
import com.example.mate.exception.DuplicateReviewException;
import com.example.mate.exception.InvalidReviewException;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.security.Principal;
import java.util.stream.Collectors;
import com.example.auth.service.UserService;

@Service
@RequiredArgsConstructor
public class PartyReviewService {

    private final PartyReviewRepository partyReviewRepository;
    private final PartyRepository partyRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private final UserService userService;

    /**
     * 리뷰 작성
     */
    @Transactional
    public PartyReviewDTO.Response createReview(PartyReviewDTO.Request request, Principal principal) {
        return createReview(request, resolveUserId(principal));
    }

    @Transactional
    public PartyReviewDTO.Response createReview(PartyReviewDTO.Request request, Long reviewerId) {
        requireUserId(reviewerId);
        Party party = requireAccessibleParty(request.getPartyId(), reviewerId);
        Long revieweeId = resolveRevieweeId(request);

        // 1. 파티가 COMPLETED 상태인지 확인
        if (party.getStatus() != Party.PartyStatus.COMPLETED) {
            throw new InvalidReviewException("완료된 파티에 대해서만 리뷰를 작성할 수 있습니다.");
        }

        // 2. 리뷰 대상자가 파티 참여자인지 확인
        boolean revieweeIsHost = party.getHostId().equals(revieweeId);
        boolean revieweeIsApprovedApplicant = partyApplicationRepository
                .findByPartyIdAndApplicantId(request.getPartyId(), revieweeId)
                .map(PartyApplication::getIsApproved)
                .orElse(false);

        if (!revieweeIsHost && !revieweeIsApprovedApplicant) {
            throw new InvalidReviewException("파티 참여자에 대해서만 리뷰를 작성할 수 있습니다.");
        }

        // 3. 본인에게 리뷰 작성 방지
        if (reviewerId.equals(revieweeId)) {
            throw new InvalidReviewException("본인에게는 리뷰를 작성할 수 없습니다.");
        }

        // 4. 중복 리뷰 방지
        if (partyReviewRepository.existsByPartyIdAndReviewerIdAndRevieweeId(
                request.getPartyId(), reviewerId, revieweeId)) {
            throw new DuplicateReviewException(request.getPartyId(), reviewerId, revieweeId);
        }

        // 5. 평점 유효성 검사 (1-5)
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new InvalidReviewException("평점은 1~5 사이의 값이어야 합니다.");
        }

        // 6. 코멘트 길이 검사 (최대 200자)
        if (request.getComment() != null && request.getComment().length() > 200) {
            throw new InvalidReviewException("코멘트는 최대 200자까지 입력할 수 있습니다.");
        }

        // 7. 리뷰 생성
        PartyReview review = PartyReview.builder()
                .partyId(request.getPartyId())
                .reviewerId(reviewerId)
                .revieweeId(revieweeId)
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        PartyReview savedReview = partyReviewRepository.save(review);

        return toResponse(savedReview);
    }

    /**
     * 파티별 리뷰 조회
     */
    @Transactional(readOnly = true)
    public List<PartyReviewDTO.Response> getReviewsByParty(Long partyId, Long viewerId) {
        requireAccessibleParty(partyId, viewerId);
        return partyReviewRepository.findByPartyId(partyId).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * 호스트(핸들)가 받은 전체 후기 조회 (공개, 최신순).
     */
    @Transactional(readOnly = true)
    public List<PartyReviewDTO.Response> getReviewsByHostHandle(String handle) {
        Long hostId = userService.getUserIdByHandle(handle);
        return partyReviewRepository.findByRevieweeId(hostId).stream()
                .sorted(java.util.Comparator.comparing(
                        PartyReview::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private Long resolveRevieweeId(PartyReviewDTO.Request request) {
        if (request.getRevieweeHandle() != null && !request.getRevieweeHandle().isBlank()) {
            return userService.getUserIdByHandle(request.getRevieweeHandle());
        }
        throw new InvalidReviewException("리뷰 대상자는 필수입니다.");
    }

    private PartyReviewDTO.Response toResponse(PartyReview review) {
        PartyReviewDTO.Response response = PartyReviewDTO.Response.from(review);
        response.setReviewerHandle(resolveUserHandle(review.getReviewerId()));
        response.setRevieweeHandle(resolveUserHandle(review.getRevieweeId()));
        return response;
    }

    private String resolveUserHandle(Long userId) {
        if (userId == null) {
            return null;
        }
        return userService.findUserById(userId).getHandle();
    }

    private Party requireAccessibleParty(Long partyId, Long userId) {
        requireUserId(userId);
        if (partyId == null) {
            throw new PartyNotFoundException(0L);
        }
        return partyRepository.findAccessibleByIdAndParticipantId(partyId, userId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private Long resolveUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new AuthenticationRequiredException("인증 정보가 없습니다.");
        }
        String principalName = principal.getName();
        try {
            return Long.valueOf(principalName);
        } catch (NumberFormatException ignored) {
            return userService.getUserIdByEmail(principalName);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증 정보가 없습니다.");
        }
    }
}
