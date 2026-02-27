package com.example.common.config;

import java.sql.Types;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.core.env.Environment;
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

	private final Environment environment;

	public PrimaryOracleJpaConfig(Environment environment) {
		this.environment = environment;
	}

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource")
	public DataSourceProperties primaryDataSourceProperties() {
		return new DataSourceProperties();
	}

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource.data-source-properties")
	public Map<String, String> primaryDataSourceJdbcProperties() {
		return new LinkedHashMap<>();
	}

	@Bean
	@Primary
	@ConfigurationProperties("spring.datasource.hikari")
	public HikariConfig primaryHikariConfig(
			@Qualifier("primaryDataSourceProperties") DataSourceProperties primaryProperties,
			@Qualifier("primaryDataSourceJdbcProperties") Map<String, String> jdbcProperties
	) {
		validateOracleWalletIfNeeded(primaryProperties.getUrl());
		HikariConfig config = new HikariConfig();
		config.setJdbcUrl(primaryProperties.getUrl());
		config.setUsername(primaryProperties.getUsername());
		config.setPassword(primaryProperties.getPassword());
		if (primaryProperties.getDriverClassName() != null && !primaryProperties.getDriverClassName().isBlank()) {
			config.setDriverClassName(primaryProperties.getDriverClassName());
		}
		for (Map.Entry<String, String> entry : jdbcProperties.entrySet()) {
			config.addDataSourceProperty(entry.getKey(), entry.getValue());
		}
		return config;
	}

	@Bean
	@Primary
	public HikariDataSource primaryDataSource(
			@Qualifier("primaryHikariConfig") HikariConfig primaryHikariConfig
	) {
		return new HikariDataSource(primaryHikariConfig);
	}

	@Bean
	@Primary
	public LocalContainerEntityManagerFactoryBean entityManagerFactory(
			EntityManagerFactoryBuilder builder,
			DataSource primaryDataSource,
			@Qualifier("primaryDataSourceProperties") DataSourceProperties primaryProperties) {
		Map<String, Object> jpaProperties = new LinkedHashMap<>();
			jpaProperties.put("hibernate.hbm2ddl.schema_filter_provider", PrimarySchemaFilterProvider.class.getName());
			if (isOracleJdbcUrl(primaryProperties.getUrl())) {
				jpaProperties.put("hibernate.type.preferred_boolean_jdbc_type", Types.INTEGER);
				jpaProperties.put("hibernate.type.preferred_json_jdbc_type", Types.CLOB);
				jpaProperties.put("hibernate.type.preferred_uuid_jdbc_type", Types.VARCHAR);
			}

		return builder
				.dataSource(primaryDataSource)
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
				.properties(jpaProperties)
				.build();
	}

	@Bean
	@Primary
	public PlatformTransactionManager transactionManager(
			LocalContainerEntityManagerFactoryBean entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory.getObject());
	}

	private void validateOracleWalletIfNeeded(String datasourceUrl) {
		if (!isOracleWithTnsAlias(datasourceUrl)) {
			return;
		}

		String tnsAdmin = extractTnsAdmin(datasourceUrl);
		if (isBlank(tnsAdmin)) {
			tnsAdmin = environment.getProperty("TNS_ADMIN");
		}
		if (isBlank(tnsAdmin)) {
			tnsAdmin = environment.getProperty("ORACLE_TNS_ADMIN");
		}

		if (isBlank(tnsAdmin)) {
			throw new IllegalStateException("Oracle TNS alias is used, but TNS admin path is missing. Set SPRING_DATASOURCE_URL with full host/port/service OR configure TNS_ADMIN.");
		}

		Path walletDir = Paths.get(tnsAdmin);
		if (!Files.exists(walletDir) || !Files.isDirectory(walletDir)) {
			throw new IllegalStateException("Oracle wallet path is invalid or not a directory: " + tnsAdmin);
		}
		if (!Files.isReadable(walletDir)) {
			throw new IllegalStateException("Oracle wallet path is not readable: " + tnsAdmin);
		}
			boolean hasWalletFile =
					Files.exists(walletDir.resolve("ewallet.p12"))
							|| Files.exists(walletDir.resolve("cwallet.sso"));
			if (!Files.exists(walletDir.resolve("tnsnames.ora")) || !hasWalletFile) {
			throw new IllegalStateException("Oracle wallet files are incomplete at " + tnsAdmin + ". Expected tnsnames.ora and wallet file (ewallet.p12 or cwallet.sso).");
		}
	}

	private boolean isOracleWithTnsAlias(String datasourceUrl) {
		if (datasourceUrl == null || !datasourceUrl.toLowerCase().startsWith("jdbc:oracle:thin:")) {
			return false;
		}
		int atIndex = datasourceUrl.indexOf('@');
		if (atIndex < 0) {
			return false;
		}
		String target = datasourceUrl.substring(atIndex + 1);
		int queryIndex = target.indexOf('?');
		if (queryIndex >= 0) {
			target = target.substring(0, queryIndex);
		}
		return !target.contains("//") && !target.contains("/") && !target.contains(":");
	}

	private String extractTnsAdmin(String datasourceUrl) {
		if (datasourceUrl == null) {
			return "";
		}
		int questionIndex = datasourceUrl.indexOf('?');
		if (questionIndex < 0) {
			return "";
		}
		String query = datasourceUrl.substring(questionIndex + 1);
		for (String entry : query.split("&")) {
			int eq = entry.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			if ("TNS_ADMIN".equalsIgnoreCase(entry.substring(0, eq).trim())) {
				return entry.substring(eq + 1).trim();
			}
		}
		return "";
	}

	private boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	private boolean isOracleJdbcUrl(String datasourceUrl) {
		return datasourceUrl != null && datasourceUrl.toLowerCase().startsWith("jdbc:oracle:");
	}
}
