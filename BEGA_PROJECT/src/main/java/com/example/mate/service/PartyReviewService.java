package com.example.mate.service;

// Force IDE re-index

import com.example.mate.dto.PartyReviewDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.entity.PartyReview;
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
import com.example.mate.exception.UnauthorizedAccessException;

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
        if (principal == null) {
            throw new UnauthorizedAccessException("인증 정보가 없습니다.");
        }
        Long reviewerId = userService.getUserIdByEmail(principal.getName());

        // 1. 파티 존재 여부 확인
        Party party = partyRepository.findById(request.getPartyId())
                .orElseThrow(() -> new PartyNotFoundException(request.getPartyId()));

        // 2. 파티가 COMPLETED 상태인지 확인
        if (party.getStatus() != Party.PartyStatus.COMPLETED) {
            throw new InvalidReviewException("완료된 파티에 대해서만 리뷰를 작성할 수 있습니다.");
        }

        // 3. 리뷰 작성자가 파티 참여자인지 확인 (호스트 또는 승인된 신청자)
        boolean isHost = party.getHostId().equals(reviewerId);
        boolean isApprovedApplicant = partyApplicationRepository
                .findByPartyIdAndApplicantId(request.getPartyId(), reviewerId)
                .map(PartyApplication::getIsApproved)
                .orElse(false);

        if (!isHost && !isApprovedApplicant) {
            throw new InvalidReviewException("파티 참여자만 리뷰를 작성할 수 있습니다.");
        }

        // 4. 리뷰 대상자가 파티 참여자인지 확인
        boolean revieweeIsHost = party.getHostId().equals(request.getRevieweeId());
        boolean revieweeIsApprovedApplicant = partyApplicationRepository
                .findByPartyIdAndApplicantId(request.getPartyId(), request.getRevieweeId())
                .map(PartyApplication::getIsApproved)
                .orElse(false);

        if (!revieweeIsHost && !revieweeIsApprovedApplicant) {
            throw new InvalidReviewException("파티 참여자에 대해서만 리뷰를 작성할 수 있습니다.");
        }

        // 5. 본인에게 리뷰 작성 방지
        if (reviewerId.equals(request.getRevieweeId())) {
            throw new InvalidReviewException("본인에게는 리뷰를 작성할 수 없습니다.");
        }

        // 6. 중복 리뷰 방지
        if (partyReviewRepository.existsByPartyIdAndReviewerIdAndRevieweeId(
                request.getPartyId(), reviewerId, request.getRevieweeId())) {
            throw new DuplicateReviewException(request.getPartyId(), reviewerId, request.getRevieweeId());
        }

        // 7. 평점 유효성 검사 (1-5)
        if (request.getRating() == null || request.getRating() < 1 || request.getRating() > 5) {
            throw new InvalidReviewException("평점은 1~5 사이의 값이어야 합니다.");
        }

        // 8. 코멘트 길이 검사 (최대 200자)
        if (request.getComment() != null && request.getComment().length() > 200) {
            throw new InvalidReviewException("코멘트는 최대 200자까지 입력할 수 있습니다.");
        }

        // 9. 리뷰 생성
        PartyReview review = PartyReview.builder()
                .partyId(request.getPartyId())
                .reviewerId(reviewerId)
                .revieweeId(request.getRevieweeId())
                .rating(request.getRating())
                .comment(request.getComment())
                .build();

        PartyReview savedReview = partyReviewRepository.save(review);

        return PartyReviewDTO.Response.from(savedReview);
    }

    /**
     * 파티별 리뷰 조회
     */
    @Transactional(readOnly = true)
    public List<PartyReviewDTO.Response> getReviewsByParty(Long partyId) {
        return partyReviewRepository.findByPartyId(partyId).stream()
                .map(PartyReviewDTO.Response::from)
                .collect(Collectors.toList());
    }

    /**
     * 사용자의 평균 평점 조회
     */
    @Transactional(readOnly = true)
    public Double getAverageRating(Long userId) {
        Double avgRating = partyReviewRepository.calculateAverageRating(userId);
        return avgRating != null ? avgRating : 0.0;
    }
}
