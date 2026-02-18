package com.example.mate.repository;

import com.example.mate.entity.Party;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("PartyRepository tests")
class PartyRepositoryTest {

    @Autowired
    private PartyRepository partyRepository;

    @Test
    @DisplayName("empty search query does not fail and returns non-excluded statuses")
    void findPartiesWithFilter_emptyQuery_returnsResults() {
        Party pendingParty = partyRepository.save(createParty(
                "host-a", "KT", "수원", "KT", "KIA", "응원석", "같이 응원", Party.PartyStatus.PENDING));
        partyRepository.save(createParty(
                "host-b", "LG", "잠실", "LG", "SSG", "1루석", "완료된 파티", Party.PartyStatus.CHECKED_IN));

        Page<Party> result = partyRepository.findPartiesWithFilter(
                null,
                null,
                null,
                "",
                List.of(Party.PartyStatus.CHECKED_IN, Party.PartyStatus.COMPLETED),
                null,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Party::getId).contains(pendingParty.getId());
        assertThat(result.getContent()).allMatch(p -> p.getStatus() != Party.PartyStatus.CHECKED_IN);
    }

    @Test
    @DisplayName("search query filters by searchable fields")
    void findPartiesWithFilter_queryFiltersResult() {
        partyRepository.save(createParty(
                "host-kt", "KT", "수원", "KT", "KIA", "응원석", "kt 응원 파티", Party.PartyStatus.PENDING));
        partyRepository.save(createParty(
                "host-lg", "LG", "잠실", "LG", "SSG", "외야석", "lg 응원 파티", Party.PartyStatus.PENDING));

        Page<Party> result = partyRepository.findPartiesWithFilter(
                null,
                null,
                null,
                "kt",
                List.of(Party.PartyStatus.CHECKED_IN, Party.PartyStatus.COMPLETED),
                null,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getHomeTeam()).isEqualTo("KT");
    }

    private Party createParty(String hostName,
                              String teamId,
                              String stadium,
                              String homeTeam,
                              String awayTeam,
                              String section,
                              String description,
                              Party.PartyStatus status) {
        return Party.builder()
                .hostId(Math.abs((long) hostName.hashCode()))
                .hostName(hostName)
                .hostBadge(Party.BadgeType.NEW)
                .hostRating(5.0)
                .teamId(teamId)
                .gameDate(LocalDate.now().plusDays(1))
                .gameTime(LocalTime.of(18, 30))
                .stadium(stadium)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .section(section)
                .maxParticipants(4)
                .currentParticipants(1)
                .description(description)
                .ticketVerified(false)
                .status(status)
                .build();
    }
}
