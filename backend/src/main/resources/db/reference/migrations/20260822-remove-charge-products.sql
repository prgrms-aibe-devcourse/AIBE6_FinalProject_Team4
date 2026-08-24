-- 직접 금액 충전만 사용하므로 정액 충전 상품과 payments의 legacy 상품 컬럼을 제거한다.
-- 이 저장소의 reference migration은 자동 실행되지 않으므로 기존 테스트 배포 DB에는 수동 적용한다.

-- CHECK 제약 추가 전에 기존 데이터가 동일한 조건을 만족하는지 검증한다.
-- 위반 행이 있으면 아래 INSERT가 ck_abort_remove_charge_products_invalid_payments 오류로 실패하며,
-- 이후 FK/컬럼/테이블 제거가 실행되지 않는다.
SET @invalid_direct_charge_payment_rows = (
    SELECT COUNT(*)
    FROM payments
    WHERE cash_amount IS NULL
       OR point_amount IS NULL
       OR (
           cash_amount = point_amount
           AND cash_amount BETWEEN 1000 AND 300000
           AND MOD(cash_amount, 10) = 0
       ) IS FALSE
);

SELECT @invalid_direct_charge_payment_rows AS invalid_direct_charge_payment_rows;

DROP TEMPORARY TABLE IF EXISTS migration_guard_20260822_remove_charge_products;

CREATE TEMPORARY TABLE migration_guard_20260822_remove_charge_products (
    invalid_payment_rows BIGINT NOT NULL,
    CONSTRAINT ck_abort_remove_charge_products_invalid_payments
        CHECK (invalid_payment_rows = 0)
);

INSERT INTO migration_guard_20260822_remove_charge_products (invalid_payment_rows)
VALUES (@invalid_direct_charge_payment_rows);

DROP TEMPORARY TABLE migration_guard_20260822_remove_charge_products;

SET @charge_product_fk = (
    SELECT constraint_name
    FROM information_schema.referential_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'payments'
      AND referenced_table_name = 'charge_products'
    LIMIT 1
);

SET @drop_charge_product_fk_sql = IF(
    @charge_product_fk IS NULL,
    'SELECT 1',
    CONCAT(
        'ALTER TABLE payments DROP FOREIGN KEY `',
        REPLACE(@charge_product_fk, '`', '``'),
        '`'
    )
);

PREPARE drop_charge_product_fk_statement FROM @drop_charge_product_fk_sql;
EXECUTE drop_charge_product_fk_statement;
DEALLOCATE PREPARE drop_charge_product_fk_statement;

SET @direct_charge_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'payments'
      AND constraint_name = 'ck_payments_direct_charge_amount'
      AND constraint_type = 'CHECK'
);

SET @drop_direct_charge_check_sql = IF(
    @direct_charge_check_exists > 0,
    'ALTER TABLE payments DROP CHECK ck_payments_direct_charge_amount',
    'SELECT 1'
);

PREPARE drop_direct_charge_check_statement FROM @drop_direct_charge_check_sql;
EXECUTE drop_direct_charge_check_statement;
DEALLOCATE PREPARE drop_direct_charge_check_statement;

SET @charge_product_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payments'
      AND column_name = 'charge_product_id'
);

SET @drop_charge_product_id_sql = IF(
    @charge_product_id_exists > 0,
    'ALTER TABLE payments DROP COLUMN charge_product_id',
    'SELECT 1'
);

PREPARE drop_charge_product_id_statement FROM @drop_charge_product_id_sql;
EXECUTE drop_charge_product_id_statement;
DEALLOCATE PREPARE drop_charge_product_id_statement;

SET @charge_product_name_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'payments'
      AND column_name = 'charge_product_name'
);

SET @drop_charge_product_name_sql = IF(
    @charge_product_name_exists > 0,
    'ALTER TABLE payments DROP COLUMN charge_product_name',
    'SELECT 1'
);

PREPARE drop_charge_product_name_statement FROM @drop_charge_product_name_sql;
EXECUTE drop_charge_product_name_statement;
DEALLOCATE PREPARE drop_charge_product_name_statement;

DROP TABLE IF EXISTS charge_products;

SET @new_direct_charge_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'payments'
      AND constraint_name = 'ck_payments_direct_charge_amount'
      AND constraint_type = 'CHECK'
);

SET @add_direct_charge_check_sql = IF(
    @new_direct_charge_check_exists = 0,
    'ALTER TABLE payments ADD CONSTRAINT ck_payments_direct_charge_amount CHECK (cash_amount = point_amount AND cash_amount BETWEEN 1000 AND 300000 AND MOD(cash_amount, 10) = 0)',
    'SELECT 1'
);

PREPARE add_direct_charge_check_statement FROM @add_direct_charge_check_sql;
EXECUTE add_direct_charge_check_statement;
DEALLOCATE PREPARE add_direct_charge_check_statement;

-- 적용 확인:
-- SELECT COUNT(*) FROM information_schema.tables
-- WHERE table_schema = DATABASE() AND table_name = 'charge_products';
-- SELECT column_name FROM information_schema.columns
-- WHERE table_schema = DATABASE() AND table_name = 'payments'
--   AND column_name IN ('charge_product_id', 'charge_product_name');
-- SELECT COUNT(*) AS invalid_payment_rows
-- FROM payments
-- WHERE cash_amount IS NULL
--    OR point_amount IS NULL
--    OR (
--        cash_amount = point_amount
--        AND cash_amount BETWEEN 1000 AND 300000
--        AND MOD(cash_amount, 10) = 0
--    ) IS FALSE;
