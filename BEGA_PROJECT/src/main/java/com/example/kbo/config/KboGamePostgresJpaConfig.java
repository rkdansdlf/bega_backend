package com.example.kbo.config;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * KBO game-read stack(PostgreSQL) 전용 JPA 구성.
 *
 * 분리 대상은 GameRepository 계열(경기/메타/이닝/요약)만 우선 적용한다.
 * 기본 도메인(auth/prediction write 등)은 primary datasource 경로를 유지한다.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
		basePackages = "com.example.kbo.repository",
		excludeFilters = @ComponentScan.Filter(
				type = org.springframework.context.annotation.FilterType.REGEX,
				pattern = "com\\.example\\.kbo\\.repository\\.(AwardRepository|PlayerMovementRepository|TeamFranchiseRepository|TeamHistoryRepository|TeamRepository|TicketVerificationRepository)"
		),
		entityManagerFactoryRef = "kboGameEntityManagerFactory",
		transactionManagerRef = "kboGameTransactionManager"
)
public class KboGamePostgresJpaConfig {

	@Bean
	public LocalContainerEntityManagerFactoryBean kboGameEntityManagerFactory(
			EntityManagerFactoryBuilder builder,
			@Qualifier("stadiumDataSource") DataSource stadiumDataSource) {
		return builder
				.dataSource(stadiumDataSource)
				.packages("com.example.kbo.entity")
				.persistenceUnit("kboGame")
				.build();
	}

	@Bean
	public PlatformTransactionManager kboGameTransactionManager(
			@Qualifier("kboGameEntityManagerFactory") LocalContainerEntityManagerFactoryBean kboGameEntityManagerFactory) {
		return new JpaTransactionManager(kboGameEntityManagerFactory.getObject());
	}
}
