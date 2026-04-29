package com.example.admin.controller;

import com.example.admin.dto.AuditLogDto;
import com.example.admin.dto.RoleChangeRequestDto;
import com.example.admin.dto.RoleChangeResponseDto;
import com.example.admin.service.AdminRoleService;
import com.example.common.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRoleControllerTest {

    @Mock
    private AdminRoleService adminRoleService;

    @InjectMocks
    private AdminRoleController controller;

    @Test
    @DisplayName("사용자를 ADMIN으로 승격한다")
    void promoteToAdmin_returnsSuccess() {
        RoleChangeRequestDto req = new RoleChangeRequestDto();
        RoleChangeResponseDto resp = RoleChangeResponseDto.builder().name("TestUser").build();
        when(adminRoleService.promoteToAdmin(42L, 1L, null)).thenReturn(resp);

        ResponseEntity<ApiResponse> result = controller.promoteToAdmin(42L, 1L, req);

        assertThat(result.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("요청 본문이 null이면 reason을 null로 전달한다")
    void promoteToAdmin_withNullRequest_passesNullReason() {
        RoleChangeResponseDto resp = RoleChangeResponseDto.builder().name("TestUser").build();
        when(adminRoleService.promoteToAdmin(42L, 1L, null)).thenReturn(resp);

        controller.promoteToAdmin(42L, 1L, null);

        verify(adminRoleService).promoteToAdmin(42L, 1L, null);
    }

    @Test
    @DisplayName("ADMIN을 USER로 강등한다")
    void demoteToUser_returnsSuccess() {
        RoleChangeResponseDto resp = RoleChangeResponseDto.builder().name("TestUser").build();
        when(adminRoleService.demoteToUser(42L, 1L, null)).thenReturn(resp);

        ResponseEntity<ApiResponse> result = controller.demoteToUser(42L, 1L, null);

        assertThat(result.getBody().isSuccess()).isTrue();
    }

    @Test
    @DisplayName("감사 로그를 페이징으로 조회한다")
    void getAuditLogs_returnsPage() {
        Pageable pageable = PageRequest.of(0, 20);
        Page<AuditLogDto> page = new PageImpl<>(List.of());
        when(adminRoleService.getAuditLogsPaged(42L, pageable)).thenReturn(page);

        ResponseEntity<ApiResponse> result = controller.getAuditLogs(42L, pageable);

        assertThat(result.getBody().isSuccess()).isTrue();
    }
}
