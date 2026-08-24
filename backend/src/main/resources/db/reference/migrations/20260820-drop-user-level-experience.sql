-- 레벨/경험치 시스템 제거. 표시용 데이터일 뿐 다른 기능(권한/뽑기 확률/할인 등)이
-- 이 값을 참조하지 않아 컬럼째 삭제한다. ddl-auto: update는 컬럼을 자동으로 지우지
-- 않으므로 운영 DB에는 이 스크립트를 직접 실행해야 한다.
ALTER TABLE users
    DROP COLUMN level,
    DROP COLUMN experience;
