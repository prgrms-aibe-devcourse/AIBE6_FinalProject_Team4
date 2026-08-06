-- source_context_hash가 NULL이면 MySQL 유니크 인덱스는 NULL끼리를 서로 다른 값으로 취급한다.
-- 즉 (species_name, guide_version, source_context_hash) 제약이 같은 종·같은 버전의 중복 행을
-- 전혀 막지 못한다. 캐시 중복 저장 방어(DataIntegrityViolationException 재조회)가 이 제약에
-- 의존하므로 컬럼을 NOT NULL로 못 박는다. 애플리케이션은 분류·공식 가이드가 없는 종도 빈 문자열을
-- 해싱해 항상 64자 해시를 채운다.
--
-- 20260805 마이그레이션 이전에 쌓인 행은 원본 컨텍스트를 알 수 없어 해시를 복원할 수 없다.
-- 조회는 언제나 non-null 해시로 나가 이 행들은 이미 읽히지 않는 죽은 캐시이므로 삭제한다
-- (재생성 가능한 캐시라 유실 비용은 해당 종의 AI 호출 1회뿐이다).
DELETE FROM ai_plant_care_guides WHERE source_context_hash IS NULL;

ALTER TABLE ai_plant_care_guides
    MODIFY COLUMN source_context_hash varchar(64) NOT NULL;
