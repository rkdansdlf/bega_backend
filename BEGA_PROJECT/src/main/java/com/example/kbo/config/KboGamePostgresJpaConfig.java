package com.example.kbo.config;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.sql.DataSource;

import com.example.kbo.entity.GameEntity;
import com.example.kbo.entity.GameEventEntity;
import com.example.kbo.entity.GameInningScoreEntity;
import com.example.kbo.entity.GameMetadataEntity;
import com.example.kbo.entity.GamePlayByPlayEntity;
import com.example.kbo.entity.GameSummaryEntity;
import com.example.kbo.entity.PlayerSeasonBattingEntity;
import com.example.kbo.entity.PlayerSeasonPitchingEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * KBO game-read stack(PostgreSQL) 전용 JPA 구성.
 *
 * 분리 대상은 GameRepository 계열(경기/메타/이닝/요약)과 player season read-model이다.
 * 기본 도메인(auth/prediction write 등)은 primary datasource 경로를 유지한다.
 */
@Slf4j
@Configuration
@EnableTransactionManagement
@ConditionalOnProperty(name = "kbo.game.db.enabled", havingValue = "true", matchIfMissing = true)
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

	// information_schema.tables/columns and current_schema() are PostgreSQL-only —
	// Oracle (including 23ai ADB) has no information_schema compatibility layer for
	// either (confirmed live: ORA-00904 / ORA-00942). Every schema-guard query below
	// branches on isOracle() and uses the ALL_TABLES/ALL_TAB_COLUMNS + SYS_CONTEXT
	// equivalents instead. Unquoted identifiers are uppercase in Oracle's catalog, so
	// the Oracle branch upper-cases schema/table/column params before binding.
	private static final String CURRENT_SCHEMA_SQL = "SELECT current_schema()";
	private static final String CURRENT_SCHEMA_SQL_ORACLE =
			"SELECT SYS_CONTEXT('USERENV', 'CURRENT_SCHEMA') FROM DUAL";
	private static final String GAME_TABLE = "game";
	private static final String GAME_METADATA_TABLE = "game_metadata";
	private static final String GAME_SUMMARY_TABLE = "game_summary";
	private static final String GAME_INNING_SCORES_TABLE = "game_inning_scores";
	private static final String GAME_EVENTS_TABLE = "game_events";
	private static final String GAME_PLAY_BY_PLAY_TABLE = "game_play_by_play";
	private static final String IS_DUMMY_COLUMN = "is_dummy";
	private static final String IS_EXTRA_COLUMN = "is_extra";

	private static final String CHECK_TABLE_SQL = """
			SELECT COUNT(*)
			FROM information_schema.tables
			WHERE table_schema = ?
			  AND table_name = ?
			""";

	private static final String CHECK_TABLE_SQL_ORACLE = """
			SELECT COUNT(*)
			FROM all_tables
			WHERE owner = ?
			  AND table_name = ?
			""";

	private static final String CHECK_COLUMN_SQL = """
			SELECT COUNT(*)
			FROM information_schema.columns
			WHERE table_schema = ?
			  AND table_name = ?
			  AND column_name = ?
			""";

	private static final String CHECK_COLUMN_SQL_ORACLE = """
			SELECT COUNT(*)
			FROM all_tab_columns
			WHERE owner = ?
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

	private static final String FIND_COLUMN_TYPE_SQL_ORACLE = """
			SELECT data_type
			FROM all_tab_columns
			WHERE owner = ?
			  AND table_name = ?
			  AND column_name = ?
			""";

	private static final String PUBLIC_SCHEMA = "public";

	@Value("${kbo.schema-guard.strict:true}")
	private boolean strictSchemaGuard;

	// 야구 데이터소스가 Oracle(ADB)로 옮겨갈 수 있어 방언을 파라미터화한다.
	// StadiumPostgresJpaConfig 와 같은 키를 읽는다 — 두 퍼시스턴스 유닛이
	// 같은 stadiumDataSource 를 공유하므로 방언이 갈라지면 안 된다.
	@Value("${baseball.jpa.database-platform:org.hibernate.dialect.PostgreSQLDialect}")
	private String kboGameDialect;

	// ensureKboGameSchema 가 EMF 생성 중에 game 테이블 존재를 검증하므로,
	// 야구 Flyway 가 그보다 먼저 돌아야 한다. 빈 ADB 에서는 이 순서가 없으면
	// 스키마 가드가 먼저 실패한다. (baseballFlyway 는 기본 비활성이라 평소엔 no-op)
	@Bean
	@DependsOn("baseballFlyway")
	public LocalContainerEntityManagerFactoryBean kboGameEntityManagerFactory(
			EntityManagerFactoryBuilder builder,
			@Qualifier("stadiumDataSource") DataSource stadiumDataSource) {
		String kboGameSchema = ensureKboGameSchema(stadiumDataSource);
		Map<String, Object> jpaProperties = new HashMap<>();
		jpaProperties.put("hibernate.default_schema", kboGameSchema);
		// Metadata access is disabled, so keep an explicit dialect for stable test/CI boot.
		jpaProperties.put("hibernate.dialect", kboGameDialect);
		jpaProperties.put("hibernate.boot.allow_jdbc_metadata_access", false);
		jpaProperties.put("hibernate.temp.use_jdbc_metadata_defaults", false);
		jpaProperties.put("hibernate.hbm2ddl.auto", "none");
		PersistenceManagedTypes managedTypes = PersistenceManagedTypes.of(
				List.of(
						GameEntity.class.getName(),
						GameMetadataEntity.class.getName(),
						GameSummaryEntity.class.getName(),
						GameInningScoreEntity.class.getName(),
						GameEventEntity.class.getName(),
						GamePlayByPlayEntity.class.getName(),
						PlayerSeasonBattingEntity.class.getName(),
						PlayerSeasonPitchingEntity.class.getName()
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
		if (!strictSchemaGuard) {
			JdbcTemplate jdbcTemplate = new JdbcTemplate(stadiumDataSource);
			try {
				String activeSchema = resolveActiveSchema(jdbcTemplate);
				String schema = resolveRelaxedSchema(jdbcTemplate, activeSchema);
				log.info(
						"Schema guard strict mode is disabled. Skipping JDBC schema validation and using schema={} (active schema={})",
						schema,
						activeSchema);
				return schema;
			} catch (CannotGetJdbcConnectionException ex) {
				log.warn(
						"Schema guard strict mode is disabled, but active schema probe failed. Falling back to schema={}. reason={}",
						PUBLIC_SCHEMA,
						ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage());
				return PUBLIC_SCHEMA;
			}
		}
		JdbcTemplate jdbcTemplate = new JdbcTemplate(stadiumDataSource);
		final int maxAttempts = 6;
		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				return ensureKboGameSchemaInternal(jdbcTemplate);
			} catch (CannotGetJdbcConnectionException ex) {
				if (attempt == maxAttempts) {
					log.error(
							"Schema guard could not obtain JDBC connection after {} attempts. Falling back to '{}' for startup continuity.",
							maxAttempts,
							PUBLIC_SCHEMA,
							ex
					);
					return PUBLIC_SCHEMA;
				}
				long backoffMs = Math.min(3000L, attempt * 500L);
				String rootMessage = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
				log.warn(
						"Schema guard JDBC connection failed (attempt {}/{}). Retrying in {}ms. reason={}",
						attempt,
						maxAttempts,
						backoffMs,
						rootMessage
				);
				sleepQuietly(backoffMs);
			}
		}

		return PUBLIC_SCHEMA;
	}

	String resolveRelaxedSchema(JdbcTemplate jdbcTemplate, String activeSchema) {
		if (hasAllKboGameTables(jdbcTemplate, PUBLIC_SCHEMA)) {
			if (!PUBLIC_SCHEMA.equals(activeSchema) && hasAllKboGameTables(jdbcTemplate, activeSchema)) {
				log.warn(
						"Schema guard relaxed mode: kboGame tables exist in both active schema ({}) and public; using public as canonical",
						activeSchema
				);
			}
			return PUBLIC_SCHEMA;
		}
		if (hasAllKboGameTables(jdbcTemplate, activeSchema)) {
			return activeSchema;
		}
		if (countTable(jdbcTemplate, PUBLIC_SCHEMA, GAME_TABLE) > 0) {
			log.warn(
					"Schema guard relaxed mode: public has partial kboGame tables. Falling back to public for compatibility. active schema={}",
					activeSchema
			);
			return PUBLIC_SCHEMA;
		}
		return activeSchema;
	}

	private String ensureKboGameSchemaInternal(JdbcTemplate jdbcTemplate) {
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
		String eventSchema = resolveSchemaForTable(jdbcTemplate, activeSchema, GAME_EVENTS_TABLE);
		String playByPlaySchema = resolveSchemaForTable(jdbcTemplate, activeSchema, GAME_PLAY_BY_PLAY_TABLE);

		if (!gameSchema.equals(metadataSchema)
				|| !gameSchema.equals(summarySchema)
				|| !gameSchema.equals(inningSchema)
				|| !gameSchema.equals(eventSchema)
				|| !gameSchema.equals(playByPlaySchema)) {
			throw new IllegalStateException(
					"[Schema Guard] kboGame tables are split across schemas. game=%s, game_metadata=%s, game_summary=%s, game_inning_scores=%s, game_events=%s, game_play_by_play=%s"
							.formatted(gameSchema, metadataSchema, summarySchema, inningSchema, eventSchema, playByPlaySchema));
		}

		validateBooleanColumnType(jdbcTemplate, gameSchema, GAME_TABLE, IS_DUMMY_COLUMN);
		validateBooleanColumnType(jdbcTemplate, gameSchema, GAME_INNING_SCORES_TABLE, IS_EXTRA_COLUMN);

		return gameSchema;
	}

	private void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

	// baseball.jpa.database-platform 은 StadiumPostgresJpaConfig 와 같은 키를 읽고,
	// 이미 야구 데이터소스 전환의 신호로 쓰이고 있다(위 kboGameDialect 주석 참고) —
	// 여기서도 같은 값으로 방언을 판정해 별도 JDBC 라운드트립 없이 분기한다.
	private boolean isOracle() {
		return kboGameDialect != null && kboGameDialect.toLowerCase(Locale.ROOT).contains("oracle");
	}

	private String resolveActiveSchema(JdbcTemplate jdbcTemplate) {
		String sql = isOracle() ? CURRENT_SCHEMA_SQL_ORACLE : CURRENT_SCHEMA_SQL;
		String activeSchema = jdbcTemplate.queryForObject(sql, String.class);
		if (activeSchema == null || activeSchema.isBlank()) {
			return PUBLIC_SCHEMA;
		}
		return activeSchema;
	}

	private boolean hasAllKboGameTables(JdbcTemplate jdbcTemplate, String schema) {
		return countTable(jdbcTemplate, schema, GAME_TABLE) > 0
				&& countTable(jdbcTemplate, schema, GAME_METADATA_TABLE) > 0
				&& countTable(jdbcTemplate, schema, GAME_SUMMARY_TABLE) > 0
				&& countTable(jdbcTemplate, schema, GAME_INNING_SCORES_TABLE) > 0
				&& countTable(jdbcTemplate, schema, GAME_EVENTS_TABLE) > 0
				&& countTable(jdbcTemplate, schema, GAME_PLAY_BY_PLAY_TABLE) > 0;
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
		if (isOracle()) {
			Integer count = jdbcTemplate.queryForObject(
					CHECK_TABLE_SQL_ORACLE, Integer.class,
					toOracleIdentifier(schema), toOracleIdentifier(tableName));
			return count == null ? 0 : count;
		}
		Integer count = jdbcTemplate.queryForObject(CHECK_TABLE_SQL, Integer.class, schema, tableName);
		return count == null ? 0 : count;
	}

	private int countColumn(JdbcTemplate jdbcTemplate, String schema, String tableName, String columnName) {
		if (isOracle()) {
			Integer count = jdbcTemplate.queryForObject(
					CHECK_COLUMN_SQL_ORACLE, Integer.class,
					toOracleIdentifier(schema), toOracleIdentifier(tableName), toOracleIdentifier(columnName));
			return count == null ? 0 : count;
		}
		Integer count = jdbcTemplate.queryForObject(CHECK_COLUMN_SQL, Integer.class, schema, tableName, columnName);
		return count == null ? 0 : count;
	}

	// Oracle folds unquoted identifiers to uppercase at DDL time, and ALL_TABLES /
	// ALL_TAB_COLUMNS store them that way — every migration in db/migration_baseball_oracle/
	// and the crawler's SQLAlchemy DDL use unquoted (lowercase-in-source) identifiers, so
	// this holds for every table/column this guard checks.
	private static String toOracleIdentifier(String identifier) {
		return identifier.toUpperCase(Locale.ROOT);
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

		boolean oracle = isOracle();
		String dataType = oracle
				? jdbcTemplate.queryForObject(
						FIND_COLUMN_TYPE_SQL_ORACLE, String.class,
						toOracleIdentifier(schema), toOracleIdentifier(tableName), toOracleIdentifier(columnName))
				: jdbcTemplate.queryForObject(FIND_COLUMN_TYPE_SQL, String.class, schema, tableName, columnName);
		if (dataType == null || dataType.isBlank()) {
			throw new IllegalStateException(
					"[Schema Guard] unable to resolve column type: %s.%s.%s".formatted(schema, tableName, columnName));
		}
		// Oracle has no boolean column type here — game/game_inning_scores are the
		// crawler's tables (game_inning_scores) or ours via
		// db/migration_baseball_oracle/V2 (game.is_dummy), and both encode this as
		// NUMBER(1). PostgreSQL keeps the real boolean check.
		boolean validType = oracle
				? "NUMBER".equalsIgnoreCase(dataType)
				: "boolean".equalsIgnoreCase(dataType);
		if (!validType) {
			throw new IllegalStateException(
					"[Schema Guard] invalid column type for %s.%s.%s. expected=%s, actual=%s"
							.formatted(schema, tableName, columnName, oracle ? "NUMBER" : "boolean", dataType));
		}
	}
}
