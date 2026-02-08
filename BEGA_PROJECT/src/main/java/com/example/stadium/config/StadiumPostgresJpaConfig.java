package com.example.stadium.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
		basePackages = "com.example.stadium.repository",
		entityManagerFactoryRef = "stadiumEntityManagerFactory",
		transactionManagerRef = "stadiumTransactionManager"
)
public class StadiumPostgresJpaConfig {

	@Bean
	@ConfigurationProperties("baseball.datasource")
	public DataSourceProperties stadiumDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@ConfigurationProperties("baseball.datasource.hikari")
	public DataSource stadiumDataSource() {
		return stadiumDataSourceProperties()
				.initializeDataSourceBuilder()
				.build();
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean stadiumEntityManagerFactory(
			EntityManagerFactoryBuilder builder) {
		return builder
				.dataSource(stadiumDataSource())
				.packages("com.example.stadium.entity")
				.persistenceUnit("stadium")
				.build();
	}

	@Bean
	public PlatformTransactionManager stadiumTransactionManager(
			LocalContainerEntityManagerFactoryBean stadiumEntityManagerFactory) {
		return new JpaTransactionManager(stadiumEntityManagerFactory.getObject());
	}
}
