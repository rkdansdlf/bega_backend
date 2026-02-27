# Mate Payout Dark Launch Smoke

## 목적
- 스테이징에서 `PAYMENT_PAYOUT_PROVIDER=TOSS`, `PAYMENT_PAYOUT_ENABLED=false` 상태를 검증한다.
- 내부 지급 API가 호출되더라도 실제 지급 대신 `SKIPPED / PAYMENT_PAYOUT_DISABLED`로 기록되는지 확인한다.

## 사전 조건
- 스테이징 백엔드 배포 완료
- 내부 API 호출 가능한 인증 정보 확보
  - 우선순위 1: `ADMIN|SUPER_ADMIN` Bearer 토큰
  - 우선순위 2: `ADMIN_EMAIL`/`ADMIN_PASSWORD` (스크립트가 로그인 후 Authorization 쿠키 토큰 자동 추출)
- 테스트용 결제 트랜잭션 ID(`payment_transactions.id`) 1건 확보

## 실행
```bash
cd /Users/mac/project/KBO_platform

ADMIN_BEARER_TOKEN="<admin-jwt>" \
BACKEND_BASE_URL="https://<stage-backend-host>" \
PAYOUT_PROVIDER="TOSS" \
SELLER_USER_ID="101" \
PROVIDER_SELLER_ID="toss_seller_101" \
MATE_TEST_PAYMENT_ID="9001" \
EXPECTED_PAYOUT_STATUS="SKIPPED" \
EXPECTED_FAILURE_CODE="PAYMENT_PAYOUT_DISABLED" \
./scripts/mate_payout_dark_launch_smoke.sh
```

```bash
cd /Users/mac/project/KBO_platform/bega_backend

ADMIN_EMAIL="admin@example.com" \
ADMIN_PASSWORD="<admin-password>" \
BACKEND_BASE_URL="https://<stage-backend-host>" \
MATE_TEST_PAYMENT_ID="9001" \
./scripts/mate_payout_dark_launch_smoke.sh
```

## 확인 포인트
- `/actuator/health` 정상
- 메트릭 조회 정상:
  - `mate_settlement_payout_total`
  - `mate_refund_total`
  - `mate_payment_compensation_total`
- `POST /api/internal/payout/sellers` 등록/조회 정상
- `POST /api/internal/settlements/{paymentId}/payout` 결과:
  - `data.status == SKIPPED`
  - `data.failureCode == PAYMENT_PAYOUT_DISABLED`

## 전환 체크리스트 (`enabled=true` 전)
1. `PAYMENT_PAYOUT_PROVIDER=TOSS` 유지
2. `TOSS_PAYOUT_API_SECRET`, `TOSS_PAYOUT_ENCRYPTION_PUBLIC_KEY` 주입 확인
3. seller mapping 등록 완료(`mate_seller_payout_profiles`)
4. 알람 규칙 배포 확인:
   - `MateRefundFailed`
   - `MatePayoutFailStreak`
5. 다크런 스모크 성공 로그 확보 후 `PAYMENT_PAYOUT_ENABLED=true` 전환
