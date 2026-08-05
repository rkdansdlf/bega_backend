-- V178 이 exact_match_count 를 SMALLINT 로 만들었으나 RankingPrediction 엔티티는
-- Integer 로 매핑한다. Oracle 쪽(V172)은 NUMBER(2) 라 Hibernate 가 INTEGER 로 읽어
-- 통과했고, PostgreSQL 에서만 int2 로 잡혀 ddl-auto=validate 가 실패한다.
--
-- V178 은 이미 적용된 환경이 있어 수정하지 않고 여기서 타입만 맞춘다.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM information_schema.columns
         WHERE table_name = 'ranking_predictions'
           AND column_name = 'exact_match_count'
           AND data_type = 'smallint'
    ) THEN
        ALTER TABLE ranking_predictions
            ALTER COLUMN exact_match_count TYPE INTEGER;
    END IF;
END $$;
