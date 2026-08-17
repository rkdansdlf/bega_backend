-- ============================================================
-- V2: 크롤러 테이블에 KBO_platform 소유 컬럼을 덧붙인다
--
-- ⚠️ **크롤러가 스키마를 만든 다음에 실행해야 한다.**
--    python3 -m src.cli.apply_oracle_migrations   (KBO_playwright)
--
-- ── 왜 필요한가 ───────────────────────────────────────────
-- 야구 테이블의 소유자는 크롤러(KBO_playwright)지만, 이 저장소가 읽는 컬럼 중
-- 일부는 크롤러 스키마에 없다. 크롤러가 수집하는 데이터가 아니라 **우리 운영자가
-- 관리자 화면에서 입력하는 값**이기 때문이다.
--
--   OffseasonMovementAdminService.applyRequest() 가 아래 9개를 직접 쓴다:
--     setDetails / setSummary / setContractTerm / setContractValue /
--     setOptionDetails / setCounterpartyTeam / setCounterpartyDetails /
--     setSourceLabel / setSourceUrl / setAnnouncedAt
--
-- 그래서 크롤러 ORM 에 떠넘기지 않고 우리가 덧붙인다. 크롤러는 이 컬럼들의
-- 존재를 몰라도 되고(모두 nullable), 우리는 우리 기능을 우리가 책임진다.
--
-- 이 파일이 없으면 ADB 전환 시 Hibernate 가 매핑에 실패한다.
-- drift 게이트(scripts/check_baseball_schema_drift.py)가 그 목록을 관리한다.
--
-- ── 여기 없는 것: awards.award_year ──────────────────────
-- 그건 누락이 아니라 **이름 불일치**다. 크롤러는 `year`, 우리 엔티티는
-- `award_year` 로 매핑한다. 같은 의미이므로 컬럼을 더 만들면 안 되고,
-- ADB 전환 시점에 **엔티티를 크롤러 이름에 맞추는** 것이 옳다.
-- 지금 바꾸면 아직 운영 중인 레거시 PostgreSQL(award_year) 읽기가 깨진다.
--
-- ⚠️ 미검증 (2026-08-17). ADB 접속이 ACL 로 막혀 실제 적용 확인을 못 했다.
-- ============================================================

DECLARE
    -- 컬럼이 이미 있으면 넘어간다(ORA-01430). 그 외 오류는 그대로 올린다.
    PROCEDURE add_column(p_table VARCHAR2, p_ddl VARCHAR2) IS
        e_column_exists EXCEPTION;
        PRAGMA EXCEPTION_INIT(e_column_exists, -1430);
        e_no_table EXCEPTION;
        PRAGMA EXCEPTION_INIT(e_no_table, -942);
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE ' || p_table || ' ADD ' || p_ddl;
    EXCEPTION
        WHEN e_column_exists THEN NULL;
        WHEN e_no_table THEN
            -- 조용히 넘어가면 앱이 기동은 하고 첫 쿼리에서 죽는다. 여기서 멈춘다.
            RAISE_APPLICATION_ERROR(
                -20001,
                '테이블 ' || p_table || ' 이(가) 없다. 크롤러 스키마를 먼저 만들 것: '
                || 'python3 -m src.cli.apply_oracle_migrations (KBO_playwright)');
    END;
BEGIN

    -- ── player_movements: 오프시즌 이적 상세 (운영자 입력) ──
    add_column('player_movements', 'details CLOB');
    add_column('player_movements', 'summary VARCHAR2(300 CHAR)');
    add_column('player_movements', 'contract_term VARCHAR2(100 CHAR)');
    add_column('player_movements', 'contract_value VARCHAR2(120 CHAR)');
    add_column('player_movements', 'option_details VARCHAR2(300 CHAR)');
    add_column('player_movements', 'counterparty_team VARCHAR2(50 CHAR)');
    add_column('player_movements', 'counterparty_details VARCHAR2(500 CHAR)');
    add_column('player_movements', 'source_label VARCHAR2(100 CHAR)');
    add_column('player_movements', 'source_url VARCHAR2(500 CHAR)');
    add_column('player_movements', 'announced_at TIMESTAMP');

    -- ── game: 더미 경기 필터 ──
    -- 크롤러의 is_primary 와는 다른 개념이다. 우리 예측/홈 화면이 더미 행을
    -- 걸러내는 데 쓴다(PredictionService, GameRepository 네이티브 쿼리).
    add_column('game', 'is_dummy NUMBER(1)');

    -- ── teams: 팀 표시 색상 ──
    -- 응원석 피드 렌더링에 쓴다(CheerFeedService, PostDtoMapper).
    add_column('teams', 'color VARCHAR2(255 CHAR)');

END;
/
