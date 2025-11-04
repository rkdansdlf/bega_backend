package com.example.mate.dto;

import com.example.mate.entity.Party;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class PartyDTO {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Request {
        private Long hostId;
        private String hostName;
        private Party.BadgeType hostBadge;
        private Double hostRating;
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
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private Long hostId;
        private String hostName;
        private Party.BadgeType hostBadge;
        private Double hostRating;
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
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static Response from(Party party) {
            return Response.builder()
                    .id(party.getId())
                    .hostId(party.getHostId())
                    .hostName(party.getHostName())
                    .hostBadge(party.getHostBadge())
                    .hostRating(party.getHostRating())
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