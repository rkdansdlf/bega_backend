package com.example.common.realtime;

public enum RealtimeOutboxStatus {
    PENDING,
    PROCESSING,
    RETRY,
    PUBLISHED,
    DEAD
}
