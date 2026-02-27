package com.example.kbo.config;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GameSummaryEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * KBO game-read stack(PostgreSQL) 전용 JPA 구성.
 *
 * 분리 대상은 GameRepository 계열(경기/메타/이닝/요약)만 우선 적용한다.
 * 기본 도메인(auth/prediction write 등)은 primary datasource 경로를 유지한다.
 */
@Slf4j
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

	private static final String CURRENT_SCHEMA_SQL = "SELECT current_schema()";
	private static final String GAME_TABLE = "game";
	private static final String GAME_METADATA_TABLE = "game_metadata";
	private static final String GAME_SUMMARY_TABLE = "game_summary";
	private static final String GAME_INNING_SCORES_TABLE = "game_inning_scores";
	private static final String IS_DUMMY_COLUMN = "is_dummy";
	private static final String IS_EXTRA_COLUMN = "is_extra";

	private static final String CHECK_TABLE_SQL = """
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = ?
			  AND table_name = ?
			""";

	private static final String CHECK_COLUMN_SQL = """
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = ?
			  AND table_name = ?
			  AND column_name = ?
			""";

	private static final String FIND_COLUMN_TYPE_SQL = """
			SELECT data_type
			FROM information_schema.columns
			WHERE table_schema = ?
			  AND table_name = ?
			  AND column_name = ?
			""";

	private static final String PUBLIC_SCHEMA = "public";

	@Value("${kbo.schema-guard.strict:true}")
	private boolean strictSchemaGuard;

	@Bean
	public LocalContainerEntityManagerFactoryBean kboGameEntityManagerFactory(
			EntityManagerFactoryBuilder builder,
			@Qualifier("stadiumDataSource") DataSource stadiumDataSource) {
		String kboGameSchema = ensureKboGameSchema(stadiumDataSource);
		Map<String, Object> jpaProperties = new HashMap<>();
		jpaProperties.put("hibernate.default_schema", kboGameSchema);
		PersistenceManagedTypes managedTypes = PersistenceManagedTypes.of(
				List.of(
						GameEntity.class.getName(),
						GameMetadataEntity.class.getName(),
						GameSummaryEntity.class.getName(),
						GameInningScoreEntity.class.getName()
				),
				List.of()
		);

		return builder
				.dataSource(stadiumDataSource)
				.managedTypes(managedTypes)
				.persistenceUnit("kboGame")
				.properties(jpaProperties)
				.build();
	}

	@Bean
	public PlatformTransactionManager kboGameTransactionManager(
			@Qualifier("kboGameEntityManagerFactory") LocalContainerEntityManagerFactoryBean kboGameEntityManagerFactory) {
		return new JpaTransactionManager(kboGameEntityManagerFactory.getObject());
	}

	private String ensureKboGameSchema(DataSource stadiumDataSource) {
		JdbcTemplate jdbcTemplate = new JdbcTemplate(stadiumDataSource);
		String activeSchema = resolveActiveSchema(jdbcTemplate);
		if (!strictSchemaGuard) {
			String schema = countTable(jdbcTemplate, PUBLIC_SCHEMA, GAME_TABLE) > 0 ? PUBLIC_SCHEMA : activeSchema;
			log.info("Schema guard strict mode is disabled. Skipping kboGame schema validation/DDL; using schema={}", schema);
			return schema;
		}

		String metadataSchema = resolveSchemaForTable(jdbcTemplate, activeSchema, GAME_METADATA_TABLE);
		String summarySchema = resolveSchemaForTable(jdbcTemplate, activeSchema, GAME_SUMMARY_TABLE);
		String gameSchema = resolveSchemaForTable(jdbcTemplate, activeSchema, GAME_TABLE);
		String inningSchema = resolveSchemaForTable(jdbcTemplate, activeSchema, GAME_INNING_SCORES_TABLE);

		if (!gameSchema.equals(metadataSchema) || !gameSchema.equals(summarySchema) || !gameSchema.equals(inningSchema)) {
			throw new IllegalStateException(
					"[Schema Guard] kboGame tables are split across schemas. game=%s, game_metadata=%s, game_summary=%s, game_inning_scores=%s"
							.formatted(gameSchema, metadataSchema, summarySchema, inningSchema));
		}

		validateBooleanColumnType(jdbcTemplate, gameSchema, GAME_TABLE, IS_DUMMY_COLUMN);
		validateBooleanColumnType(jdbcTemplate, gameSchema, GAME_INNING_SCORES_TABLE, IS_EXTRA_COLUMN);

		return gameSchema;
	}

	private String resolveActiveSchema(JdbcTemplate jdbcTemplate) {
		String activeSchema = jdbcTemplate.queryForObject(CURRENT_SCHEMA_SQL, String.class);
		if (activeSchema == null || activeSchema.isBlank()) {
			return PUBLIC_SCHEMA;
		}
		return activeSchema;
	}

	private String resolveSchemaForTable(JdbcTemplate jdbcTemplate, String activeSchema, String tableName) {
		int publicCount = countTable(jdbcTemplate, PUBLIC_SCHEMA, tableName);
		int activeCount = countTable(jdbcTemplate, activeSchema, tableName);

		// 운영 표준은 public 스키마를 우선 사용한다.
		if (publicCount > 0) {
			if (!PUBLIC_SCHEMA.equals(activeSchema) && activeCount > 0) {
				log.warn(
						"Schema guard: {} exists in both active schema ({}) and public; using public as canonical",
						tableName,
						activeSchema
				);
			}
			return PUBLIC_SCHEMA;
		}
		if (activeCount > 0) {
			return activeSchema;
		}
		throw new IllegalStateException(
				"[Schema Guard] %s table not found in both active schema(%s) and public"
						.formatted(tableName, activeSchema));
	}

	private int countTable(JdbcTemplate jdbcTemplate, String schema, String tableName) {
		Integer count = jdbcTemplate.queryForObject(CHECK_TABLE_SQL, Integer.class, schema, tableName);
		return count == null ? 0 : count;
	}

	private int countColumn(JdbcTemplate jdbcTemplate, String schema, String tableName, String columnName) {
		Integer count = jdbcTemplate.queryForObject(CHECK_COLUMN_SQL, Integer.class, schema, tableName, columnName);
		return count == null ? 0 : count;
	}

	private void validateBooleanColumnType(
			JdbcTemplate jdbcTemplate,
			String schema,
			String tableName,
			String columnName
	) {
		if (countColumn(jdbcTemplate, schema, tableName, columnName) == 0) {
			throw new IllegalStateException(
					"[Schema Guard] missing column: %s.%s.%s".formatted(schema, tableName, columnName));
		}

		String dataType = jdbcTemplate.queryForObject(FIND_COLUMN_TYPE_SQL, String.class, schema, tableName, columnName);
		if (dataType == null || dataType.isBlank()) {
			throw new IllegalStateException(
					"[Schema Guard] unable to resolve column type: %s.%s.%s".formatted(schema, tableName, columnName));
		}
		if (!"boolean".equalsIgnoreCase(dataType)) {
			throw new IllegalStateException(
					"[Schema Guard] invalid column type for %s.%s.%s. expected=boolean, actual=%s"
							.formatted(schema, tableName, columnName, dataType));
		}
	}
}
