package com.example.common.clienterror;

import java.time.LocalDateTime;

public interface ClientErrorTopFingerprintProjection {

    String getFingerprint();

    long getEventCount();

    long getUniqueSessions();

    LocalDateTime getLatestOccurredAt();
}
