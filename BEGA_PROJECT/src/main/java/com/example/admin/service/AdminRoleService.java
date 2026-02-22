package com.example.admin.service;

import com.example.admin.dto.AuditLogDto;
import com.example.admin.dto.RoleChangeResponseDto;
import com.example.admin.entity.AuditLog;
import com.example.admin.exception.InsufficientPrivilegeException;
import com.example.admin.exception.InvalidRoleChangeException;
import com.example.admin.repository.AuditLogRepository;
import com.example.auth.entity.Role;
import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;
import com.example.common.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 어드민 역할 관리 서비스
 * SUPER_ADMIN만 권한 변경 가능
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRoleService {

        private final UserRepository userRepository;
        private final AuditLogRepository auditLogRepository;

        /**
         * 사용자를 ADMIN으로 승격 (SUPER_ADMIN만 가능)
         */
        @Transactional
        public RoleChangeResponseDto promoteToAdmin(Long adminId, Long targetUserId, String reason) {
                // 1. 현재 관리자 확인
                UserEntity admin = userRepository.findById(Objects.requireNonNull(adminId))
                                .orElseThrow(() -> new UserNotFoundException(adminId));

                // 2. SUPER_ADMIN 권한 확인
                if (!admin.isSuperAdmin()) {
                        throw new InsufficientPrivilegeException();
                }

                // 3. 대상 사용자 조회
                UserEntity targetUser = userRepository.findById(Objects.requireNonNull(targetUserId))
                                .orElseThrow(() -> new UserNotFoundException(targetUserId));

                // 4. 이미 ADMIN 이상인지 확인
                if (targetUser.isAdmin()) {
                        throw new InvalidRoleChangeException(
                                        "해당 사용자는 이미 관리자 권한을 보유하고 있습니다. 현재 역할: " + targetUser.getRole());
                }

                // 5. 역할 변경
                String oldRole = targetUser.getRole();
                String newRole = Role.ADMIN.getKey();
                targetUser.setRole(newRole);
                userRepository.save(targetUser);

                // 6. 감사 로그 기록
                AuditLog auditLog = AuditLog.builder()
                                .adminId(adminId)
                                .targetUserId(targetUserId)
                                .action(AuditLog.AuditAction.PROMOTE_TO_ADMIN)
                                .oldValue(oldRole)
                                .newValue(newRole)
                                .description(reason)
                                .build();
                auditLogRepository.save(Objects.requireNonNull(auditLog));

                log.info("User {} promoted to ADMIN by SUPER_ADMIN {}. Reason: {}",
                                targetUserId, adminId, reason);

                // 7. 응답 생성
                return RoleChangeResponseDto.builder()
                                .userId(targetUser.getId())
                                .email(targetUser.getEmail())
                                .name(targetUser.getName())
                                .previousRole(oldRole)
                                .newRole(newRole)
                                .changedAt(Instant.now())
                                .build();
        }

        /**
         * ADMIN을 USER로 강등 (SUPER_ADMIN만 가능)
         */
        @Transactional
        public RoleChangeResponseDto demoteToUser(Long adminId, Long targetUserId, String reason) {
                // 1. 현재 관리자 확인
                UserEntity admin = userRepository.findById(Objects.requireNonNull(adminId))
                                .orElseThrow(() -> new UserNotFoundException(adminId));

                // 2. SUPER_ADMIN 권한 확인
                if (!admin.isSuperAdmin()) {
                        throw new InsufficientPrivilegeException();
                }

                // 3. 대상 사용자 조회
                UserEntity targetUser = userRepository.findById(Objects.requireNonNull(targetUserId))
                                .orElseThrow(() -> new UserNotFoundException(targetUserId));

                // 4. SUPER_ADMIN은 강등 불가
                if (targetUser.isSuperAdmin()) {
                        throw new InvalidRoleChangeException("SUPER_ADMIN은 강등할 수 없습니다.");
                }

                // 5. 이미 USER인지 확인
                if (Role.USER.getKey().equals(targetUser.getRole())) {
                        throw new InvalidRoleChangeException("해당 사용자는 이미 일반 사용자입니다.");
                }

                // 6. 자기 자신 강등 방지
                if (adminId.equals(targetUserId)) {
                        throw new InvalidRoleChangeException("자기 자신의 권한은 변경할 수 없습니다.");
                }

                // 7. 역할 변경
                String oldRole = targetUser.getRole();
                String newRole = Role.USER.getKey();
                targetUser.setRole(newRole);
                userRepository.save(targetUser);

                // 8. 감사 로그 기록
                AuditLog auditLog = AuditLog.builder()
                                .adminId(adminId)
                                .targetUserId(targetUserId)
                                .action(AuditLog.AuditAction.DEMOTE_TO_USER)
                                .oldValue(oldRole)
                                .newValue(newRole)
                                .description(reason)
                                .build();
                auditLogRepository.save(Objects.requireNonNull(auditLog));

                log.info("User {} demoted to USER by SUPER_ADMIN {}. Reason: {}",
                                targetUserId, adminId, reason);

                // 9. 응답 생성
                return RoleChangeResponseDto.builder()
                                .userId(targetUser.getId())
                                .email(targetUser.getEmail())
                                .name(targetUser.getName())
                                .previousRole(oldRole)
                                .newRole(newRole)
                                .changedAt(Instant.now())
                                .build();
        }

        /**
         * 감사 로그 조회 (SUPER_ADMIN만 가능)
         */
        @Transactional(readOnly = true)
        public List<AuditLogDto> getAuditLogs(Long adminId) {
                // 권한 확인
                UserEntity admin = userRepository.findById(Objects.requireNonNull(adminId))
                                .orElseThrow(() -> new UserNotFoundException(adminId));

                if (!admin.isSuperAdmin()) {
                        throw new InsufficientPrivilegeException("감사 로그 조회는 SUPER_ADMIN만 가능합니다.");
                }

                List<AuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtDesc();

                return logs.stream()
                                .map(this::enrichAuditLogDto)
                                .collect(Collectors.toList());
        }

        /**
         * 감사 로그 조회 (페이징, SUPER_ADMIN만 가능)
         */
        @Transactional(readOnly = true)
        public Page<AuditLogDto> getAuditLogsPaged(Long adminId, Pageable pageable) {
                // 권한 확인
                UserEntity admin = userRepository.findById(Objects.requireNonNull(adminId))
                                .orElseThrow(() -> new UserNotFoundException(adminId));

                if (!admin.isSuperAdmin()) {
                        throw new InsufficientPrivilegeException("감사 로그 조회는 SUPER_ADMIN만 가능합니다.");
                }

                return auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                                .map(this::enrichAuditLogDto);
        }

        /**
         * AuditLog 엔티티를 DTO로 변환하면서 사용자 정보 추가
         */
        private AuditLogDto enrichAuditLogDto(AuditLog log) {
                // 관리자 정보 조회
                UserEntity admin = log.getAdminId() != null
                                ? userRepository.findById(Objects.requireNonNull(log.getAdminId())).orElse(null)
                                : null;

                // 대상 사용자 정보 조회
                UserEntity targetUser = log.getTargetUserId() != null
                                ? userRepository.findById(Objects.requireNonNull(log.getTargetUserId())).orElse(null)
                                : null;

                return AuditLogDto.builder()
                                .id(log.getId())
                                .adminId(log.getAdminId())
                                .adminEmail(admin != null ? admin.getEmail() : "(삭제된 사용자)")
                                .adminName(admin != null ? admin.getName() : "(삭제된 사용자)")
                                .targetUserId(log.getTargetUserId())
                                .targetUserEmail(targetUser != null ? targetUser.getEmail() : "(삭제된 사용자)")
                                .targetUserName(targetUser != null ? targetUser.getName() : "(삭제된 사용자)")
                                .action(log.getAction().name())
                                .actionDescription(log.getAction().getDescription())
                                .oldValue(log.getOldValue())
                                .newValue(log.getNewValue())
                                .description(log.getDescription())
                                .createdAt(log.getCreatedAt())
                                .build();
        }
}
