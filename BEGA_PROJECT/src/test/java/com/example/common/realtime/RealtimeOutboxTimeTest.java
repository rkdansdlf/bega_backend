package com.example.common.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RealtimeOutboxTimeTest {

    @Test
    @DisplayName("나노초 잔여분은 반올림이 아니라 절삭한다 (저장값이 기준 시각보다 커지지 않는다)")
    void truncatesSubMicrosecondResidue() {
        Instant now = Instant.parse("2026-09-30T14:00:00.123456999Z");

        assertThat(RealtimeOutboxTime.persisted(now)).isEqualTo(Instant.parse("2026-09-30T14:00:00.123456Z"));
        assertThat(RealtimeOutboxTime.persisted(now)).isBeforeOrEqualTo(now);
    }

    @Test
    @DisplayName("이미 마이크로초 정밀도이면 그대로 두고, null 은 null 로 통과한다")
    void isIdempotentAndNullSafe() {
        Instant micros = Instant.parse("2026-09-30T14:00:00.123456Z");

        assertThat(RealtimeOutboxTime.persisted(micros)).isEqualTo(micros);
        assertThat(RealtimeOutboxTime.persisted(RealtimeOutboxTime.persisted(micros))).isEqualTo(micros);
        assertThat(RealtimeOutboxTime.persisted(null)).isNull();
    }

    @Test
    @DisplayName("pending() 이벤트의 availableAt/createdAt 은 절삭된 값이라 같은 시각 조회 기준(dueAt)에 항상 포함된다")
    void pendingEventIsAlwaysDueAtItsOwnCreationTime() {
        Instant now = Instant.parse("2026-09-30T14:00:00.123456789Z");
        RealtimeOutboxEvent event = RealtimeOutboxEvent.pending(
                RealtimeMessageEnvelope.broadcast("t-1", "/topic/x", new ObjectMapper().valueToTree(Map.of("m", "x"))),
                "{\"m\":\"x\"}",
                now);

        assertThat(event.getAvailableAt()).isBeforeOrEqualTo(RealtimeOutboxTime.persisted(now));
        assertThat(event.getCreatedAt()).isEqualTo(RealtimeOutboxTime.persisted(now));
    }
}
