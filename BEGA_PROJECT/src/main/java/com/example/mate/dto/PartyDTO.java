package com.example.mate.dto;

import com.example.mate.entity.Party;
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
        private LocalDate gameDate;
        private LocalTime gameTime;
        private String stadium;
        private String homeTeam;
        private String awayTeam;
        private String section;
        private Integer maxParticipants;
        private String description;
        private String ticketImageUrl;
        private Integer ticketPrice;
        private String reservationNumber;
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
                    .ticketImageUrl(response.getTicketImageUrl())
                    .status(response.getStatus())
                    .price(response.getPrice())
                    .ticketPrice(response.getTicketPrice())
                    .reservationNumber(response.getReservationNumber())
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
        private Integer price;
        private String description;
        private String section;
        private Integer maxParticipants;
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
