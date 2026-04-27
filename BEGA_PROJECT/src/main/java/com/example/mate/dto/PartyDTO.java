package com.example.mate.dto;

import com.example.mate.entity.Party;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public class PartyDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private String teamId;

        @NotNull(message = "응원 방향은 필수입니다.")
        private Party.CheeringSide cheeringSide;

        @NotNull(message = "경기 날짜는 필수입니다.")
        private LocalDate gameDate;

        @NotNull(message = "경기 시간은 필수입니다.")
        private LocalTime gameTime;

        @NotBlank(message = "구장 정보는 필수입니다.")
        @Size(max = 100, message = "구장 정보는 100자 이하여야 합니다.")
        private String stadium;

        @NotBlank(message = "홈 팀 정보는 필수입니다.")
        @Size(max = 20, message = "홈 팀 정보는 20자 이하여야 합니다.")
        private String homeTeam;

        @NotBlank(message = "원정 팀 정보는 필수입니다.")
        @Size(max = 20, message = "원정 팀 정보는 20자 이하여야 합니다.")
        private String awayTeam;

        @NotBlank(message = "좌석 정보는 필수입니다.")
        @Size(max = 50, message = "좌석 정보는 50자 이하여야 합니다.")
        private String section;

        @NotNull(message = "최대 참여 인원은 필수입니다.")
        @Min(value = 2, message = "최대 참여 인원은 2명 이상이어야 합니다.")
        @Max(value = 20, message = "최대 참여 인원은 20명 이하여야 합니다.")
        private Integer maxParticipants;

        @NotBlank(message = "소개글은 필수입니다.")
        @Size(min = 10, max = 200, message = "소개글은 10자 이상 200자 이하로 입력해주세요.")
        private String description;

        @Size(max = 2048, message = "티켓 이미지 URL은 2048자 이하여야 합니다.")
        private String ticketImageUrl;

        @Min(value = 0, message = "티켓 가격은 0원 이상이어야 합니다.")
        private Integer ticketPrice;

        @Size(max = 50, message = "예매번호는 50자 이하여야 합니다.")
        private String reservationNumber;

        @NotBlank(message = "예매 인증 토큰은 필수입니다.")
        @Size(max = 128, message = "예매 인증 토큰은 128자 이하여야 합니다.")
        private String verificationToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PublicResponse {
        private Long id;
        private String hostHandle;
        private String hostName;
        private String hostProfileImageUrl;
        private String hostFavoriteTeam;
        private Party.BadgeType hostBadge;
        private Double hostAverageRating;
        private Long hostReviewCount;
        private String teamId;
        private Party.CheeringSide cheeringSide;
        private LocalDate gameDate;
        private LocalTime gameTime;
        private String stadium;
        private String homeTeam;
        private String awayTeam;
        private String section;
        private Integer maxParticipants;
        private Integer currentParticipants;
        private String description;
        private Boolean ticketVerified;
        private Party.PartyStatus status;
        private Integer price;
        private Integer ticketPrice;
        private Instant createdAt;
        private Instant updatedAt;

        public static PublicResponse from(Response response) {
            return PublicResponse.builder()
                    .id(response.getId())
                    .hostHandle(response.getHostHandle())
                    .hostName(response.getHostName())
                    .hostProfileImageUrl(response.getHostProfileImageUrl())
                    .hostFavoriteTeam(response.getHostFavoriteTeam())
                    .hostBadge(response.getHostBadge())
                    .hostAverageRating(response.getHostAverageRating())
                    .hostReviewCount(response.getHostReviewCount())
                    .teamId(response.getTeamId())
                    .cheeringSide(response.getCheeringSide())
                    .gameDate(response.getGameDate())
                    .gameTime(response.getGameTime())
                    .stadium(response.getStadium())
                    .homeTeam(response.getHomeTeam())
                    .awayTeam(response.getAwayTeam())
                    .section(response.getSection())
                    .maxParticipants(response.getMaxParticipants())
                    .currentParticipants(response.getCurrentParticipants())
                    .description(response.getDescription())
                    .ticketVerified(response.getTicketVerified())
                    .status(response.getStatus())
                    .price(response.getPrice())
                    .ticketPrice(response.getTicketPrice())
                    .createdAt(response.getCreatedAt())
                    .updatedAt(response.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long hostId;
        private String hostHandle;
        private String hostName;
        private String hostProfileImageUrl;
        private String hostFavoriteTeam;
        private Party.BadgeType hostBadge;
        private Double hostAverageRating;
        private Long hostReviewCount;
        private String teamId;
        private Party.CheeringSide cheeringSide;
        private LocalDate gameDate;
        private LocalTime gameTime;
        private String stadium;
        private String homeTeam;
        private String awayTeam;
        private String section;
        private Integer maxParticipants;
        private Integer currentParticipants;
        private String description;
        private Boolean ticketVerified;
        private String ticketImageUrl;
        private Party.PartyStatus status;
        private Integer price;
        private Integer ticketPrice;
        private String reservationNumber;
        private Instant createdAt;
        private Instant updatedAt;

        public static Response from(Party party) {
            return Response.builder()
                    .id(party.getId())
                    .hostId(party.getHostId())
                    .hostHandle(null)
                    .hostName(party.getHostName())
                    .hostProfileImageUrl(party.getHostProfileImageUrl())
                    .hostFavoriteTeam(party.getHostFavoriteTeam())
                    .hostBadge(party.getHostBadge())
                    .teamId(party.getTeamId())
                    .cheeringSide(party.getCheeringSide())
                    .gameDate(party.getGameDate())
                    .gameTime(party.getGameTime())
                    .stadium(party.getStadium())
                    .homeTeam(party.getHomeTeam())
                    .awayTeam(party.getAwayTeam())
                    .section(party.getSection())
                    .maxParticipants(party.getMaxParticipants())
                    .currentParticipants(party.getCurrentParticipants())
                    .description(party.getDescription())
                    .ticketVerified(party.getTicketVerified())
                    .ticketImageUrl(party.getTicketImageUrl())
                    .status(party.getStatus())
                    .price(party.getPrice())
                    .ticketPrice(party.getTicketPrice())
                    .reservationNumber(party.getReservationNumber())
                    .createdAt(party.getCreatedAt())
                    .updatedAt(party.getUpdatedAt())
                    .build();
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private Party.PartyStatus status;

        @Min(value = 100, message = "판매 가격은 최소 100원 이상이어야 합니다.")
        private Integer price;

        @Size(min = 10, max = 200, message = "소개글은 10자 이상 200자 이하로 입력해주세요.")
        private String description;

        @Size(max = 50, message = "좌석 정보는 50자 이하여야 합니다.")
        private String section;

        @Min(value = 2, message = "최대 참여 인원은 2명 이상이어야 합니다.")
        @Max(value = 20, message = "최대 참여 인원은 20명 이하여야 합니다.")
        private Integer maxParticipants;

        @Min(value = 0, message = "티켓 가격은 0원 이상이어야 합니다.")
        private Integer ticketPrice;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchRequest {
        private String query;
        private Party.PartyStatus status;
        private LocalDate startDate;
        private LocalDate endDate;
        private String stadium;
        private String teamId;
    }
}
