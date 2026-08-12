-- 동일 멱등키의 최초 동시 요청에서 실제 INSERT 선점자를 구분하기 위한 내부 토큰이다.
-- 기존 행과 구버전 인스턴스의 쓰기를 허용해야 하므로 nullable expand 컬럼으로 추가한다.
ALTER TABLE idempotency_keys
    ADD COLUMN claim_token varchar(36) NULL AFTER request_hash;
