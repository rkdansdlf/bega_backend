package com.example.common.clienterror;

record ClientErrorAlertPayload(
        ClientErrorBucket bucket,
        ClientErrorSource source,
        long count,
        int windowMinutes,
        String route,
        String statusGroup,
        String latestEventId,
        String fingerprint,
        String latestMessage,
        String adminUrl) {
}
