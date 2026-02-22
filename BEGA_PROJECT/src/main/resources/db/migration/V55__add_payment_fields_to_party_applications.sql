-- V55: Toss Payments 결제 연동을 위한 필드 추가
-- party_applications 테이블에 paymentKey, orderId 컬럼 추가

ALTER TABLE party_applications ADD (
    payment_key VARCHAR2(200),
    order_id    VARCHAR2(100)
);
