package com.example.common.realtime;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Outbox 시각의 영속화 정밀도를 한 곳에서 고정한다.
 *
 * <p>H2/PostgreSQL timestamp 는 마이크로초 정밀도다. Linux JDK 의 {@code Instant.now()} 는 나노초라서
 * 그대로 저장하면 값이 반올림되어, 저장값이 이후 조회 조건({@code availableAt <= :dueAt} 등)의
 * 기준 시각보다 커질 수 있다. 그러면 같은 시각의 claim 이 0건이 된다(macOS 는 마이크로초라 재현되지 않는다).
 * 저장하는 값과 비교에 쓰는 값을 모두 같은 정밀도로 절삭해 두면 정밀도 차이 자체가 사라진다.
 *
 * <p>outbox 에 쓰거나 outbox 시각과 비교하는 모든 경로는 이 helper 를 거친다:
 * {@link RealtimeOutboxEvent#pending}, {@link RealtimeOutboxStateService}.
 */
final class RealtimeOutboxTime {

    private RealtimeOutboxTime() {
    }

    static Instant persisted(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }
}
