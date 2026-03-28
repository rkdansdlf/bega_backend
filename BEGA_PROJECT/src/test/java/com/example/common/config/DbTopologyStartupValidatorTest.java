package com.example.common.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DbTopologyStartupValidator tests")
class DbTopologyStartupValidatorTest {

    @Test
    @DisplayName("prod topology accepts Oracle primary and PostgreSQL baseball datasource")
    void validate_acceptsOraclePrimaryAndPostgresBaseball() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("BASEBALL_DB_URL", "jdbc:postgresql://postgres.example.com:5432/baseball")
                .withProperty("BASEBALL_DB_USERNAME", "postgres")
                .withProperty("BASEBALL_DB_PASSWORD", "secret");
        DataSourceProperties primary = new DataSourceProperties();
        primary.setDriverClassName("oracle.jdbc.OracleDriver");
        primary.setUrl("jdbc:oracle:thin:@//oracle.example.com:1521/begadb");

        DataSourceProperties baseball = new DataSourceProperties();
        baseball.setDriverClassName("org.postgresql.Driver");
        baseball.setUrl("jdbc:postgresql://postgres.example.com:5432/baseball");
        baseball.setUsername("postgres");
        baseball.setPassword("secret");

        DbTopologyStartupValidator validator = new DbTopologyStartupValidator(environment, primary, baseball);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("prod topology rejects PostgreSQL primary datasource")
    void validate_rejectsPostgresPrimaryDatasource() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("BASEBALL_DB_URL", "jdbc:postgresql://postgres.example.com:5432/baseball")
                .withProperty("BASEBALL_DB_USERNAME", "postgres")
                .withProperty("BASEBALL_DB_PASSWORD", "secret");
        DataSourceProperties primary = new DataSourceProperties();
        primary.setDriverClassName("org.postgresql.Driver");
        primary.setUrl("jdbc:postgresql://postgres.example.com:5432/app");

        DataSourceProperties baseball = new DataSourceProperties();
        baseball.setDriverClassName("org.postgresql.Driver");
        baseball.setUrl("jdbc:postgresql://postgres.example.com:5432/baseball");
        baseball.setUsername("postgres");
        baseball.setPassword("secret");

        DbTopologyStartupValidator validator = new DbTopologyStartupValidator(environment, primary, baseball);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primary datasource driver must be Oracle")
                .hasMessageContaining("primary datasource url must be Oracle JDBC");
    }
}
