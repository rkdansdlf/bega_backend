package com.example.kbo.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.TeamFranchiseEntity;
import com.example.kbo.entity.TeamEntity;
import com.example.kbo.entity.TeamHistoryEntity;
import com.example.kbo.repository.TeamFranchiseRepository;
import com.example.kbo.repository.TeamRepository;
import com.example.kbo.repository.TeamHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Import(TeamHistoryService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:team_history_serialization;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class TeamHistorySerializationIntegrationTest {

    @Autowired
    private TeamHistoryService teamHistoryService;

    @Autowired
    private TeamFranchiseRepository teamFranchiseRepository;

    @Autowired
    private TeamHistoryRepository teamHistoryRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void historyAndFranchiseTeams_canBeSerializedAfterRepositoryTransactionsClose() throws Exception {
        Integer season = 2024;
        Integer franchiseId = new TransactionTemplate(transactionManager).execute(status -> {
            TeamFranchiseEntity franchise = teamFranchiseRepository.saveAndFlush(TeamFranchiseEntity.builder()
                    .name("Synthetic Franchise")
                    .originalCode("SYN")
                    .currentCode("SYN")
                    .build());
            teamHistoryRepository.saveAndFlush(TeamHistoryEntity.builder()
                    .franchise(franchise)
                    .season(season)
                    .teamName("Synthetic Team")
                    .teamCode("SYN")
                    .build());
            teamRepository.saveAndFlush(TeamEntity.builder()
                    .teamId("SYN")
                    .teamName("Synthetic Team")
                    .teamShortName("Synthetic")
                    .city("Synthetic City")
                    .franchise(franchise)
                    .isActive(true)
                    .build());
            return franchise.getId();
        });

        List<TeamHistoryEntity> history = teamHistoryService.getHistoryBySeason(season);
        List<TeamHistoryEntity> franchiseHistory = teamHistoryService.getFranchiseHistory(franchiseId);
        TeamHistoryEntity teamSeason = teamHistoryService.getTeamInSeason("SYN", season).orElseThrow();
        List<TeamEntity> allFranchiseTeams = teamRepository.findByFranchiseId(franchiseId);
        List<TeamEntity> activeFranchiseTeams = teamRepository.findAllByFranchise_IdAndIsActive(franchiseId, true);
        TeamEntity currentTeam = teamRepository.findByFranchise_IdAndIsActive(franchiseId, true).orElseThrow();

        assertThat(objectMapper.writeValueAsString(history))
                .contains("Synthetic Franchise", "Synthetic Team", "franchiseId");
        assertThat(objectMapper.writeValueAsString(franchiseHistory))
                .contains("Synthetic Franchise", "Synthetic Team", "franchiseId");
        assertThat(objectMapper.writeValueAsString(teamSeason))
                .contains("Synthetic Franchise", "Synthetic Team", "franchiseId");
        assertThat(objectMapper.writeValueAsString(allFranchiseTeams))
                .contains("Synthetic Franchise", "Synthetic Team", "franchiseId");
        assertThat(objectMapper.writeValueAsString(activeFranchiseTeams))
                .contains("Synthetic Franchise", "Synthetic Team", "franchiseId");
        assertThat(objectMapper.writeValueAsString(currentTeam))
                .contains("Synthetic Franchise", "Synthetic Team", "franchiseId");
    }
}
