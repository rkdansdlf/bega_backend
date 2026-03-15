package com.example.bega.auth.dto;

/**
 * OAuth2 계정 연동용 one-time ticket 저장 데이터
 */
public record OAuth2LinkTicketData(
        Long userId,
        long issuedAt,
        boolean consumed
) {
    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    public static OAuth2LinkTicketData issued(Long userId) {
        return new OAuth2LinkTicketData(userId, System.currentTimeMillis(), false);
    }

    public OAuth2LinkTicketData consumedMarker() {
        return new OAuth2LinkTicketData(userId, issuedAt, true);
    }

    public boolean isExpired() {
        return remainingTtlMillis() <= 0;
    }

    public long remainingTtlMillis() {
        return TTL_MILLIS - Math.max(0L, System.currentTimeMillis() - issuedAt);
    }
}
