package com.example.cheerboard.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class CheerRepostConstraintDetectorTest {

    @Test
    void detectsSimpleRepostDuplicateConstraintMessages() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "duplicate",
                new SQLException("duplicate key value violates constraint uq_cheer_post_simple_repost"));

        assertThat(CheerRepostConstraintDetector.isDuplicateViolation(exception)).isTrue();
    }

    @Test
    void ignoresUnrelatedIntegrityViolations() {
        DataIntegrityViolationException exception = new DataIntegrityViolationException(
                "foreign key",
                new SQLException("insert violates foreign key constraint fk_cheer_post_author"));

        assertThat(CheerRepostConstraintDetector.isDuplicateViolation(exception)).isFalse();
    }
}
