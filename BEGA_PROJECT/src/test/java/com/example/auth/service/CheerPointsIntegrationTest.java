package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Point System Integration Test
 * Verifies the Earn -> Spend cycle for Cheer Points
 */
class CheerPointsIntegrationTest {

    @Test
    @DisplayName("포인트 적립 및 차감 전체 사이클 테스트")
    void testFullEarnSpendCycle() {
        // 1. Setup: Create a user with 0 points
        UserEntity user = UserEntity.builder()
                .email("test@example.com")
                .name("TestUser")
                .build();

        // Initial state: cheerPoints should be 0 (via @Builder.Default)
        assertEquals(0, user.getCheerPoints(), "Initial cheerPoints should be 0");

        // 2. Earn: Add points (simulating like action)
        user.addCheerPoints(5); // Daily login bonus
        assertEquals(5, user.getCheerPoints(), "After daily login bonus, should have 5 points");

        user.addCheerPoints(1); // Like action
        assertEquals(6, user.getCheerPoints(), "After like, should have 6 points");

        // 3. Spend: Deduct points (simulating vote action)
        user.deductCheerPoints(1); // Vote action
        assertEquals(5, user.getCheerPoints(), "After vote, should have 5 points");

        // 4. Edge case: Deduct more than available
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> user.deductCheerPoints(10),
                "Should throw exception when deducting more points than available");
        assertEquals("응원 포인트가 부족합니다.", exception.getMessage());

        // Final state: Points should be unchanged after failed deduction
        assertEquals(5, user.getCheerPoints(), "Points should remain 5 after failed deduction");
    }

    @Test
    @DisplayName("0 포인트에서 시작하는 경우 테스트")
    void testZeroInitialPoints() {
        UserEntity user = UserEntity.builder()
                .email("zero_test@example.com")
                .build();

        // cheerPoints is 0 initially (via @Builder.Default)
        assertEquals(0, user.getCheerPoints());

        // Add points -> should become 1
        user.addCheerPoints(1);
        assertEquals(1, user.getCheerPoints());

        // Deduct 1 -> should become 0
        user.deductCheerPoints(1);
        assertEquals(0, user.getCheerPoints());

        // Deduct from 0 -> should throw
        assertThrows(IllegalStateException.class, () -> user.deductCheerPoints(1));
    }
}
