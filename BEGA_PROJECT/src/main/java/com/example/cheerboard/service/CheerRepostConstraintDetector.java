package com.example.cheerboard.service;

import org.springframework.dao.DataIntegrityViolationException;

public final class CheerRepostConstraintDetector {

    private CheerRepostConstraintDetector() {
    }

    public static boolean isDuplicateViolation(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        return isDuplicateViolation(message);
    }

    public static boolean isDuplicateViolation(String message) {
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase();
        return lower.contains("uq_cheer_post_simple_repost")
                || (lower.contains("duplicate key") && lower.contains("repost_type") && lower.contains("repost_of_id"))
                || (lower.contains("repost_of_id") && lower.contains("repost_type"))
                || (lower.contains("cheer_post_repost") && lower.contains("duplicate key"))
                || lower.contains("cheer_post_repost_pkey");
    }
}
