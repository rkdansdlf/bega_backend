-- V93: 사용자 계정 상태 관리 필드 추가 (중복되지 않게 한 번만 반영)
-- [Security Fix] 계정 비활성화/잠금 기능 지원

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD enabled NUMBER(1) DEFAULT 1 NOT NULL';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -01430 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD locked NUMBER(1) DEFAULT 0 NOT NULL';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -01430 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'ALTER TABLE users ADD lock_expires_at TIMESTAMP';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -01430 THEN
            RAISE;
        END IF;
END;
/

BEGIN
    BEGIN
        EXECUTE IMMEDIATE 'COMMENT ON COLUMN users.enabled IS ''계정 활성화 여부 (0=비활성화, 1=활성화)''';
    EXCEPTION
        WHEN OTHERS THEN
            NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'COMMENT ON COLUMN users.locked IS ''계정 잠금 여부 (0=정상, 1=잠금)''';
    EXCEPTION
        WHEN OTHERS THEN
            NULL;
    END;

    BEGIN
        EXECUTE IMMEDIATE 'COMMENT ON COLUMN users.lock_expires_at IS ''계정 잠금 해제 예정 시간 (null이면 영구 잠금 또는 잠금 없음)''';
    EXCEPTION
        WHEN OTHERS THEN
            NULL;
    END;
END;
/
