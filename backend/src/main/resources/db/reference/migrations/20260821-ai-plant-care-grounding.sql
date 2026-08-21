-- 재배가이드·챗봇·사진분석이 공통으로 사용하는 공식 문서 근거 상태·적용 범위와 출처 메타데이터를 보존한다.
-- 공식 문서 원문은 코퍼스가 소유하며, 캐시와 분석 결과에는 추적에 필요한 출처·버전·내용 해시만 저장한다.

ALTER TABLE ai_plant_care_guides
    ADD COLUMN evidence_status varchar(30) NULL AFTER source_context_hash,
    ADD COLUMN evidence_sources_json longtext NULL AFTER evidence_status;

-- 기존 캐시는 새 검색기 fingerprint로 조회되지 않는다. 출처 목록을 복원할 수 없으므로 legacy 행은
-- GENERAL_FALLBACK으로 표시하고, 새 요청에서는 새 source_context_hash로 가이드를 다시 생성한다.
UPDATE ai_plant_care_guides
   SET evidence_status = 'GENERAL_FALLBACK',
       evidence_sources_json = '[]'
 WHERE evidence_status IS NULL;

ALTER TABLE ai_plant_care_guides
    MODIFY COLUMN evidence_status varchar(30) NOT NULL,
    MODIFY COLUMN evidence_sources_json longtext NOT NULL,
    ADD CONSTRAINT chk_ai_plant_care_guide_evidence_status
        CHECK (evidence_status IN ('VERIFIED', 'GENERAL_FALLBACK')),
    ADD CONSTRAINT chk_ai_plant_care_guide_evidence_sources_json
        CHECK (json_valid(evidence_sources_json));

ALTER TABLE ai_journal_image_analyses
    ADD COLUMN evidence_status varchar(30) NULL AFTER model,
    ADD COLUMN evidence_scope varchar(30) NULL AFTER evidence_status,
    ADD COLUMN evidence_species_name varchar(100) NULL AFTER evidence_scope,
    ADD COLUMN source_context_hash varchar(64) NULL AFTER evidence_species_name,
    ADD COLUMN evidence_sources_json longtext NULL AFTER source_context_hash;

-- 과거 분석은 공식 문서 검색을 사용하지 않았으므로 일반 지식 응답으로 명확히 구분한다.
UPDATE ai_journal_image_analyses
   SET evidence_status = 'GENERAL_FALLBACK',
       evidence_scope = 'NONE',
       source_context_hash = sha2(concat('legacy-image-analysis:', id), 256),
       evidence_sources_json = '[]'
 WHERE status = 'COMPLETED';

ALTER TABLE ai_journal_image_analyses
    ADD CONSTRAINT chk_ai_journal_image_analysis_evidence_status
        CHECK (evidence_status IS NULL OR evidence_status IN ('VERIFIED', 'GENERAL_FALLBACK')),
    ADD CONSTRAINT chk_ai_journal_image_analysis_evidence_scope
        CHECK (evidence_scope IS NULL OR evidence_scope IN ('EXACT_SPECIES', 'BASE_SPECIES', 'NONE')),
    ADD CONSTRAINT chk_ai_journal_image_analysis_evidence_sources_json
        CHECK (evidence_sources_json IS NULL OR json_valid(evidence_sources_json)),
    ADD CONSTRAINT chk_ai_journal_image_analysis_evidence
        CHECK (
            (status = 'COMPLETED'
                AND evidence_status IS NOT NULL
                AND evidence_scope IS NOT NULL
                AND ((evidence_status = 'GENERAL_FALLBACK' AND evidence_scope = 'NONE')
                    OR (evidence_status = 'VERIFIED'
                        AND evidence_scope IN ('EXACT_SPECIES', 'BASE_SPECIES')
                        AND evidence_species_name IS NOT NULL))
                AND source_context_hash IS NOT NULL
                AND evidence_sources_json IS NOT NULL)
            OR (status IN ('PENDING', 'FAILED')
                AND evidence_status IS NULL
                AND evidence_scope IS NULL
                AND evidence_species_name IS NULL
                AND source_context_hash IS NULL
                AND evidence_sources_json IS NULL)
        );
