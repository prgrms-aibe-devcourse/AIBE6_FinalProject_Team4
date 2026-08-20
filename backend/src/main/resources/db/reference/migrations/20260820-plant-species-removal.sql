-- AI 도입으로 관리자가 수동 관리하던 종(plant_species) 카탈로그가 더 이상 필요 없어져 제거하고,
-- plant_profile/products가 종 이름을 자유텍스트(species_name)로 직접 갖도록 전환한다.
-- FK 제약 이름은 초기 스키마 생성 시점(migrations 폴더 도입 이전) 값이라 저장소에 기록이 없다 —
-- 환경마다 실제 생성된 이름이 다를 수 있어 information_schema로 조회해 동적으로 드롭한다.

-- 1) products: plant_species_id → species_name
ALTER TABLE products
    ADD COLUMN species_name varchar(100) NULL AFTER stock;

UPDATE products p
    JOIN plant_species ps ON p.plant_species_id = ps.id
    SET p.species_name = ps.name;

SET @products_fk := (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'products'
      AND COLUMN_NAME = 'plant_species_id' AND REFERENCED_TABLE_NAME = 'plant_species'
    LIMIT 1
);
SET @drop_products_fk := CONCAT('ALTER TABLE products DROP FOREIGN KEY ', @products_fk);
PREPARE stmt FROM @drop_products_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE products
    DROP COLUMN plant_species_id;

-- 2) plant_profile: specie_id(FK, NOT NULL) → species_name(NOT NULL)
--    등록 시 항상 값이 있었으므로 백필 후 바로 NOT NULL로 못 박는다.
ALTER TABLE plant_profile
    ADD COLUMN species_name varchar(100) NULL AFTER user_id;

UPDATE plant_profile pp
    JOIN plant_species ps ON pp.specie_id = ps.id
    SET pp.species_name = ps.name;

ALTER TABLE plant_profile
    MODIFY COLUMN species_name varchar(100) NOT NULL;

SET @plant_profile_fk := (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'plant_profile'
      AND COLUMN_NAME = 'specie_id' AND REFERENCED_TABLE_NAME = 'plant_species'
    LIMIT 1
);
SET @drop_plant_profile_fk := CONCAT('ALTER TABLE plant_profile DROP FOREIGN KEY ', @plant_profile_fk);
PREPARE stmt FROM @drop_plant_profile_fk;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE plant_profile
    DROP INDEX idx_plant_profile_species_id,
    DROP COLUMN specie_id;

-- 3) ai_plant_care_guides: source_species_id는 애초에 물리 FK 없는 논리 참조였다(엔티티 주석 참고).
--    species 테이블이 사라지므로 더 이상 의미가 없어 컬럼만 제거한다. 캐시 자체(species_name 키)는 그대로 유지된다.
ALTER TABLE ai_plant_care_guides
    DROP COLUMN source_species_id;

-- 4) 더 이상 어떤 테이블도 참조하지 않으므로 카탈로그 테이블 자체를 제거한다.
DROP TABLE plant_species;
