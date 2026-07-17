package com.example.cheerboard;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class CheerLinkedPostMigrationSqlTest {

    private static final String LINK_TYPE_CHECK = "(posttype in ('normal', 'notice') "
            + "and diary_id is null and party_id is null) "
            + "or (posttype = 'checkin' and party_id is null) "
            + "or (posttype = 'recruitment' and diary_id is null)";

    @Test
    @DisplayName("PostgreSQL migration defines linked cheer post columns, constraints, and indexes")
    void postgresMigrationDefinesLinkedPostSchema() throws IOException {
        String postgresSql = normalizeSql(loadSql(
                "db/migration_postgresql/V173__add_cheer_linked_post_types.sql"));

        assertThat(postgresSql)
                .contains("alter table cheer_post add column if not exists diary_id bigint;")
                .contains("alter table cheer_post add column if not exists party_id bigint;")
                .contains("if not exists (select 1 from pg_constraint "
                        + "where conname = 'fk_cheer_post_diary' and conrelid = 'cheer_post'::regclass) then "
                        + "alter table cheer_post add constraint fk_cheer_post_diary "
                        + "foreign key (diary_id) references bega_diary(id) on delete set null; end if;")
                .contains("if not exists (select 1 from pg_constraint "
                        + "where conname = 'fk_cheer_post_party' and conrelid = 'cheer_post'::regclass) then "
                        + "alter table cheer_post add constraint fk_cheer_post_party "
                        + "foreign key (party_id) references parties(id) on delete set null; end if;")
                .contains("if not exists (select 1 from pg_constraint "
                        + "where conname = 'ck_cheer_post_link_type' and conrelid = 'cheer_post'::regclass) then "
                        + "alter table cheer_post add constraint ck_cheer_post_link_type check ("
                        + LINK_TYPE_CHECK + "); end if;")
                .contains("create index if not exists idx_cheer_post_diary on cheer_post (diary_id);")
                .contains("create index if not exists idx_cheer_post_party on cheer_post (party_id);")
                .contains("create unique index if not exists uq_cheer_post_active_diary "
                        + "on cheer_post (diary_id) where diary_id is not null and deleted = false;")
                .contains("create unique index if not exists uq_cheer_post_active_party "
                        + "on cheer_post (party_id) where party_id is not null and deleted = false;");

        assertThat(occurrencesOf(postgresSql, "add column if not exists")).isEqualTo(2);
        assertThat(occurrencesOf(postgresSql, "if not exists (select 1 from pg_constraint")).isEqualTo(3);
        assertThat(occurrencesOf(postgresSql, "and conrelid = 'cheer_post'::regclass")).isEqualTo(3);
        assertThat(occurrencesOf(postgresSql, "create index if not exists")).isEqualTo(2);
        assertThat(occurrencesOf(postgresSql, "create unique index if not exists")).isEqualTo(2);
    }

    @Test
    @DisplayName("Oracle migration defines linked cheer post columns, constraints, and indexes")
    void oracleMigrationDefinesLinkedPostSchema() throws IOException {
        String oracleSql = normalizeSql(loadSql(
                "db/migration/V167__add_cheer_linked_post_types.sql"));

        assertThat(oracleSql)
                .contains("e_column_exists exception;")
                .contains("pragma exception_init(e_column_exists, -1430);")
                .contains("execute immediate 'alter table cheer_post add (diary_id number(19))';")
                .contains("execute immediate 'alter table cheer_post add (party_id number(19))';")
                .contains("e_constraint_exists exception;")
                .contains("pragma exception_init(e_constraint_exists, -2264);")
                .contains("execute immediate 'alter table cheer_post add constraint fk_cheer_post_diary "
                        + "foreign key (diary_id) references bega_diary(id) on delete set null';")
                .contains("execute immediate 'alter table cheer_post add constraint fk_cheer_post_party "
                        + "foreign key (party_id) references parties(id) on delete set null';")
                .contains("execute immediate q'[ alter table cheer_post add constraint ck_cheer_post_link_type check ("
                        + LINK_TYPE_CHECK + ") ]';")
                .contains("e_index_exists exception;")
                .contains("pragma exception_init(e_index_exists, -955);")
                .contains("execute immediate 'create index idx_cheer_post_diary on cheer_post(diary_id)';")
                .contains("execute immediate 'create index idx_cheer_post_party on cheer_post(party_id)';")
                .contains("execute immediate 'create unique index uq_cheer_post_active_diary "
                        + "on cheer_post(case when nvl(deleted, 0) = 0 and diary_id is not null then diary_id end)';")
                .contains("execute immediate 'create unique index uq_cheer_post_active_party "
                        + "on cheer_post(case when nvl(deleted, 0) = 0 and party_id is not null then party_id end)';");

        assertThat(occurrencesOf(oracleSql, "when e_column_exists then null;")).isEqualTo(2);
        assertThat(occurrencesOf(oracleSql, "when e_constraint_exists then null;")).isEqualTo(3);
        assertThat(occurrencesOf(oracleSql, "when e_index_exists then null;")).isEqualTo(4);
        assertThat(occurrencesOf(oracleSql, "pragma exception_init(e_column_exists, -1430);")).isOne();
        assertThat(occurrencesOf(oracleSql, "pragma exception_init(e_constraint_exists, -2264);")).isOne();
        assertThat(occurrencesOf(oracleSql, "pragma exception_init(e_index_exists, -955);")).isOne();
    }

    private String normalizeSql(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .replaceAll("\\(\\s+", "(")
                .replaceAll("\\s+\\)", ")")
                .trim();
    }

    private int occurrencesOf(String sql, String clause) {
        return (sql.length() - sql.replace(clause, "").length()) / clause.length();
    }

    private String loadSql(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
