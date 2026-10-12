package com.example.admin.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPaymentIntentReconciliationControllerSecurityTest {

    @Test
    void allReconciliationReviewAndResolutionRoutesRequireAdminRole() {
        PreAuthorize guard = AdminPaymentIntentReconciliationController.class.getAnnotation(PreAuthorize.class);

        assertThat(guard).isNotNull();
        assertThat(guard.value()).isEqualTo("hasRole('ADMIN')");
    }
}
