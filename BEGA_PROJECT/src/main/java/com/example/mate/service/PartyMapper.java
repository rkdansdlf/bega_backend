package com.example.mate.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.homepage.FeaturedMateCardDto;
import com.example.kbo.util.TeamCodeNormalizer;
import com.example.mate.dto.PartyDTO;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyReview;
import com.example.mate.repository.PartyApplicationRepository;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.PartyReviewRepository;
import com.example.profile.storage.service.ProfileImageService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PartyMapper {

    private static final List<Party.PartyStatus> TRUST_COMPLETED_STATUSES = List.of(
            Party.PartyStatus.CHECKED_IN,
            Party.PartyStatus.COMPLETED);

    private static final Map<String, List<String>> REVIEW_KEYWORDS = Map.of(
            "매너 좋아요", List.of("매너", "친절", "좋", "배려"),
            "시간 약속", List.of("시간", "약속", "정시", "늦지"),
            "응원 분위기", List.of("응원", "분위기", "재밌", "즐거"),
            "소통 원활", List.of("소통", "답장", "연락", "대화"),
            "초보 환영", List.of("초보", "처음", "설명", "안내"));

    private final UserRepository userRepository;
    private final PartyRepository partyRepository;
    private final PartyApplicationRepository partyApplicationRepository;
    private final PartyReviewRepository partyReviewRepository;
    private final ProfileImageService profileImageService;

    public PartyDTO.Response toResponse(Party party) {
        Map<Long, HostInfo> hostInfoById = loadHostInfoMap(List.of(party));
        PartyDTO.Response response = toResponse(party, hostInfoById, loadHostRatingMetrics(List.of(party)));
        HostInfo hostInfo = party != null && party.getHostId() != null ? hostInfoById.get(party.getHostId()) : null;
        applyHostTrustMetrics(response, party, hostInfo);
        return response;
    }

    public PartyDTO.PublicResponse toPublicResponse(Party party) {
        PartyDTO.Response response = toResponse(party);
        return response == null ? null : PartyDTO.PublicResponse.from(response);
    }

    public List<PartyDTO.Response> toResponses(List<Party> parties) {
        if (parties == null || parties.isEmpty()) {
            return List.of();
        }

        Map<Long, HostInfo> hostInfoById = loadHostInfoMap(parties);
        Map<Long, HostRatingMetrics> ratingMetricsByHostId = loadHostRatingMetrics(parties);
        return parties.stream()
                .map(party -> toResponse(party, hostInfoById, ratingMetricsByHostId))
                .toList();
    }

    public List<PartyDTO.PublicResponse> toPublicResponses(List<Party> parties) {
        if (parties == null || parties.isEmpty()) {
            return List.of();
        }

        Map<Long, HostInfo> hostInfoById = loadHostInfoMap(parties);
        Map<Long, HostRatingMetrics> ratingMetricsByHostId = loadHostRatingMetrics(parties);
        return parties.stream()
                .map(party -> {
                    PartyDTO.Response response = toResponse(party, hostInfoById, ratingMetricsByHostId);
                    return response == null ? null : PartyDTO.PublicResponse.from(response);
                })
                .toList();
    }

    public FeaturedMateCardDto toFeaturedMateCard(Party party) {
        Party.CheeringSide resolvedCheeringSide = resolveCheeringSide(party);
        return FeaturedMateCardDto.builder()
                .id(party.getId())
                .hostId(party.getHostId())
                .teamId(resolveEffectiveTeamId(party, resolvedCheeringSide))
                .gameDate(party.getGameDate() == null ? null : party.getGameDate().toString())
                .gameTime(party.getGameTime() == null ? null : party.getGameTime().toString())
                .stadium(party.getStadium())
                .section(party.getSection())
                .description(party.getDescription())
                .homeTeam(party.getHomeTeam())
                .awayTeam(party.getAwayTeam())
                .currentParticipants(party.getCurrentParticipants())
                .maxParticipants(party.getMaxParticipants())
                .ticketPrice(party.getTicketPrice())
                .status(party.getStatus() == null ? null : party.getStatus().name())
                .build();
    }

    public boolean isLegacyOrInvalidProfileValue(String profilePathOrUrl) {
        if (profilePathOrUrl == null || profilePathOrUrl.isBlank()) {
            return false;
        }
        return profilePathOrUrl.startsWith("/assets/")
                || profilePathOrUrl.startsWith("/src/assets/")
                || profilePathOrUrl.startsWith("blob:")
                || profilePathOrUrl.startsWith("data:");
    }

    private PartyDTO.Response toResponse(
            Party party,
            Map<Long, HostInfo> hostInfoById,
            Map<Long, HostRatingMetrics> ratingMetricsByHostId) {
        if (party == null) {
            return null;
        }

        PartyDTO.Response response = PartyDTO.Response.from(party);
        Party.CheeringSide resolvedCheeringSide = resolveCheeringSide(party);
        response.setCheeringSide(resolvedCheeringSide);
        response.setTeamId(resolveEffectiveTeamId(party, resolvedCheeringSide));
        String profilePathOrUrl = party.getHostProfileImageUrl();
        Long hostId = party.getHostId();
        HostInfo hostInfo = hostId != null ? hostInfoById.get(hostId) : null;

        if (isLegacyOrInvalidProfileValue(profilePathOrUrl)) {
            profilePathOrUrl = hostInfo != null && isUsableProfileValue(hostInfo.profileImageUrl())
                    ? hostInfo.profileImageUrl()
                    : null;
        }

        response.setHostProfileImageUrl(profileImageService.getProfileImageUrlForUser(hostId, profilePathOrUrl));
        if (hostInfo != null) {
            response.setHostHandle(hostInfo.handle());
        }

        HostRatingMetrics hostRatingMetrics = hostId != null
                ? ratingMetricsByHostId.getOrDefault(hostId, HostRatingMetrics.empty())
                : HostRatingMetrics.empty();
        applyHostRatingMetrics(response, hostRatingMetrics);
        return Objects.requireNonNull(response);
    }

    private Map<Long, HostInfo> loadHostInfoMap(List<Party> parties) {
        if (parties == null || parties.isEmpty()) {
            return Map.of();
        }

        List<Long> hostIds = parties.stream()
                .map(Party::getHostId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (hostIds.isEmpty()) {
            return Map.of();
        }

        return userRepository.findAllById(hostIds).stream()
                .collect(Collectors.toMap(
                        UserEntity::getId,
                        user -> new HostInfo(
                                user.getHandle(),
                                user.getProfileImageUrl(),
                                user.getFavoriteTeamId(),
                                user.getLastLoginDate())));
    }

    private Map<Long, HostRatingMetrics> loadHostRatingMetrics(List<Party> parties) {
        if (parties == null || parties.isEmpty()) {
            return Map.of();
        }

        List<Long> hostIds = parties.stream()
                .map(Party::getHostId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (hostIds.isEmpty()) {
            return Map.of();
        }

        List<PartyReviewRepository.RevieweeRatingSummary> summaries = partyReviewRepository
                .findRatingSummariesByRevieweeIds(hostIds);
        if (summaries == null || summaries.isEmpty()) {
            return Map.of();
        }

        Map<Long, HostRatingMetrics> metricsByHostId = new HashMap<>();
        summaries.forEach(summary -> {
            if (summary.getRevieweeId() == null) {
                return;
            }
            metricsByHostId.put(
                    summary.getRevieweeId(),
                    new HostRatingMetrics(summary.getAverageRating(), summary.getReviewCount()));
        });
        return metricsByHostId;
    }

    private void applyHostRatingMetrics(PartyDTO.Response response, HostRatingMetrics metrics) {
        if (response == null || metrics == null) {
            return;
        }
        response.setHostAverageRating(metrics.reviewCount() > 0 ? metrics.averageRating() : null);
        response.setHostReviewCount(metrics.reviewCount());
    }

    private void applyHostTrustMetrics(PartyDTO.Response response, Party party, HostInfo hostInfo) {
        if (response == null || party == null || party.getHostId() == null) {
            return;
        }

        Long hostId = party.getHostId();
        response.setHostTrustMetrics(PartyDTO.HostTrustMetrics.builder()
                .averageResponseMinutes(resolveAverageResponseMinutes(hostId))
                .lastActiveAt(hostInfo != null ? hostInfo.lastLoginDate() : null)
                .completedMateCount(partyRepository.countByHostIdAndStatusIn(hostId, TRUST_COMPLETED_STATUSES))
                .recentNoShowCount(partyRepository.countHostedNoShowsSince(
                        hostId,
                        TRUST_COMPLETED_STATUSES,
                        LocalDate.now().minusMonths(6)))
                .reviewKeywordSummary(resolveReviewKeywordSummary(hostId))
                .recentHostReviews(resolveRecentHostReviews(hostId))
                .build());
    }

    private Integer resolveAverageResponseMinutes(Long hostId) {
        List<PartyApplicationRepository.HostResponseTiming> timings = partyApplicationRepository
                .findResponseTimingsByHostId(hostId);
        if (timings == null || timings.isEmpty()) {
            return null;
        }

        double averageMinutes = timings.stream()
                .filter(timing -> timing.getCreatedAt() != null && timing.getRespondedAt() != null)
                .mapToLong(timing -> Duration.between(timing.getCreatedAt(), timing.getRespondedAt()).toMinutes())
                .filter(minutes -> minutes >= 0)
                .average()
                .orElse(-1);
        return averageMinutes < 0 ? null : (int) Math.round(averageMinutes);
    }

    private List<PartyDTO.ReviewKeywordSummary> resolveReviewKeywordSummary(Long hostId) {
        List<PartyReview> reviews = partyReviewRepository.findByRevieweeId(hostId);
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }

        List<PartyDTO.ReviewKeywordSummary> summaries = new ArrayList<>();
        REVIEW_KEYWORDS.forEach((label, keywords) -> {
            long count = reviews.stream()
                    .map(PartyReview::getComment)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .filter(comment -> keywords.stream().anyMatch(keyword -> comment.contains(keyword.toLowerCase())))
                    .count();
            if (count > 0) {
                summaries.add(PartyDTO.ReviewKeywordSummary.builder()
                        .label(label)
                        .count(count)
                        .build());
            }
        });

        return summaries.stream()
                .sorted((left, right) -> Long.compare(right.getCount(), left.getCount()))
                .limit(3)
                .toList();
    }

    private List<PartyDTO.HostReviewSnippet> resolveRecentHostReviews(Long hostId) {
        List<PartyReview> reviews = partyReviewRepository.findTop2ByRevieweeIdOrderByCreatedAtDesc(hostId);
        if (reviews == null || reviews.isEmpty()) {
            return List.of();
        }

        Map<Long, String> reviewerHandles = userRepository.findAllById(
                reviews.stream()
                        .map(PartyReview::getReviewerId)
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList())
                .stream()
                .collect(Collectors.toMap(UserEntity::getId, UserEntity::getHandle));

        return reviews.stream()
                .map(review -> PartyDTO.HostReviewSnippet.builder()
                        .reviewerHandle(reviewerHandles.get(review.getReviewerId()))
                        .rating(review.getRating())
                        .comment(review.getComment())
                        .createdAt(review.getCreatedAt())
                        .build())
                .toList();
    }

    private Party.CheeringSide resolveCheeringSide(Party party) {
        if (party == null) {
            return null;
        }
        if (party.getCheeringSide() != null) {
            return party.getCheeringSide();
        }
        String section = party.getSection();
        if (section == null) {
            return null;
        }
        if (section.startsWith("[홈응원]")) {
            return Party.CheeringSide.HOME;
        }
        if (section.startsWith("[원정응원]")) {
            return Party.CheeringSide.AWAY;
        }
        if (section.startsWith("[중립]")) {
            return Party.CheeringSide.NEUTRAL;
        }
        return null;
    }

    private String resolveEffectiveTeamId(Party party, Party.CheeringSide cheeringSide) {
        if (party == null) {
            return null;
        }
        if (cheeringSide == null) {
            return party.getTeamId();
        }
        return switch (cheeringSide) {
            case HOME -> normalizeTeamCode(party.getHomeTeam());
            case AWAY -> normalizeTeamCode(party.getAwayTeam());
            case NEUTRAL -> "NEUTRAL";
        };
    }

    private String normalizeTeamCode(String teamCode) {
        if (teamCode == null) {
            return null;
        }
        String normalized = TeamCodeNormalizer.normalize(teamCode);
        return normalized == null || normalized.isBlank() ? teamCode : normalized;
    }

    private boolean isUsableProfileValue(String profilePathOrUrl) {
        if (profilePathOrUrl == null || profilePathOrUrl.isBlank()) {
            return false;
        }
        return !isLegacyOrInvalidProfileValue(profilePathOrUrl);
    }

    private record HostRatingMetrics(Double averageRating, long reviewCount) {
        private static HostRatingMetrics empty() {
            return new HostRatingMetrics(null, 0L);
        }
    }

    private record HostInfo(
            String handle,
            String profileImageUrl,
            String favoriteTeamId,
            java.time.LocalDateTime lastLoginDate) {
    }
}
