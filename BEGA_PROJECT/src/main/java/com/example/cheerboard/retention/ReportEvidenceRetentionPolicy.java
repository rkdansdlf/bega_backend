package com.example.cheerboard.retention;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 신고 증거 미디어 보관 기한의 계산 규칙. 모든 시점은 계정 최초 삭제 요청일을 기준으로 한다.
 *
 * <ul>
 *   <li>기본 보관 기간은 30일이다.</li>
 *   <li>활성 사건과 명시적 승인이 있을 때만 연장할 수 있으며, 어떤 경우에도 90일을 넘을 수 없다.</li>
 * </ul>
 */
public final class ReportEvidenceRetentionPolicy {

    public static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    public static final Duration MAX_RETENTION = Duration.ofDays(90);
    /** 법적 보존 근거의 검토 기한은 기록 시점부터 이 기간을 넘을 수 없다. */
    public static final Duration MAX_LEGAL_REVIEW = Duration.ofDays(365);
    /**
     * 열린 사건의 기한이 지났을 때 행마다 한 번만 주는 긴급 검토 유예. 유예가 끝나도 결정이 없으면 삭제한다.
     * 23시간인 이유: 일일 작업은 하루 한 번 돌고, 작업 시작 시각은 몇 초씩 흔들린다. 23시간이면 다음 일일 작업이
     * 유예 종료를 놓치지 않는다. 그러면 최악의 경우에도 삭제는 유예 시작 후 24시간 안에 일어난다.
     */
    public static final Duration URGENT_REVIEW_GRACE = Duration.ofHours(23);
    /**
     * 긴급 검토가 끝나야 하는 시점은 90일 상한보다 이만큼 앞선다. 일일 작업의 실행 간격(최대 24시간)과 시작 시각의
     * 흔들림을 흡수해서, 삭제가 상한 안에서 끝나게 한다.
     */
    public static final Duration URGENT_DEADLINE_MARGIN = Duration.ofDays(2);
    /**
     * 긴급 검토 유예의 최소 길이. 상한 여유를 뺀 시각까지 이만큼 남지 않으면 유예를 줄 수 없으므로, 자동 삭제 대신
     * 검토 대상으로 보낸다(OVER_CAP_NO_GRACE).
     */
    public static final Duration MIN_URGENT_GRACE = Duration.ofHours(12);
    /** 서버가 쓰는 시간대. 계정 삭제 시각 컬럼(TIMESTAMPTZ)을 이 시간대의 벽시계 시각으로 읽는다. */
    public static final ZoneId APP_ZONE = ZoneId.of("Asia/Seoul");
    /** 삭제 실패가 이 횟수에 도달하면 운영자 알림 로그를 남긴다. */
    public static final int ALERT_ATTEMPT_THRESHOLD = 5;
    /**
     * 삭제 점유 유효 시간. 저장소 호출 제한 시간(30초)보다 훨씬 길어서, 제한 시간을 넘긴 호출이 아직 돌고 있을 수 있는
     * 동안에는 점유가 풀리지 않는다. 점유가 살아 있으면 보존 근거와 연장은 거부된다.
     */
    public static final Duration DELETE_CLAIM_LEASE = Duration.ofHours(1);
    /** 같은 객체를 다른 유효한 행이 아직 보호하고 있을 때 삭제를 미루는 간격. */
    public static final Duration DELETE_DEFERRAL = Duration.ofDays(1);
    /** 법적 보존 검토 기한이 이 기간 안에 남으면 운영자 알림을 남긴다. 기한이 끝나면 자동 삭제 대상이 된다. */
    public static final Duration LEGAL_HOLD_ALERT_WINDOW = Duration.ofDays(14);

    private static final long RETRY_BASE_MINUTES = 10;
    private static final long RETRY_CAP_MINUTES = 24 * 60;

    private ReportEvidenceRetentionPolicy() {}

    public static LocalDateTime defaultUntil(LocalDateTime baseRequestedAt) {
        return baseRequestedAt.plus(DEFAULT_RETENTION);
    }

    public static LocalDateTime hardLimit(LocalDateTime baseRequestedAt) {
        return baseRequestedAt.plus(MAX_RETENTION);
    }

    /**
     * 신고 증거의 보관 기준일. 가장 이른 신고가 현재 삭제 주기 시작 전에 있었으면 기존 증거이므로 최초 삭제 요청일을 쓴다.
     * 취소 후 재요청된 뒤에 생긴 증거만 현재 주기 시작일을 쓴다. 기존 증거의 기한은 재요청으로 늘어나지 않는다.
     * 최초 삭제 요청일이 없으면 기준일을 정할 수 없으므로 null을 돌려준다.
     */
    public static LocalDateTime evidenceBase(LocalDateTime firstRequestedAt, LocalDateTime cycleStartedAt,
                                             LocalDateTime earliestReportAt) {
        if (firstRequestedAt == null) {
            return null;
        }
        if (cycleStartedAt == null || earliestReportAt == null || earliestReportAt.isBefore(cycleStartedAt)) {
            return firstRequestedAt;
        }
        return cycleStartedAt;
    }

    /**
     * 네이티브 쿼리가 돌려주는 시각 값을 서버 시간대의 LocalDateTime으로 바꾼다.
     * TIMESTAMP 컬럼은 java.sql.Timestamp로, TIMESTAMPTZ 컬럼은 드라이버에 따라 Instant나 OffsetDateTime으로 온다.
     * 그 밖의 타입은 받지 않는다. 잘못된 행이 전체 등록을 막지 않도록 호출하는 쪽에서 행 단위로 감싼다.
     */
    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.atZoneSameInstant(APP_ZONE).toLocalDateTime();
        }
        if (value instanceof java.time.Instant instant) {
            return instant.atZone(APP_ZONE).toLocalDateTime();
        }
        throw new IllegalArgumentException("지원하지 않는 시각 타입입니다: " + value.getClass().getSimpleName());
    }

    /**
     * 긴급 검토 유예가 끝나는 시각. 기본은 now + 유예이지만, 90일 상한에서 여유 기간을 뺀 시각을 넘지 않는다.
     * 상한이 가까울수록 유예가 짧아진다. 상한이 이미 지났으면 그 시각이 현재보다 앞서므로 다음 일일 작업에서 판단한다.
     */
    public static LocalDateTime urgentReviewDueAt(LocalDateTime now, LocalDateTime hardLimitAt) {
        LocalDateTime grace = now.plus(URGENT_REVIEW_GRACE);
        if (hardLimitAt == null) {
            return grace;
        }
        LocalDateTime deadline = hardLimitAt.minus(URGENT_DEADLINE_MARGIN);
        return grace.isBefore(deadline) ? grace : deadline;
    }

    /** 지금부터 최소 유예만큼 기다린 뒤에도 상한 여유 기간 전이어야 자동 삭제 경로를 쓸 수 있다. */
    public static boolean graceFitsBeforeCap(LocalDateTime now, LocalDateTime hardLimitAt) {
        return !now.plus(MIN_URGENT_GRACE).isAfter(hardLimitAt.minus(URGENT_DEADLINE_MARGIN));
    }

    /** 기본 기한과 승인된 연장 기한 중 늦은 쪽을 쓰되, 90일 상한을 넘지 않는다. */
    public static LocalDateTime effectiveUntil(LocalDateTime retainUntil, LocalDateTime hardLimitAt,
                                               LocalDateTime extensionUntil) {
        if (extensionUntil == null || !extensionUntil.isAfter(retainUntil)) {
            return retainUntil;
        }
        return extensionUntil.isBefore(hardLimitAt) ? extensionUntil : hardLimitAt;
    }

    /** 실패 횟수에 따라 늘어나는 재시도 시각. 10분부터 두 배씩 늘고 24시간을 넘지 않는다. */
    public static LocalDateTime nextAttemptAfter(int attemptCount, LocalDateTime now) {
        int shift = Math.min(Math.max(attemptCount - 1, 0), 10);
        long minutes = Math.min(RETRY_BASE_MINUTES << shift, RETRY_CAP_MINUTES);
        return now.plusMinutes(minutes);
    }
}
