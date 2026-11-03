package com.example.admin.controller;

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
import java.util.List;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/admin/reports 와 /{id} 의 접근 제어와 page/size 경계를 고정한다.
 * 비인증은 401, 일반 사용자는 403, ADMIN·SUPER_ADMIN 만 통과해야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.profiles.active=test",
        "spring.jwt.secret=test-jwt-secret-64-characters-long-for-hs512-signature-tests-key-1234567890",
        "spring.jwt.refresh-expiration=86400000",
        "spring.datasource.url=jdbc:h2:mem:admin_report_list_auth;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
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
class AdminReportListAuthorizationIntegrationTest {

    private static final String LIST = "/api/admin/reports";
    private static final String DETAIL = "/api/admin/reports/1";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AdminService adminService;
    @MockitoBean private SeatViewService seatViewService;
    @MockitoBean private TossPaymentService tossPaymentService;
    @MockitoBean private JobScheduler jobScheduler;
    @MockitoBean private CheerPostRepo cheerPostRepo;
    @MockitoBean private CheerReportRepo cheerReportRepo;
    @MockitoBean private MediaUploadService mediaUploadService;

    private void stubService() {
        given(adminService.getReports(isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .willReturn(new PageImpl<>(List.<AdminReportDto>of(), PageRequest.of(0, 20), 0));
        given(adminService.getReport(eq(1L))).willReturn(AdminReportDto.builder().id(1L).build());
    }

    @Test
    void anonymousRequestsAreRejectedWith401() throws Exception {
        mockMvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(DETAIL)).andExpect(status().isUnauthorized());

        verify(adminService, never()).getReports(any(), any(), any(), any(), anyInt(), anyInt());
        verify(adminService, never()).getReport(any());
    }

    @Test
    @WithMockUser(roles = "USER")
    void regularUserRequestsAreRejectedWith403() throws Exception {
        mockMvc.perform(get(LIST)).andExpect(status().isForbidden());
        mockMvc.perform(get(DETAIL)).andExpect(status().isForbidden());

        verify(adminService, never()).getReports(any(), any(), any(), any(), anyInt(), anyInt());
        verify(adminService, never()).getReport(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void administratorCanListAndReadReports() throws Exception {
        stubService();

        mockMvc.perform(get(LIST)).andExpect(status().isOk());
        mockMvc.perform(get(DETAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    @WithMockUser(roles = "SUPER_ADMIN")
    void superAdministratorInheritsAdminAccess() throws Exception {
        stubService();

        mockMvc.perform(get(LIST)).andExpect(status().isOk());
        mockMvc.perform(get(DETAIL)).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void pageSizeIsCappedAt100AndPageMustNotBeNegative() throws Exception {
        stubService();

        mockMvc.perform(get(LIST).param("size", "100")).andExpect(status().isOk());
        verify(adminService).getReports(isNull(), isNull(), isNull(), isNull(), eq(0), eq(100));

        mockMvc.perform(get(LIST).param("size", "101")).andExpect(status().isBadRequest());
        mockMvc.perform(get(LIST).param("size", "0")).andExpect(status().isBadRequest());
        mockMvc.perform(get(LIST).param("page", "-1")).andExpect(status().isBadRequest());

        verify(adminService, never()).getReports(any(), any(), any(), any(), anyInt(), eq(101));
    }
}
