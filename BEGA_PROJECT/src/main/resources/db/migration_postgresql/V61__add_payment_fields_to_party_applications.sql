-- V61: Toss Payments 결제 연동을 위한 필드 추가
-- party_applications 테이블에 payment_key, order_id 컬럼 추가

ALTER TABLE party_applications
    ADD COLUMN payment_key VARCHAR(200),
    ADD COLUMN order_id    VARCHAR(100);
