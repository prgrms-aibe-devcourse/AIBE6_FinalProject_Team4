-- EXPAND PHASE ONLY
-- 과거 결제 내역의 상품명이 관리자 상품명 수정에 따라 바뀌지 않도록 거래 당시 이름을 저장한다.
--
-- 현재 배포 workflow는 db/reference/migrations SQL을 자동 실행하지 않고 운영의
-- spring.jpa.hibernate.ddl-auto=update가 엔티티 매핑을 반영한다. 따라서 Payment 엔티티도
-- 이 단계에서는 nullable로 유지하며, SQL을 수동으로 먼저 적용하지 않아도 신규 애플리케이션이
-- nullable 컬럼을 추가하고 null/blank legacy 값을 조회할 수 있다.
--
-- 이 reference SQL은 배포 전후 어느 쪽에서도 수동 실행할 수 있도록 컬럼 존재 여부를 확인한다.
-- 구버전 인스턴스와 안전하게 공존하고 롤백할 수 있도록 이번 단계에서는 NOT NULL로 전환하지 않는다.
-- 모든 인스턴스가 신규 컬럼을 쓰는 버전으로 교체되고 backfill 잔여 건이 0인지 확인한 뒤,
-- 별도의 contract migration에서 NOT NULL 전환을 수행한다.
SET @charge_product_name_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payments'
      AND column_name = 'charge_product_name'
);

SET @add_charge_product_name_sql = IF(
    @charge_product_name_exists = 0,
    'ALTER TABLE payments ADD COLUMN charge_product_name varchar(50) NULL AFTER charge_product_id',
    'SELECT 1'
);

PREPARE add_charge_product_name_statement FROM @add_charge_product_name_sql;
EXECUTE add_charge_product_name_statement;
DEALLOCATE PREPARE add_charge_product_name_statement;

-- 혹시 자동 DDL이나 이전 시도로 NOT NULL 컬럼이 먼저 만들어졌더라도 expand 상태로 복구한다.
ALTER TABLE payments
    MODIFY COLUMN charge_product_name varchar(50) NULL;

UPDATE payments AS p
INNER JOIN charge_products AS cp ON cp.id = p.charge_product_id
SET p.charge_product_name = cp.name
WHERE p.charge_product_name IS NULL
   OR CHAR_LENGTH(TRIM(p.charge_product_name)) = 0;

-- contract 단계 전 운영 확인 쿼리(이번 expand migration에서는 제약을 닫지 않는다):
-- SELECT COUNT(*) AS remaining_legacy_rows
-- FROM payments
-- WHERE charge_product_name IS NULL
--    OR CHAR_LENGTH(TRIM(charge_product_name)) = 0;
--
-- 모든 애플리케이션 인스턴스가 스냅샷을 기록하는 버전이고 위 결과가 0일 때만 후속 migration에서:
-- ALTER TABLE payments MODIFY COLUMN charge_product_name varchar(50) NOT NULL;
