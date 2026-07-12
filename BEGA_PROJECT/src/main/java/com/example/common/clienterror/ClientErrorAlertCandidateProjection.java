package com.example.common.clienterror;

import java.time.LocalDateTime;

public interface ClientErrorAlertCandidateProjection {

    String getFingerprint();

    long getObservedCount();

    LocalDateTime getLatestOccurredAt();
}
