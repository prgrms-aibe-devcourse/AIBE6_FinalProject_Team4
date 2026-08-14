-- 관리자 충전 상품 수정의 lost update를 막기 위한 JPA 낙관적 락 버전이다.
-- 기존 행은 version=0으로 시작하고 이후 UPDATE 성공마다 Hibernate가 1씩 증가시킨다.
ALTER TABLE charge_products
    ADD COLUMN version bigint NOT NULL DEFAULT 0 AFTER is_active;
