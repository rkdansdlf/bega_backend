-- Issue #48: 이메일 회원가입 인증.
--
-- 기존 계정 처리: 이 마이그레이션 시점에 이미 있던 모든 계정은 인증 완료로 간주한다(grandfather).
--   * 기존 사용자(모니터링 계정 @bega_monitor 포함)가 배포 직후 로그인에서 잠기지 않게 한다.
--   * 기본값 TRUE 로 컬럼을 추가해 기존 행을 한 번에 채운 뒤(PostgreSQL 11+ 는 테이블 재작성 없음),
--     이후 신규 행의 기본값은 FALSE 로 바꾼다. 애플리케이션은 가입 경로마다 값을 명시적으로 넣는다.
--   * grandfather 계정은 email_verified_at 이 NULL 로 남아 신규 인증 계정과 구분된다.
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;

-- 모니터링 계정은 명시적으로 인증 상태를 보장한다(위 grandfather 와 겹치지만 의도를 남긴다).
UPDATE users
   SET email_verified = TRUE
 WHERE lower(btrim(email)) = 'baseballguide251021+monitor@gmail.com';

CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_digest VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_verif_tokens_digest
    ON email_verification_tokens (token_digest);
CREATE INDEX IF NOT EXISTS ix_email_verif_tokens_user
    ON email_verification_tokens (user_id);
