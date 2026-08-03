-- 가챠 구매 최종 실패 환불 상태 추가 (MySQL 8)
-- 운영 반영 전 실행한다. 애플리케이션의 ddl-auto=update는 기존 CHECK를 갱신하지 않는다.

ALTER TABLE gacha_draws
    DROP CHECK ck_gacha_draws_status;

ALTER TABLE gacha_draws
    ADD CONSTRAINT ck_gacha_draws_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'RETRYABLE_FAILED',
            'MANUAL_REVIEW',
            'REFUNDED'
        )
    );
