package com.example.kbo.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.example.kbo.dto.TeamDiagnosticsDto;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamDiagnosticsService {

    private static final List<String> CANONICAL_CODES = List.of(
            "SS", "LT", "LG", "OB", "HT", "WO", "HH", "SSG", "NC", "KT");

    private final JdbcTemplate jdbcTemplate;

    public TeamDiagnosticsDto getDiagnostics() {
        return new TeamDiagnosticsDto(
                CANONICAL_CODES,
                queryNonCanonical("home_team", false),
                queryNonCanonical("away_team", false),
                queryNonCanonical("winning_team", true)
        );
    }

    private List<TeamDiagnosticsDto.CodeCount> queryNonCanonical(String column, boolean excludeNull) {
        String inClause = CANONICAL_CODES.stream()
                .map(code -> "'" + code + "'")
                .collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ")
                .append(column)
                .append(" AS code, COUNT(*) AS cnt ")
                .append("FROM game WHERE ");

        if (excludeNull) {
            sql.append(column).append(" IS NOT NULL AND ");
        }

        sql.append(column)
                .append(" NOT IN (")
                .append(inClause)
                .append(") ")
                .append("GROUP BY ")
                .append(column)
                .append(" ORDER BY cnt DESC");

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) ->
                new TeamDiagnosticsDto.CodeCount(
                        rs.getString("code"),
                        rs.getLong("cnt")
                ));
    }
}
