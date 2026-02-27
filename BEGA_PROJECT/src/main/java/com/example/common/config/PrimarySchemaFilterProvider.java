package com.example.common.config;

import java.util.Locale;
import java.util.Set;

import org.hibernate.boot.model.relational.Namespace;
import org.hibernate.boot.model.relational.Sequence;
import org.hibernate.mapping.Table;
import org.hibernate.tool.schema.spi.SchemaFilter;
import org.hibernate.tool.schema.spi.SchemaFilterProvider;

/**
 * primary(Oracle) validation에서 postgres 전용 game-read 테이블을 제외한다.
 *
 * 동일 엔티티를 dual DB에서 공유할 때 발생하는 타입 불일치(oracle number vs postgres boolean)
 * 를 validation 단계에서 분리해, 실제 사용 persistence unit(kboGame)에서 검증하도록 한다.
 */
public class PrimarySchemaFilterProvider implements SchemaFilterProvider {

    private static final Set<String> EXCLUDED_VALIDATE_TABLES = Set.of(
            "game",
            "game_metadata",
            "game_summary",
            "game_inning_scores"
    );

    private static final SchemaFilter VALIDATE_FILTER = new SchemaFilter() {
        @Override
        public boolean includeNamespace(Namespace namespace) {
            return true;
        }

        @Override
        public boolean includeTable(Table table) {
            String tableName = table == null ? null : table.getName();
            if (tableName == null || tableName.isBlank()) {
                return true;
            }
            return !EXCLUDED_VALIDATE_TABLES.contains(tableName.toLowerCase(Locale.ROOT));
        }

        @Override
        public boolean includeSequence(Sequence sequence) {
            return true;
        }
    };

    @Override
    public SchemaFilter getCreateFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getDropFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getTruncatorFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getMigrateFilter() {
        return SchemaFilter.ALL;
    }

    @Override
    public SchemaFilter getValidateFilter() {
        return VALIDATE_FILTER;
    }
}
