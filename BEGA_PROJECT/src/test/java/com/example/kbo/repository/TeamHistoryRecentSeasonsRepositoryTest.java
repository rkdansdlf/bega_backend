package com.example.kbo.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.kbo.entity.TeamFranchiseEntity;
import com.example.kbo.entity.TeamHistoryEntity;
import com.example.kbo.service.TeamHistoryService;
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
        "spring.datasource.url=jdbc:h2:mem:team_history_recent_seasons;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.open-in-view=false"
})
class TeamHistoryRecentSeasonsRepositoryTest {

    @Autowired
    private TeamHistoryService teamHistoryService;

    @Autowired
    private TeamFranchiseRepository teamFranchiseRepository;

    @Autowired
    private TeamHistoryRepository teamHistoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void recentLimitReturnsEveryTeamFromOnlyTheRequestedNumberOfSeasons() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            TeamFranchiseEntity franchise = teamFranchiseRepository.saveAndFlush(TeamFranchiseEntity.builder()
                    .name("Synthetic Franchise")
                    .originalCode("SYN")
                    .currentCode("SYN")
                    .build());

            for (int season = 2023; season <= 2025; season++) {
                for (int teamIndex = 1; teamIndex <= 2; teamIndex++) {
                    teamHistoryRepository.saveAndFlush(TeamHistoryEntity.builder()
                            .franchise(franchise)
                            .season(season)
                            .teamName("Synthetic Team " + teamIndex)
                            .teamCode("SYN" + season + teamIndex)
                            .build());
                }
            }
        });

        List<TeamHistoryEntity> recent = teamHistoryService.getRecentSeasons(2);

        assertThat(recent)
                .extracting(TeamHistoryEntity::getSeason)
                .containsExactly(2025, 2025, 2024, 2024);
    }
}
