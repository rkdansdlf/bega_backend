-- Orphan author_id 점검/정리 스크립트
-- 대상: cheer_post.author_id 가 users.id 와 매칭되지 않는 레코드 조회

-- 1) orphan 게시글 목록 조회 (PostgreSQL/Oracle 공통)
SELECT cp.id AS orphan_post_id,
       cp.author_id,
       cp.createdat,
       cp.team_id,
       cp.posttype,
       cp.deleted,
       cp.content
FROM cheer_post cp
WHERE cp.author_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM users u
      WHERE u.id = cp.author_id
  )
ORDER BY cp.createdat DESC;

-- 2) 정합성 정책 예시
--    A) soft-delete 정책 유지: 삭제 처리만 수행
--       (아카이브/삭제 전, 실제 운영 반영 정책과 일치하는 방식만 사용)

--    B) 임시 익명 유저로 대체:
--       UPDATE cheer_post cp
--          SET author_id = :anonymous_user_id
--        WHERE cp.author_id IS NOT NULL
--          AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = cp.author_id);
--
--    C) 게시글 자체 아카이브/삭제:
--       DELETE FROM cheer_post cp
--        WHERE cp.author_id IS NOT NULL
--          AND NOT EXISTS (SELECT 1 FROM users u WHERE u.id = cp.author_id);
