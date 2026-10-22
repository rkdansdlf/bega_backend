package com.example.admin.controller;

import com.example.admin.dto.MateLinkReconciliationCasePageDto;
import com.example.admin.dto.PayoutHoldDto;
import com.example.admin.dto.PayoutHoldPageDto;
import com.example.admin.service.MateLinkReconciliationCaseAdminService;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.mate.service.TossPaymentService;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:admin_mate_link_reconciliation_auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.jpa.open-in-view=false",
        "jobrunr.background-job-server.enabled=false",
        "jobrunr.dashboard.enabled=false",
        "storage.type=oci",
        "oci.s3.endpoint=http://localhost:4566",
        "oci.s3.access-key=test-access-key",
        "oci.s3.secret-key=test-secret-key",
        "oci.s3.bucket=test-bucket",
        "oci.s3.region=ap-seoul-1"
})
class AdminMateLinkReconciliationCaseAuthorizationIntegrationTest {

    private static final String ENDPOINT = "/api/admin/mate-link-reconciliation-cases";
    private static final String PAYOUT_HOLDS_ENDPOINT = ENDPOINT + "/payout-holds";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MateLinkReconciliationCaseAdminService adminService;

    @MockitoBean
    private TossPaymentService tossPaymentService;

    @MockitoBean
    private JobScheduler jobScheduler;

    @MockitoBean
    private CheerPostRepo cheerPostRepo;

    @MockitoBean
    private CheerReportRepo cheerReportRepo;

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadReconciliationCases() throws Exception {
        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isForbidden());

        verify(adminService, never()).list(0, 20);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadPagedReconciliationCases() throws Exception {
        given(adminService.list(2, 25))
                .willReturn(new MateLinkReconciliationCasePageDto(java.util.List.of(), 2, 25, 0, 0));

        mockMvc.perform(get(ENDPOINT).param("page", "2").param("size", "25"))
                .andExpect(status().isOk());

        verify(adminService).list(2, 25);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanReadReconciliationCases() throws Exception {
        given(adminService.list(0, 20))
                .willReturn(new MateLinkReconciliationCasePageDto(java.util.List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(ENDPOINT))
                .andExpect(status().isOk());

        verify(adminService).list(0, 20);
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotReadPayoutHolds() throws Exception {
        mockMvc.perform(get(PAYOUT_HOLDS_ENDPOINT))
                .andExpect(status().isForbidden());

        verify(adminService, never()).listPayoutHolds(0, 20);
    }

    @Test
    void anonymousUserCannotReadPayoutHolds() throws Exception {
        mockMvc.perform(get(PAYOUT_HOLDS_ENDPOINT))
                .andExpect(status().isUnauthorized());

        verify(adminService, never()).listPayoutHolds(0, 20);
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanReadPayoutHoldsWithoutProviderOrPersonalFields() throws Exception {
        given(adminService.listPayoutHolds(0, 20))
                .willReturn(new PayoutHoldPageDto(
                        java.util.List.of(new PayoutHoldDto(
                                81L,
                                91L,
                                com.example.mate.entity.SettlementStatus.REQUESTED,
                                "PAYOUT_MANUAL_RECONCILIATION_REQUIRED",
                                java.time.Instant.parse("2026-10-19T00:00:00Z"))),
                        0,
                        20,
                        1,
                        1));

        mockMvc.perform(get(PAYOUT_HOLDS_ENDPOINT))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.items[0].payoutId").value(81))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.items[0].paymentTransactionId").value(91))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.items[0].status").value("REQUESTED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.items[0].reasonCode").value("PAYOUT_MANUAL_RECONCILIATION_REQUIRED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.items[0].updatedAt").value("2026-10-19T00:00:00Z"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.items[0].providerRef").doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                        "$.data.items[0].sellerId").doesNotExist());

        verify(adminService).listPayoutHolds(0, 20);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdminCanReadPayoutHolds() throws Exception {
        given(adminService.listPayoutHolds(0, 20))
                .willReturn(new PayoutHoldPageDto(java.util.List.of(), 0, 20, 0, 0));

        mockMvc.perform(get(PAYOUT_HOLDS_ENDPOINT))
                .andExpect(status().isOk());

        verify(adminService).listPayoutHolds(0, 20);
    }

    @Test
    void reconciliationCaseControllerDeclaresNoWriteMappings() {
        assertThat(Arrays.stream(AdminMateLinkReconciliationCaseController.class.getDeclaredMethods())
                .noneMatch(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(PatchMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class)))
                .isTrue();
    }
}
