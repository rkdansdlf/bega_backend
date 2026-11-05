package com.example.cheerboard.retention;

/** 신고 증거 미디어가 삭제 대기 상태로 넘어간 이유. 감사 이력과 운영 알림에 함께 남긴다. */
public enum ReportEvidenceDeleteReason {
    /** 사건이 종결되어 보존 근거가 없다. */
    CASE_CLOSED,
    /** 법적 보존 검토 기한이 지나 보존 근거가 사라졌다. 자동 삭제 결정에 따라 일반 규칙을 적용한다. */
    HOLD_LAPSED,
    /** 열린 사건이 긴급 검토 유예 안에 결정을 받지 못했다. */
    URGENT_GRACE_EXPIRED,
    /** 상한 여유 기간이 최소 유예보다 짧아 자동 삭제 대신 검토로 보냈다. 검토 대기 이벤트의 사유로만 쓴다. */
    OVER_CAP_NO_GRACE,
    /** 운영자가 검토 대기 기록의 삭제를 승인했다. 승인한 사건 번호와 사유를 이력에 남긴다. */
    OPERATOR_RELEASED
}
