package com.example.mate.entity;

public enum SettlementStatus {
    PENDING,
    REQUESTED,
    COMPLETED,
    FAILED,
    SKIPPED,
    /** 판매자에게 정산 완료 후 구매자에게 환불된 상태. 판매자로부터 환수 필요. */
    REFUNDED_AFTER_SETTLEMENT
}
