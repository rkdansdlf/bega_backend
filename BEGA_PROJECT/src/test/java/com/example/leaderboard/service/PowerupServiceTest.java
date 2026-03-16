package com.example.leaderboard.service;

import com.example.leaderboard.dto.PowerupInventoryDto;
import com.example.leaderboard.entity.ActivePowerup;
import com.example.leaderboard.entity.UserPowerup;
import com.example.leaderboard.entity.UserPowerup.PowerupType;
import com.example.leaderboard.repository.ActivePowerupRepository;
import com.example.leaderboard.repository.UserPowerupRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.example.leaderboard.support.LeaderboardTestFixtureFactory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PowerupServiceTest {

    @InjectMocks
    private PowerupService powerupService;

    @Mock
    private UserPowerupRepository userPowerupRepository;

    @Mock
    private ActivePowerupRepository activePowerupRepository;

    // ============================================
    // getUserPowerups
    // ============================================

    @Test
    @DisplayName("보유한 파워업만 있어도 모든 타입이 반환된다 (미보유는 수량 0)")
    void getUserPowerups_returnsAllTypesWithZeroForMissing() {
        Long userId = 1L;
        UserPowerup magicBat = powerup(userId, PowerupType.MAGIC_BAT, 2);
        when(userPowerupRepository.findByUserId(userId)).thenReturn(List.of(magicBat));

        List<PowerupInventoryDto> result = powerupService.getUserPowerups(userId);

        assertThat(result).hasSize(PowerupType.values().length);
        assertThat(result.stream().filter(d -> d.getType().equals("MAGIC_BAT")).findFirst().get().getQuantity())
                .isEqualTo(2);
        assertThat(result.stream().filter(d -> d.getType().equals("GOLDEN_GLOVE")).findFirst().get().getQuantity())
                .isZero();
    }

    @Test
    @DisplayName("파워업이 없으면 모든 타입 수량 0으로 반환")
    void getUserPowerups_emptyInventory() {
        Long userId = 2L;
        when(userPowerupRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        List<PowerupInventoryDto> result = powerupService.getUserPowerups(userId);

        assertThat(result).hasSize(PowerupType.values().length);
        assertThat(result).allMatch(d -> d.getQuantity() == 0);
    }

    // ============================================
    // usePowerup
    // ============================================

    @Test
    @DisplayName("파워업 사용 성공 시 차감 후 남은 수량 반환")
    void usePowerup_successfulActivation() {
        Long userId = 3L;
        UserPowerup inv = powerup(userId, PowerupType.MAGIC_BAT, 3);
        when(userPowerupRepository.findByUserIdAndPowerupType(userId, PowerupType.MAGIC_BAT))
                .thenReturn(Optional.of(inv));
        when(userPowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(activePowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Integer remaining = powerupService.usePowerup(userId, "MAGIC_BAT", "game1");

        assertThat(remaining).isEqualTo(2);
        verify(activePowerupRepository).save(any(ActivePowerup.class));
    }

    @Test
    @DisplayName("인벤토리에 없으면 null 반환")
    void usePowerup_noInventory() {
        Long userId = 4L;
        when(userPowerupRepository.findByUserIdAndPowerupType(userId, PowerupType.MAGIC_BAT))
                .thenReturn(Optional.empty());

        Integer result = powerupService.usePowerup(userId, "MAGIC_BAT", "game1");

        assertThat(result).isNull();
        verify(activePowerupRepository, never()).save(any());
    }

    @Test
    @DisplayName("수량 0이면 null 반환")
    void usePowerup_zeroQuantity() {
        Long userId = 5L;
        UserPowerup inv = powerup(userId, PowerupType.GOLDEN_GLOVE, 0);
        when(userPowerupRepository.findByUserIdAndPowerupType(userId, PowerupType.GOLDEN_GLOVE))
                .thenReturn(Optional.of(inv));

        Integer result = powerupService.usePowerup(userId, "GOLDEN_GLOVE", "game1");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("잘못된 타입 이름이면 null 반환")
    void usePowerup_invalidTypeName() {
        Long userId = 6L;

        Integer result = powerupService.usePowerup(userId, "INVALID", "game1");

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("소문자 타입 입력도 대문자로 변환되어 처리")
    void usePowerup_caseInsensitive() {
        Long userId = 7L;
        UserPowerup inv = powerup(userId, PowerupType.MAGIC_BAT, 1);
        when(userPowerupRepository.findByUserIdAndPowerupType(userId, PowerupType.MAGIC_BAT))
                .thenReturn(Optional.of(inv));
        when(userPowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(activePowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        Integer remaining = powerupService.usePowerup(userId, "magic_bat", "game1");

        assertThat(remaining).isEqualTo(0);
    }

    // ============================================
    // grantPowerup
    // ============================================

    @Test
    @DisplayName("기존 인벤토리에 수량 추가")
    void grantPowerup_existingInventory() {
        Long userId = 8L;
        UserPowerup existing = powerup(userId, PowerupType.SCOUTER, 2);
        when(userPowerupRepository.findByUserIdAndPowerupType(userId, PowerupType.SCOUTER))
                .thenReturn(Optional.of(existing));
        when(userPowerupRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        powerupService.grantPowerup(userId, PowerupType.SCOUTER, 3);

        assertThat(existing.getQuantity()).isEqualTo(5);
        verify(userPowerupRepository).save(existing);
    }

    @Test
    @DisplayName("인벤토리가 없으면 새로 생성 후 수량 추가")
    void grantPowerup_createsNewInventory() {
        Long userId = 9L;
        when(userPowerupRepository.findByUserIdAndPowerupType(userId, PowerupType.MAGIC_BAT))
                .thenReturn(Optional.empty());
        UserPowerup created = powerup(userId, PowerupType.MAGIC_BAT, 0);
        when(userPowerupRepository.save(any())).thenReturn(created);

        powerupService.grantPowerup(userId, PowerupType.MAGIC_BAT, 5);

        // save is called twice: once for create, once for add
        verify(userPowerupRepository, times(2)).save(any(UserPowerup.class));
    }

    // ============================================
    // cleanupExpiredPowerups
    // ============================================

    @Test
    @DisplayName("만료된 파워업 수 반환")
    void cleanupExpiredPowerups_returnsCount() {
        when(activePowerupRepository.markExpiredAsUsed(any(LocalDateTime.class))).thenReturn(5);

        int count = powerupService.cleanupExpiredPowerups();

        assertThat(count).isEqualTo(5);
    }

    @Test
    @DisplayName("만료된 파워업이 없으면 0 반환")
    void cleanupExpiredPowerups_noneExpired() {
        when(activePowerupRepository.markExpiredAsUsed(any(LocalDateTime.class))).thenReturn(0);

        int count = powerupService.cleanupExpiredPowerups();

        assertThat(count).isZero();
    }

    // ============================================
    // canUseScouter / hasMagicBatForGame / hasGoldenGloveForGame
    // ============================================

    @Test
    @DisplayName("스카우터 사용 가능 시 true")
    void canUseScouter_available() {
        Long userId = 10L;
        // hasAvailablePowerup is a default method → mock it directly
        when(userPowerupRepository.hasAvailablePowerup(userId, PowerupType.SCOUTER)).thenReturn(true);

        assertThat(powerupService.canUseScouter(userId)).isTrue();
    }

    @Test
    @DisplayName("스카우터 없으면 false")
    void canUseScouter_unavailable() {
        Long userId = 11L;
        when(userPowerupRepository.hasAvailablePowerup(userId, PowerupType.SCOUTER)).thenReturn(false);

        assertThat(powerupService.canUseScouter(userId)).isFalse();
    }

    @Test
    @DisplayName("게임에 활성 매직배트가 있으면 true")
    void hasMagicBatForGame_active() {
        Long userId = 12L;
        ActivePowerup ap = activePowerupForGame(userId, PowerupType.MAGIC_BAT, "game1");
        when(activePowerupRepository.findActiveForGameAndType(userId, "game1", PowerupType.MAGIC_BAT))
                .thenReturn(Optional.of(ap));

        assertThat(powerupService.hasMagicBatForGame(userId, "game1")).isTrue();
    }

    @Test
    @DisplayName("게임에 활성 골든글러브가 없으면 false")
    void hasGoldenGloveForGame_notActive() {
        Long userId = 13L;
        when(activePowerupRepository.findActiveForGameAndType(userId, "game1", PowerupType.GOLDEN_GLOVE))
                .thenReturn(Optional.empty());

        assertThat(powerupService.hasGoldenGloveForGame(userId, "game1")).isFalse();
    }
}
