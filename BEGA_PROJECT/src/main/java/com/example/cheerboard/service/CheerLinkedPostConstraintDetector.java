package com.example.cheerboard.service;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class CheerLinkedPostConstraintDetector {

    private static final String ACTIVE_DIARY_CONSTRAINT = "uq_cheer_post_active_diary";
    private static final String ACTIVE_PARTY_CONSTRAINT = "uq_cheer_post_active_party";
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z0-9_$#]+");

    private CheerLinkedPostConstraintDetector() {
    }

    static boolean isActiveDiaryConflict(DataIntegrityViolationException exception) {
        return causeIdentifiesConstraint(exception, ACTIVE_DIARY_CONSTRAINT);
    }

    static boolean isActivePartyConflict(DataIntegrityViolationException exception) {
        return causeIdentifiesConstraint(exception, ACTIVE_PARTY_CONSTRAINT);
    }

    private static boolean causeIdentifiesConstraint(Throwable throwable, String expectedConstraint) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable.getCause();
        while (current != null && visited.add(current)) {
            if (current instanceof ConstraintViolationException violation
                    && containsExactIdentifier(violation.getConstraintName(), expectedConstraint)) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && containsExactIdentifier(sqlException.getMessage(), expectedConstraint)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean containsExactIdentifier(String value, String expectedConstraint) {
        if (value == null) {
            return false;
        }
        Matcher matcher = SQL_IDENTIFIER.matcher(value);
        while (matcher.find()) {
            if (expectedConstraint.equalsIgnoreCase(matcher.group())) {
                return true;
            }
        }
        return false;
    }
}
