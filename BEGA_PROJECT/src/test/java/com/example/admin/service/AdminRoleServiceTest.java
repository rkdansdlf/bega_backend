package com.example.admin.service;

import com.example.admin.dto.AuditLogDto;
import com.example.admin.entity.AuditLog;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRoleService tests")
class AdminRoleServiceTest {

    @InjectMocks
    private AdminRoleService adminRoleService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("getAuditLogsPaged batches distinct users into one repository lookup")
    void getAuditLogsPaged_batchesDistinctUsersIntoOneLookup() {
        UserEntity admin = user(1L, "admin@example.com", "Admin", "ROLE_SUPER_ADMIN");
        UserEntity user10 = user(10L, "user10@example.com", "User 10", "ROLE_USER");
        UserEntity user20 = user(20L, "user20@example.com", "User 20", "ROLE_USER");
        UserEntity user30 = user(30L, "user30@example.com", "User 30", "ROLE_USER");
        UserEntity user40 = user(40L, "user40@example.com", "User 40", "ROLE_USER");
        PageRequest pageable = PageRequest.of(0, 10);

        List<AuditLog> logs = List.of(
                auditLog(10L, 20L, AuditLog.AuditAction.PROMOTE_TO_ADMIN, "ROLE_USER", "ROLE_ADMIN", LocalDateTime.now()),
                auditLog(10L, 30L, AuditLog.AuditAction.DEMOTE_TO_USER, "ROLE_ADMIN", "ROLE_USER", LocalDateTime.now().minusMinutes(1)),
                auditLog(40L, 20L, AuditLog.AuditAction.DELETE_USER, null, null, LocalDateTime.now().minusMinutes(2)));

        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(new PageImpl<>(logs, pageable, logs.size()));
        when(userRepository.findAllById(any())).thenReturn(List.of(user10, user20, user30, user40));

        List<AuditLogDto> result = adminRoleService.getAuditLogsPaged(1L, pageable).getContent();

        assertThat(result).hasSize(3);
        assertThat(result.get(1).getAdminEmail()).isEqualTo("user10@example.com");
        assertThat(result.get(1).getTargetUserEmail()).isEqualTo("user30@example.com");
        verify(userRepository, times(1)).findById(1L);
        verify(userRepository, times(1)).findAllById(argThat((Iterable<Long> userIds) -> {
            List<Long> ids = new java.util.ArrayList<>();
            userIds.forEach(ids::add);
            return ids.size() == 4 && ids.containsAll(List.of(10L, 20L, 30L, 40L));
        }));
    }

    private UserEntity user(Long id, String email, String name, String role) {
        return UserEntity.builder()
                .id(id)
                .email(email)
                .name(name)
                .handle("@" + name.toLowerCase().replace(' ', '_'))
                .role(role)
                .build();
    }

    private AuditLog auditLog(
            Long adminId,
            Long targetUserId,
            AuditLog.AuditAction action,
            String oldValue,
            String newValue,
            LocalDateTime createdAt) {
        return AuditLog.builder()
                .adminId(adminId)
                .targetUserId(targetUserId)
                .action(action)
                .oldValue(oldValue)
                .newValue(newValue)
                .description(action.getDescription())
                .createdAt(createdAt)
                .build();
    }
}
