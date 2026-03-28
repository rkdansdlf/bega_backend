-- V115: 응원 게시판 고트래픽 성능 인덱스 추가
-- 수만 명 동시 접속 시 타임라인/좋아요 조회 병목 해소

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_author_created ON cheer_post (author_id, createdat DESC)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_post_like_postid ON cheer_post_like (post_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_comment_like_cid ON cheer_comment_like (comment_id)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
    EXECUTE IMMEDIATE 'CREATE INDEX idx_cheer_comment_post_created ON cheer_comment (post_id, created_at DESC)';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/
