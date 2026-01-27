package com.example.common.config;

import com.example.kbo.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

/**
 * DataInitializer
 * 
 * 애플리케이션 시작 시 기본 KBO 팀 데이터를 초기화하는 컴포넌트입니다.
 * teams 테이블이 비어있는 경우 seed_kbo_data.sql을 실행합니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final TeamRepository teamRepository;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        long teamCount = teamRepository.count();
        log.info("Current team count in database: {}", teamCount);

        if (teamCount == 0) {
            log.info("Teams table is empty. Initializing KBO data from seed_kbo_data.sql...");
            try {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
                populator.addScript(new ClassPathResource("seed_kbo_data.sql"));
                populator.execute(dataSource);
                log.info("KBO data initialization completed successfully.");
            } catch (Exception e) {
                log.error("Failed to initialize KBO data: {}", e.getMessage(), e);
            }
        } else {
            log.info("Teams data already exists. Skipping initialization.");
            teamRepository.findAll()
                    .forEach(team -> log.info("Existing Team: ID={}, Name={}", team.getTeamId(), team.getTeamName()));
        }
    }
}
