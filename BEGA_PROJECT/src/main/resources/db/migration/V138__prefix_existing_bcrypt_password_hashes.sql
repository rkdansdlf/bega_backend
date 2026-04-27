-- [Security Fix - Critical #2] 기존 평문 BCrypt 해시에 {bcrypt} prefix를 추가한다.
-- Spring Security의 DelegatingPasswordEncoder는 prefix로 알고리즘을 구분하므로,
-- prefix가 없는 legacy 해시는 더 이상 matches()에서 검증되지 않는다.
-- 기존 BCrypt 해시는 모두 $2a$ / $2b$ / $2y$로 시작하므로 안전하게 변환 가능하다.
-- Argon2id 해시는 $argon2id$로 시작하며 {argon2} prefix는 별도 조건으로 제외된다.

UPDATE users
SET password = '{bcrypt}' || password
WHERE password IS NOT NULL
  AND password NOT LIKE '{bcrypt}%'
  AND password NOT LIKE '{argon2}%'
  AND (password LIKE '$2a$%' OR password LIKE '$2b$%' OR password LIKE '$2y$%');
