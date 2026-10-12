package com.example.admin.controller;

import com.example.admin.dto.PaymentIntentReconciliationPageDto;
import com.example.admin.service.PaymentIntentReconciliationAdminService;
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

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:admin_payment_reconciliation_auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class AdminPaymentIntentReconciliationAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentIntentReconciliationAdminService reconciliationAdminService;

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
    void regularUserCannotReadReconciliationQueue() throws Exception {
        mockMvc.perform(get("/api/admin/payment-intents/reconciliation"))
                .andExpect(status().isForbidden());

        verify(reconciliationAdminService, never()).listPending(0, 20);
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotSubmitReconciliationDecision() throws Exception {
        mockMvc.perform(post("/api/admin/payment-intents/reconciliation/410/resolution")
                        .contentType(APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        verify(reconciliationAdminService, never()).resolve(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanReadReconciliationQueue() throws Exception {
        given(reconciliationAdminService.listPending(0, 20))
                .willReturn(new PaymentIntentReconciliationPageDto(java.util.List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/payment-intents/reconciliation"))
                .andExpect(status().isOk());

        verify(reconciliationAdminService).listPending(0, 20);
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdministratorCanReadReconciliationQueue() throws Exception {
        given(reconciliationAdminService.listPending(0, 20))
                .willReturn(new PaymentIntentReconciliationPageDto(java.util.List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/admin/payment-intents/reconciliation"))
                .andExpect(status().isOk());

        verify(reconciliationAdminService).listPending(0, 20);
    }
}
