package com.example.mate.repository;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.mate.entity.Party;
import com.example.mate.entity.PartyApplication;
import com.example.mate.support.MateTestFixtureFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("PartyRepository tests")
class PartyRepositoryTest {

    @Autowired
    private PartyRepository partyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PartyApplicationRepository partyApplicationRepository;

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

    @Test
    @DisplayName("explicit status filter overrides excluded statuses")
    void findPartiesWithFilter_statusOverridesExcludedStatuses() {
        partyRepository.save(createParty(
                "host-pending", "KT", "수원", "KT", "KIA", "응원석", "모집 중", Party.PartyStatus.PENDING));
        Party checkedInParty = partyRepository.save(createParty(
                "host-checked-in", "LG", "잠실", "LG", "SSG", "1루석", "체크인 완료", Party.PartyStatus.CHECKED_IN));

        Page<Party> result = partyRepository.findPartiesWithFilter(
                null,
                null,
                null,
                "",
                List.of(Party.PartyStatus.CHECKED_IN, Party.PartyStatus.COMPLETED),
                Party.PartyStatus.CHECKED_IN,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Party::getId).containsExactly(checkedInParty.getId());
    }

    @Test
    @DisplayName("visible public party query handles null filters and anonymous user")
    void findVisiblePublicPartiesWithFilter_nullFiltersAnonymousUser_returnsPublicParties() {
        UserEntity publicHost = userRepository.save(MateTestFixtureFactory.user("public-host@example.com", "Public Host"));
        UserEntity privateHost = MateTestFixtureFactory.user("private-host@example.com", "Private Host");
        privateHost.setPrivateAccount(true);
        privateHost = userRepository.save(privateHost);

        Party publicParty = partyRepository.save(createPartyForHost(
                publicHost, "KT", "수원", "KT", "KIA", "응원석", "공개 호스트 파티", Party.PartyStatus.PENDING));
        partyRepository.save(createPartyForHost(
                privateHost, "LG", "잠실", "LG", "SSG", "1루석", "비공개 호스트 파티", Party.PartyStatus.PENDING));
        partyRepository.save(createPartyForHost(
                publicHost, "LG", "잠실", "LG", "SSG", "3루석", "체크인 완료", Party.PartyStatus.CHECKED_IN));

        Page<Party> result = partyRepository.findVisiblePublicPartiesWithFilter(
                null,
                null,
                null,
                "",
                List.of(Party.PartyStatus.CHECKED_IN, Party.PartyStatus.COMPLETED),
                null,
                null,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Party::getId).containsExactly(publicParty.getId());
    }

    @Test
    @DisplayName("visible public party query filters nonblank search text")
    void findVisiblePublicPartiesWithFilter_queryFiltersResult() {
        UserEntity publicHost = userRepository.save(MateTestFixtureFactory.user("search-host@example.com", "Search Host"));
        Party blueZoneParty = partyRepository.save(createPartyForHost(
                publicHost, "LG", "잠실", "LG", "SSG", "블루존", "bluezone match", Party.PartyStatus.PENDING));
        partyRepository.save(createPartyForHost(
                publicHost, "KT", "수원", "KT", "KIA", "응원석", "orange match", Party.PartyStatus.PENDING));

        Page<Party> result = partyRepository.findVisiblePublicPartiesWithFilter(
                null,
                null,
                null,
                "  BLUEZONE  ",
                List.of(Party.PartyStatus.CHECKED_IN, Party.PartyStatus.COMPLETED),
                null,
                null,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Party::getId).containsExactly(blueZoneParty.getId());
    }

    @Test
    @DisplayName("visible public party query status filter overrides excluded statuses")
    void findVisiblePublicPartiesWithFilter_statusOverridesExcludedStatuses() {
        UserEntity publicHost = userRepository.save(MateTestFixtureFactory.user("status-host@example.com", "Status Host"));
        partyRepository.save(createPartyForHost(
                publicHost, "KT", "수원", "KT", "KIA", "응원석", "모집 중", Party.PartyStatus.PENDING));
        Party checkedInParty = partyRepository.save(createPartyForHost(
                publicHost, "LG", "잠실", "LG", "SSG", "1루석", "체크인 완료", Party.PartyStatus.CHECKED_IN));

        Page<Party> result = partyRepository.findVisiblePublicPartiesWithFilter(
                null,
                null,
                null,
                "",
                List.of(Party.PartyStatus.CHECKED_IN, Party.PartyStatus.COMPLETED),
                Party.PartyStatus.CHECKED_IN,
                null,
                PageRequest.of(0, 10));

        assertThat(result.getContent()).extracting(Party::getId).containsExactly(checkedInParty.getId());
    }

    @Test
    @DisplayName("visible public party query adds id tie-breaker for stable pagination")
    void findVisiblePublicPartiesWithFilter_addsIdTieBreakerForStablePagination() {
        UserEntity publicHost = userRepository.save(MateTestFixtureFactory.user("tie-host@example.com", "Tie Host"));
        Party firstParty = partyRepository.save(createPartyForHost(
                publicHost, "KT", "수원", "KT", "KIA", "응원석", "첫 번째 파티", Party.PartyStatus.PENDING));
        Party secondParty = partyRepository.save(createPartyForHost(
                publicHost, "LG", "잠실", "LG", "SSG", "1루석", "두 번째 파티", Party.PartyStatus.PENDING));

        Page<Party> result = partyRepository.findVisiblePublicPartiesWithFilter(
                null,
                null,
                null,
                "",
                List.of(Party.PartyStatus.CHECKED_IN, Party.PartyStatus.COMPLETED),
                null,
                null,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "currentParticipants")));

        assertThat(result.getContent())
                .extracting(Party::getId)
                .containsExactly(secondParty.getId(), firstParty.getId());
    }

    @Test
    @DisplayName("my history query includes hosted and approved parties with paging and no duplicates")
    void findMyHistory_includesHostedAndApprovedPartiesWithPaging() {
        UserEntity me = userRepository.save(MateTestFixtureFactory.user("history-user@example.com", "History User"));
        UserEntity otherHost = userRepository.save(MateTestFixtureFactory.user("history-host@example.com", "History Host"));

        Party hostedParty = partyRepository.save(createPartyForHost(
                me, "KT", "수원", "KT", "KIA", "응원석", "호스트 파티", Party.PartyStatus.PENDING));
        Party approvedParty = partyRepository.save(createPartyForHost(
                otherHost, "LG", "잠실", "LG", "SSG", "1루석", "승인 참여 파티", Party.PartyStatus.MATCHED));
        Party pendingApplicationParty = partyRepository.save(createPartyForHost(
                otherHost, "WO", "고척", "WO", "LG", "3루석", "미승인 신청 파티", Party.PartyStatus.PENDING));
        Party rejectedApplicationParty = partyRepository.save(createPartyForHost(
                otherHost, "LT", "사직", "LT", "KT", "외야석", "거절 신청 파티", Party.PartyStatus.PENDING));
        Party duplicateHostedParty = partyRepository.save(createPartyForHost(
                me, "NC", "창원", "NC", "HH", "내야석", "호스트이면서 승인 신청 파티", Party.PartyStatus.COMPLETED));

        partyApplicationRepository.save(createApplication(approvedParty, me, true, false));
        partyApplicationRepository.save(createApplication(pendingApplicationParty, me, false, false));
        partyApplicationRepository.save(createApplication(rejectedApplicationParty, me, false, true));
        partyApplicationRepository.save(createApplication(duplicateHostedParty, me, true, false));

        Page<Party> firstPage = partyRepository.findMyHistory(
                me.getId(),
                null,
                PageRequest.of(0, 2, latestHistorySort()));

        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent()).hasSize(2);

        Page<Party> all = partyRepository.findMyHistory(
                me.getId(),
                null,
                PageRequest.of(0, 10, latestHistorySort()));

        assertThat(all.getContent())
                .extracting(Party::getId)
                .containsExactly(duplicateHostedParty.getId(), approvedParty.getId(), hostedParty.getId());
        assertThat(all.getContent())
                .extracting(Party::getId)
                .doesNotContain(pendingApplicationParty.getId(), rejectedApplicationParty.getId());
    }

    @Test
    @DisplayName("my history query applies status group filters")
    void findMyHistory_filtersByStatuses() {
        UserEntity me = userRepository.save(MateTestFixtureFactory.user("history-filter-user@example.com", "History Filter"));
        UserEntity otherHost = userRepository.save(MateTestFixtureFactory.user("history-filter-host@example.com", "History Filter Host"));

        Party completedParty = partyRepository.save(createPartyForHost(
                me, "KT", "수원", "KT", "KIA", "응원석", "완료 파티", Party.PartyStatus.COMPLETED));
        Party checkedInParty = partyRepository.save(createPartyForHost(
                otherHost, "LG", "잠실", "LG", "SSG", "1루석", "체크인 참여 파티", Party.PartyStatus.CHECKED_IN));
        Party ongoingParty = partyRepository.save(createPartyForHost(
                me, "WO", "고척", "WO", "LG", "3루석", "진행 중 파티", Party.PartyStatus.MATCHED));

        partyApplicationRepository.save(createApplication(checkedInParty, me, true, false));

        Page<Party> result = partyRepository.findMyHistory(
                me.getId(),
                List.of(Party.PartyStatus.COMPLETED, Party.PartyStatus.CHECKED_IN),
                PageRequest.of(0, 10, latestHistorySort()));

        assertThat(result.getContent())
                .extracting(Party::getId)
                .containsExactly(checkedInParty.getId(), completedParty.getId());
        assertThat(result.getContent()).extracting(Party::getId).doesNotContain(ongoingParty.getId());
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
                .teamId(teamId)
                .gameDate(LocalDate.now().plusDays(1))
                .gameTime(LocalTime.of(18, 30))
                .stadium(stadium)
                .homeTeam(homeTeam)
                .awayTeam(awayTeam)
                .section(section)
                .searchText(String.join(" ",
                        stadium,
                        homeTeam,
                        awayTeam,
                        section,
                        hostName,
                        description))
                .maxParticipants(4)
                .currentParticipants(1)
                .description(description)
                .ticketVerified(false)
                .status(status)
                .build();
    }

    private Party createPartyForHost(UserEntity host,
                                     String teamId,
                                     String stadium,
                                     String homeTeam,
                                     String awayTeam,
                                     String section,
                                     String description,
                                     Party.PartyStatus status) {
        Party party = createParty(
                host.getName(),
                teamId,
                stadium,
                homeTeam,
                awayTeam,
                section,
                description,
                status);
        party.setHostId(host.getId());
        return party;
    }

    private PartyApplication createApplication(
            Party party,
            UserEntity applicant,
            boolean approved,
            boolean rejected) {
        return PartyApplication.builder()
                .partyId(party.getId())
                .applicantId(applicant.getId())
                .applicantName(applicant.getName())
                .applicantBadge(Party.BadgeType.NEW)
                .applicantRating(5.0)
                .message("같이 응원하고 싶습니다.")
                .depositAmount(0)
                .isPaid(false)
                .isApproved(approved)
                .isRejected(rejected)
                .paymentType(PartyApplication.PaymentType.DEPOSIT)
                .build();
    }

    private Sort latestHistorySort() {
        return Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
