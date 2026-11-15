package com.example.notification.dto;

import com.example.notification.entity.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void responseSerializesCreatedAtWithTheServerZoneOffset() throws Exception {
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 14, 12, 30, 0, 123_000_000);
        Notification notification = Notification.builder()
                .id(1L)
                .userId(10L)
                .type(Notification.NotificationType.NEW_FOLLOWER)
                .title("새 팔로워")
                .message("새 팔로워가 있습니다.")
                .isRead(false)
                .createdAt(createdAt)
                .build();

        byte[] responseBody = objectMapper.writeValueAsBytes(NotificationDTO.Response.from(notification));
        JsonNode json = objectMapper.readTree(responseBody);
        OffsetDateTime responseCreatedAt = OffsetDateTime.parse(json.get("createdAt").asText());

        assertThat(responseCreatedAt.toLocalDateTime()).isEqualTo(createdAt);
        assertThat(responseCreatedAt.getOffset()).isEqualTo(
                ZoneId.systemDefault().getRules().getOffset(createdAt));
    }
}
