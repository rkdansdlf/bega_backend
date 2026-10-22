package com.example.admin.controller;

import com.example.admin.dto.AdminReportAppealDecision;
import com.example.admin.dto.AdminReportDto;
import com.example.admin.service.AdminService;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.media.service.MediaUploadService;
import com.example.BegaDiary.Service.SeatViewService;
import com.example.mate.service.TossPaymentService;
import org.jobrunr.scheduling.JobScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:admin_report_appeal_auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class AdminReportAppealDecisionAuthorizationIntegrationTest {

    private static final String ENDPOINT = "/api/admin/reports/1/appeal";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AdminService adminService;
    @MockitoBean private SeatViewService seatViewService;
    @MockitoBean private TossPaymentService tossPaymentService;
    @MockitoBean private JobScheduler jobScheduler;
    @MockitoBean private CheerPostRepo cheerPostRepo;
    @MockitoBean private CheerReportRepo cheerReportRepo;
    @MockitoBean private MediaUploadService mediaUploadService;

    @Test
    @WithMockUser(roles = "USER")
    void regularUserCannotDecideAnAppeal() throws Exception {
        mockMvc.perform(patch(ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isForbidden());

        verify(adminService, never()).decideReportAppeal(eq(1L), any(), isNull());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanDecideAnAppeal() throws Exception {
        given(adminService.decideReportAppeal(eq(1L), any(), isNull()))
                .willReturn(AdminReportDto.builder().id(1L).appealStatus("ACCEPTED").build());

        mockMvc.perform(patch(ENDPOINT)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"ACCEPTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appealStatus").value("ACCEPTED"));

        verify(adminService).decideReportAppeal(
                eq(1L),
                org.mockito.ArgumentMatchers.argThat(req -> req.decision() == AdminReportAppealDecision.ACCEPTED),
                isNull());
    }
}
