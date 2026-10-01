package com.example.auth.service;

/**
 * 테스트 전용. digest 를 원문 그대로 돌려줘서 테스트가 저장된 digest 와 원문을 직접 비교할 수 있게 한다.
 * 운영 코드에는 두지 않는다 — 생성자 우회로 원문이 digest 컬럼에 저장되는 경로가 생기면 안 된다.
 */
public class IdentityRefreshTokenDigestService extends RefreshTokenDigestService {

    public IdentityRefreshTokenDigestService() {
        super("test-refresh-token-pepper-value");
    }

    @Override
    public String digest(String rawToken) {
        return rawToken;
    }
}
