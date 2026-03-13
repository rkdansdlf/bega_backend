-- V115: 응원 게시판 고트래픽 성능 인덱스 추가
-- 수만 명 동시 접속 시 타임라인/좋아요 조회 병목 해소

-- 게시글: 작성자별 조회 가속 (프로필 페이지 "내 게시글" 풀스캔 방지)
CREATE INDEX IF NOT EXISTS idx_cheer_post_author_created
    ON cheer_post (author_id, createdat DESC);

-- 게시글 좋아요: post_id 기준 집계 쿼리 가속 (countByPostId 등)
CREATE INDEX IF NOT EXISTS idx_cheer_post_like_postid
    ON cheer_post_like (post_id);

-- 댓글 좋아요: comment_id 기준 집계 쿼리 가속
CREATE INDEX IF NOT EXISTS idx_cheer_comment_like_cid
    ON cheer_comment_like (comment_id);

-- 댓글: post_id + 시간순 정렬 가속 (댓글 목록 조회)
CREATE INDEX IF NOT EXISTS idx_cheer_comment_post_created
    ON cheer_comment (post_id, created_at DESC);
