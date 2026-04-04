package com.example.stadium.config;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Secondary PostgreSQL JPA configuration.
 *
 * baseball.datasource 계열을 사용하며, 현재 범위는 stadium 도메인 전용이다.
 * KBO/Prediction까지 PostgreSQL로 확장하려면 repository 스캔 범위 변경만으로
 * 끝나지 않고 서비스 계층의 transactionManager 경계 재설계가 함께 필요하다.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
		basePackages = "com.example.stadium.repository",
		entityManagerFactoryRef = "stadiumEntityManagerFactory",
		transactionManagerRef = "stadiumTransactionManager"
)
public class StadiumPostgresJpaConfig {

	private static final String HIBERNATE_DEFAULT_SCHEMA = "hibernate.default_schema";
	private static final String HIBERNATE_HBM2DDL_AUTO = "hibernate.hbm2ddl.auto";
	private static final String HIBERNATE_DIALECT = "hibernate.dialect";
	private static final String HIBERNATE_ALLOW_METADATA = "hibernate.boot.allow_jdbc_metadata_access";
	private static final String HIBERNATE_METADATA_DEFAULTS = "hibernate.temp.use_jdbc_metadata_defaults";

	@Value("${baseball.datasource.data-source-properties.currentSchema:public}")
	private String stadiumDefaultSchema;

	@Value("${baseball.jpa.hibernate.ddl-auto:none}")
	private String stadiumDdlAuto;

	@Bean
	@ConfigurationProperties("baseball.datasource")
	public DataSourceProperties stadiumDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@ConfigurationProperties("baseball.datasource.hikari")
	public DataSource stadiumDataSource() {
		DataSource dataSource = stadiumDataSourceProperties()
				.initializeDataSourceBuilder()
				.build();
		if (dataSource instanceof HikariDataSource hikariDataSource) {
			hikariDataSource.setMinimumIdle(0);
			hikariDataSource.setInitializationFailTimeout(-1);
			hikariDataSource.setConnectionTimeout(3000);
			hikariDataSource.setValidationTimeout(2000);
			hikariDataSource.setKeepaliveTime(45000);
		}
		return dataSource;
	}

	@Bean
	public LocalContainerEntityManagerFactoryBean stadiumEntityManagerFactory(
			EntityManagerFactoryBuilder builder) {
		Map<String, Object> jpaProperties = new LinkedHashMap<>();
		jpaProperties.put(HIBERNATE_DEFAULT_SCHEMA, stadiumDefaultSchema);
		jpaProperties.put(HIBERNATE_HBM2DDL_AUTO, stadiumDdlAuto);
		jpaProperties.put(HIBERNATE_DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
		jpaProperties.put(HIBERNATE_ALLOW_METADATA, false);
		jpaProperties.put(HIBERNATE_METADATA_DEFAULTS, false);

		return builder
				.dataSource(stadiumDataSource())
				.packages("com.example.stadium.entity")
				.persistenceUnit("stadium")
				.properties(jpaProperties)
				.build();
	}

	@Bean
	public PlatformTransactionManager stadiumTransactionManager(
			LocalContainerEntityManagerFactoryBean stadiumEntityManagerFactory) {
		return new JpaTransactionManager(stadiumEntityManagerFactory.getObject());
	}
}
