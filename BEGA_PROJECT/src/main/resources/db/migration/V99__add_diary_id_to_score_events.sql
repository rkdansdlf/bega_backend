-- V99: score_events 테이블에 diary_id 컬럼 추가 (좌석 시야 UGC 리워드 idempotency용)
ALTER TABLE score_events ADD (diary_id NUMBER(19));
