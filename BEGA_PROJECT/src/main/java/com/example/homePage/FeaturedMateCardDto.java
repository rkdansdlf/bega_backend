package com.example.homepage;

import com.example.mate.entity.Party;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeaturedMateCardDto {

    private Long id;
    private String gameDate;
    private String gameTime;
    private String homeTeam;
    private String awayTeam;
    private Integer currentParticipants;
    private Integer maxParticipants;
    private Integer ticketPrice;
    private Party.PartyStatus status;
}
