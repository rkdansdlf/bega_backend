package com.example.common.clienterror;

import java.time.LocalDateTime;

public interface ClientErrorEventSummaryProjection {

    String getEventId();

    ClientErrorBucket getBucket();

    ClientErrorSource getSource();

    String getMessage();

    Integer getStatusCode();

    String getStatusGroup();

    String getResponseCode();

    String getRoute();

    String getNormalizedRoute();

    String getMethod();

    String getEndpoint();

    String getNormalizedEndpoint();

    String getFingerprint();

    LocalDateTime getOccurredAt();

    String getSessionId();

    Long getUserId();

    Integer getFeedbackCount();
}
