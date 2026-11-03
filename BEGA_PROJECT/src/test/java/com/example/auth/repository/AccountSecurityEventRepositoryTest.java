package com.example.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.auth.entity.AccountSecurityEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

@DataJpaTest
class AccountSecurityEventRepositoryTest {

    @Autowired
    private AccountSecurityEventRepository accountSecurityEventRepository;

    @Test
    void anonymizesUserAndDeviceFieldsButPreservesEventTypeAndRetentionTimestamp() {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 1, 12, 30);
        AccountSecurityEvent event = accountSecurityEventRepository.saveAndFlush(AccountSecurityEvent.builder()
                .userId(700L)
                .occurredAt(occurredAt)
                .eventType(AccountSecurityEvent.EventType.NEW_DEVICE_LOGIN)
                .deviceLabel("synthetic device")
                .deviceType("desktop")
                .browser("synthetic browser")
                .os("synthetic os")
                .ip("192.0.2.20")
                .message("synthetic account detail")
                .build());

        int affected = accountSecurityEventRepository.anonymizeByUserId(700L);
        AccountSecurityEvent anonymized = accountSecurityEventRepository.findById(event.getId()).orElseThrow();

        assertThat(affected).isEqualTo(1);
        assertThat(anonymized.getUserId()).isNull();
        assertThat(anonymized.getDeviceLabel()).isNull();
        assertThat(anonymized.getDeviceType()).isNull();
        assertThat(anonymized.getBrowser()).isNull();
        assertThat(anonymized.getOs()).isNull();
        assertThat(anonymized.getIp()).isNull();
        assertThat(anonymized.getMessage()).isEqualTo("계정 보안 이벤트");
        assertThat(anonymized.getEventType()).isEqualTo(AccountSecurityEvent.EventType.NEW_DEVICE_LOGIN);
        assertThat(anonymized.getOccurredAt()).isEqualTo(occurredAt);
    }
}
