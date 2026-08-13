-- 저장된 성장일지 사진의 AI 분석 상태와 구조화 결과를 보존한다.
-- journal_id는 일지 삭제·사진 교체 뒤에도 과거 분석 이력을 남기기 위한 논리 참조라 FK를 두지 않는다.
CREATE TABLE IF NOT EXISTS ai_journal_image_analyses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    journal_id BIGINT NOT NULL,
    image_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    result_json LONGTEXT NULL,
    model VARCHAR(100) NULL,
    claim_token VARCHAR(36) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_journal_image_analysis_journal_hash UNIQUE (journal_id, image_hash),
    INDEX idx_ai_journal_image_analysis_journal_status (journal_id, status),
    CONSTRAINT chk_ai_journal_image_analysis_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT chk_ai_journal_image_analysis_result
        CHECK (
            (status = 'COMPLETED' AND result_json IS NOT NULL AND model IS NOT NULL)
            OR (status IN ('PENDING', 'FAILED') AND result_json IS NULL)
        ),
    CONSTRAINT chk_ai_journal_image_analysis_claim
        CHECK (
            (status = 'PENDING' AND claim_token IS NOT NULL)
            OR (status IN ('COMPLETED', 'FAILED') AND claim_token IS NULL)
        )
);
