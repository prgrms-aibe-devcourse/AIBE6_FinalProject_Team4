-- 정액 충전 상품 선택 방식에서 사용자가 금액을 직접 입력하는 1원=1P 충전 방식으로 전환한다.
-- 기존 결제/환불 이력과 롤백 가능성을 위해 charge_products 테이블과 과거 charge_product_id 값은 유지한다.
-- 신규 결제는 charge_product_id를 null로 저장하며 금액/포인트 스냅샷만 사용한다.
-- 과거 이력의 참조 무결성을 위해 기존 charge_products FK는 유지한다.

SET @charge_products_exists = (
    SELECT COUNT(*)
    FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name = 'charge_products'
);

SET @backfill_charge_name_sql = IF(
    @charge_products_exists > 0,
    'UPDATE payments AS p LEFT JOIN charge_products AS cp ON cp.id = p.charge_product_id SET p.charge_product_name = COALESCE(NULLIF(TRIM(p.charge_product_name), ''''), cp.name, CONCAT(FORMAT(p.point_amount, 0), ''P 충전'')) WHERE p.charge_product_name IS NULL OR CHAR_LENGTH(TRIM(p.charge_product_name)) = 0',
    'UPDATE payments SET charge_product_name = CONCAT(FORMAT(point_amount, 0), ''P 충전'') WHERE charge_product_name IS NULL OR CHAR_LENGTH(TRIM(charge_product_name)) = 0'
);

PREPARE backfill_charge_name_statement FROM @backfill_charge_name_sql;
EXECUTE backfill_charge_name_statement;
DEALLOCATE PREPARE backfill_charge_name_statement;

ALTER TABLE payments
    MODIFY COLUMN charge_product_id bigint NULL,
    MODIFY COLUMN charge_product_name varchar(50) NOT NULL;

SET @direct_charge_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE()
      AND table_name = 'payments'
      AND constraint_name = 'ck_payments_direct_charge_amount'
      AND constraint_type = 'CHECK'
);

SET @add_direct_charge_check_sql = IF(
    @direct_charge_check_exists = 0,
    'ALTER TABLE payments ADD CONSTRAINT ck_payments_direct_charge_amount CHECK (charge_product_id IS NOT NULL OR (cash_amount = point_amount AND cash_amount BETWEEN 1000 AND 300000 AND MOD(cash_amount, 10) = 0))',
    'SELECT 1'
);

PREPARE add_direct_charge_check_statement FROM @add_direct_charge_check_sql;
EXECUTE add_direct_charge_check_statement;
DEALLOCATE PREPARE add_direct_charge_check_statement;

-- 운영 확인:
-- SELECT COUNT(*) AS invalid_direct_charge_rows
-- FROM payments
-- WHERE charge_product_id IS NULL
--   AND (cash_amount <> point_amount
--        OR cash_amount NOT BETWEEN 1000 AND 300000
--        OR MOD(cash_amount, 10) <> 0);
--
-- charge_products는 과거 이력 확인과 롤백을 위해 이번 migration에서 삭제하지 않는다.
