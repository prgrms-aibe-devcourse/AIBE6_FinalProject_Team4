-- 계정별 KST 일일 일지 작성 리마인더 발송 여부를 원자적으로 잠그는 전용 테이블이다.
-- notification 테이블의 (user_id, type, ref_type, ref_id) 인덱스는 다른 알림 종류들이
-- 배송 시작→완료처럼 같은 refType/refId를 여러 번 재사용해서 테이블 전체에 유니크 제약을
-- 걸 수 없다 — 이 리마인더만 계정·날짜당 최대 1건이 보장되면 되므로 별도 테이블에
-- 유니크 제약을 둬서 로그인/토큰 재발급이 동시에 들어와도 DB가 원자적으로 승자를 하나만
-- 남긴다. ddl-auto: update가 새 테이블은 자동으로 만들어주므로 이 스크립트는 참고용이다.
CREATE TABLE IF NOT EXISTS journal_reminder_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reminder_date DATE NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_journal_reminder_logs_user_date UNIQUE (user_id, reminder_date),
    CONSTRAINT fk_journal_reminder_logs_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);
