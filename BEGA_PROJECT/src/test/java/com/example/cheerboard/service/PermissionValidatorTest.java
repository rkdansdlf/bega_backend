package com.example.cheerboard.service;

import com.example.auth.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissionValidatorTest {

    private final PermissionValidator permissionValidator = new PermissionValidator();

    @Test
    @DisplayName("마이팀이 일치하면 팀 제한 액션이 허용된다")
    void validateTeamAccess_allowsMatchingFavoriteTeam() {
        UserEntity user = UserEntity.builder().id(1L).role("ROLE_USER").build();
        user.setFavoriteTeam(com.example.kbo.entity.TeamEntity.builder().teamId("LG").build());

        assertThatCode(() -> permissionValidator.validateTeamAccess(user, "LG", "게시글 작성"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("마이팀이 다르면 팀 제한 액션이 거부된다")
    void validateTeamAccess_rejectsMismatchedFavoriteTeam() {
        UserEntity user = UserEntity.builder().id(1L).role("ROLE_USER").build();
        user.setFavoriteTeam(com.example.kbo.entity.TeamEntity.builder().teamId("KIA").build());

        assertThatThrownBy(() -> permissionValidator.validateTeamAccess(user, "LG", "댓글 작성"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("마이팀에서만");
    }

    @Test
    @DisplayName("관리자는 팀 제한을 우회한다")
    void validateTeamAccess_allowsAdminBypass() {
        UserEntity admin = UserEntity.builder().id(9L).role("ROLE_ADMIN").build();

        assertThatCode(() -> permissionValidator.validateTeamAccess(admin, "LG", "게시글 작성"))
                .doesNotThrowAnyException();
    }
}
