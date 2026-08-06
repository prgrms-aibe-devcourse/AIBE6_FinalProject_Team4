-- 공식 재배 가이드·분류가 바뀌면 기존 AI 캐시를 재사용하지 않도록 원본 fingerprint를 저장한다.
ALTER TABLE ai_plant_care_guides
    DROP INDEX uq_ai_plant_care_guide_species_name_version,
    ADD COLUMN source_context_hash varchar(64) NULL AFTER source_species_id,
    ADD CONSTRAINT uq_ai_plant_care_guide_species_name_version
        UNIQUE (species_name, guide_version, source_context_hash);
