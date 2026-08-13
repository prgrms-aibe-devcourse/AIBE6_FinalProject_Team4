-- 기존 원장과의 조회 호환성을 위해 nullable로 추가한다.
-- 신규 ADMIN_ADJUST 원장의 사유 필수·증감별 허용값 검증은 애플리케이션 서비스가 담당한다.
ALTER TABLE point_transactions
    ADD COLUMN adjustment_reason varchar(30) NULL AFTER ref_id;
