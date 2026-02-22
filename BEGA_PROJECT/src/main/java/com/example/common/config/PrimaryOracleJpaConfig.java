package com.example.common.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Primary JPA configuration.
 *
 * 운영(prod)에서는 spring.datasource가 Oracle Autonomous DB를 가리키고,
 * 개발(dev)에서는 spring.datasource가 PostgreSQL을 가리킨다.
 *
 * 현재는 stadium 전용 repository만 별도 DataSource로 분리하고, 나머지 도메인
 * (auth/prediction/kbo 등)은 primary persistence unit으로 유지한다.
 * 이 구조는 기존 서비스 트랜잭션 경계를 깨지 않기 위한 호환성 우선 설계다.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
		basePackages = "com.example",
		excludeFilters = {
				@ComponentScan.Filter(
						type = org.springframework.context.annotation.FilterType.REGEX,
						pattern = "com\\.example\\.stadium\\.repository\\..*"
				),
				@ComponentScan.Filter(
						type = org.springframework.context.annotation.FilterType.REGEX,
						pattern = "com\\.example\\.kbo\\.repository\\.(GameRepository|GameMetadataRepository|GameInningScoreRepository|GameSummaryRepository)"
				)
		}
)
public class PrimaryOracleJpaConfig {

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties primaryDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource.hikari")
	public DataSource primaryDataSource() {
		return primaryDataSourceProperties()
				.initializeDataSourceBuilder()
				.build();
	}

	@Bean
	@Primary
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
			EntityManagerFactoryBuilder builder) {
		return builder
				.dataSource(primaryDataSource())
				.packages(
						"com.example.admin",
						"com.example.auth",
						"com.example.bega",
						"com.example.BegaDiary",
						"com.example.cheerboard",
						"com.example.common",
						"com.example.homepage",
						"com.example.kbo",
						"com.example.leaderboard",
						"com.example.mate",
						"com.example.mypage",
						"com.example.notification",
						"com.example.prediction",
						"com.example.profile",
						"com.example.teamRecommendationTest"
				)
				.persistenceUnit("primary")
				.build();
	}

	@Bean
	@Primary
	public PlatformTransactionManager transactionManager(
			LocalContainerEntityManagerFactoryBean entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory.getObject());
	}
}
