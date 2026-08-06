-- 캐시 미스 경쟁에서 실제 AI 호출을 한 요청으로 제한하는 짧은 생성 lease.
-- locked_until 이후에는 다음 요청이 같은 키를 다시 선점할 수 있다.
CREATE TABLE ai_plant_care_guide_generation_locks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    species_name VARCHAR(100) NOT NULL,
    guide_version INT NOT NULL,
    source_context_hash VARCHAR(64) NOT NULL,
    locked_until DATETIME(6) NOT NULL,
    owner_token VARCHAR(36) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_ai_plant_care_guide_generation_lock
        UNIQUE (species_name, guide_version, source_context_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
