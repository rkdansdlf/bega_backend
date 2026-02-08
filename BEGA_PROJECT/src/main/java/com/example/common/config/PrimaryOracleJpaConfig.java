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

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
		basePackages = "com.example",
		excludeFilters = @ComponentScan.Filter(
				type = org.springframework.context.annotation.FilterType.REGEX,
				pattern = "com\\.example\\.stadium\\.repository\\..*"
		)
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
