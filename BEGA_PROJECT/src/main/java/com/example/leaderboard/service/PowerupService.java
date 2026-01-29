package com.example.leaderboard.service;

import com.example.leaderboard.dto.ActivePowerupDto;
import com.example.leaderboard.dto.PowerupInventoryDto;
import com.example.leaderboard.entity.ActivePowerup;
import com.example.leaderboard.entity.UserPowerup;
import com.example.leaderboard.repository.ActivePowerupRepository;
import com.example.leaderboard.repository.UserPowerupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 파워업 관리 서비스
 * 파워업 인벤토리 및 사용을 담당합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PowerupService {

    private final UserPowerupRepository userPowerupRepository;
    private final ActivePowerupRepository activePowerupRepository;

    /**
     * 사용자 파워업 인벤토리 조회
     */
    @Transactional(readOnly = true)
    public List<PowerupInventoryDto> getUserPowerups(Long userId) {
        List<UserPowerup> userPowerups = userPowerupRepository.findByUserId(userId);

        // 기존 파워업을 Map으로 변환
        Map<UserPowerup.PowerupType, UserPowerup> powerupMap = userPowerups.stream()
                .collect(Collectors.toMap(UserPowerup::getPowerupType, Function.identity()));

        // 모든 파워업 타입에 대해 DTO 생성 (없으면 수량 0)
        return Arrays.stream(UserPowerup.PowerupType.values())
                .map(type -> {
                    UserPowerup powerup = powerupMap.get(type);
                    int quantity = powerup != null ? powerup.getQuantity() : 0;
                    return PowerupInventoryDto.fromType(type, quantity);
                })
                .toList();
    }

    /**
     * 활성화된 파워업 조회
     */
    @Transactional(readOnly = true)
    public List<ActivePowerupDto> getActivePowerups(Long userId) {
        return activePowerupRepository.findActiveByUserId(userId, Instant.now()).stream()
                .map(ActivePowerupDto::from)
                .toList();
    }

    /**
     * 특정 게임에 파워업 사용
     * @param userId 사용자 ID
     * @param type 파워업 타입
     * @param gameId 게임 ID (선택적)
     * @return 성공 여부
     */
    @Transactional
    public Integer usePowerup(Long userId, String type, String gameId) {
        UserPowerup.PowerupType powerupType;
        try {
            powerupType = UserPowerup.PowerupType.valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid powerup type: {}", type);
            return null;
        }

        // 인벤토리에서 파워업 확인
        UserPowerup inventory = userPowerupRepository.findByUserIdAndPowerupType(userId, powerupType)
                .orElse(null);

        if (inventory == null || !inventory.hasAny()) {
            log.warn("User {} has no {} powerup available", userId, powerupType);
            return null;
        }

        // 인벤토리에서 차감
        inventory.use();
        userPowerupRepository.save(inventory);

        // 활성화된 파워업 생성
        ActivePowerup activePowerup = ActivePowerup.activateForGame(userId, powerupType, gameId);
        activePowerupRepository.save(activePowerup);

        log.info("User {} activated {} powerup for game {}", userId, powerupType, gameId);
        return inventory.getQuantity();
    }

    /**
     * 파워업 보상 지급
     * @param userId 사용자 ID
     * @param type 파워업 타입
     * @param amount 수량
     */
    @Transactional
    public void grantPowerup(Long userId, UserPowerup.PowerupType type, int amount) {
        UserPowerup powerup = userPowerupRepository.findByUserIdAndPowerupType(userId, type)
                .orElseGet(() -> {
                    UserPowerup newPowerup = UserPowerup.create(userId, type, 0);
                    return userPowerupRepository.save(newPowerup);
                });

        powerup.add(amount);
        userPowerupRepository.save(powerup);

        log.info("Granted {} x{} to user {}", type, amount, userId);
    }

    /**
     * 만료된 파워업 정리 (스케줄러용)
     */
    @Transactional
    public int cleanupExpiredPowerups() {
        int count = activePowerupRepository.markExpiredAsUsed(Instant.now());
        if (count > 0) {
            log.info("Marked {} expired powerups as used", count);
        }
        return count;
    }

    /**
     * 스카우터 파워업 사용 가능 여부 확인
     */
    @Transactional(readOnly = true)
    public boolean canUseScouter(Long userId) {
        return userPowerupRepository.hasAvailablePowerup(userId, UserPowerup.PowerupType.SCOUTER);
    }

    /**
     * 특정 게임에 대해 활성화된 매직 배트 확인
     */
    @Transactional(readOnly = true)
    public boolean hasMagicBatForGame(Long userId, String gameId) {
        return activePowerupRepository.findActiveForGameAndType(
                userId, gameId, UserPowerup.PowerupType.MAGIC_BAT
        ).isPresent();
    }

    /**
     * 특정 게임에 대해 활성화된 골든 글러브 확인
     */
    @Transactional(readOnly = true)
    public boolean hasGoldenGloveForGame(Long userId, String gameId) {
        return activePowerupRepository.findActiveForGameAndType(
                userId, gameId, UserPowerup.PowerupType.GOLDEN_GLOVE
        ).isPresent();
    }
}
