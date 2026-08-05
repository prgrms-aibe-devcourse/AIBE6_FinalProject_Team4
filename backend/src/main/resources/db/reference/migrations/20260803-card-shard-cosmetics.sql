-- 수동 적용 순서: user_card_shard_wallets -> card_shard_transactions -> user_card_cosmetics
-- 적용 전 백업 및 대상 DB의 기존 동명 테이블 유무를 확인한다.

CREATE TABLE user_card_shard_wallets (
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    lifetime_earned BIGINT NOT NULL DEFAULT 0,
    lifetime_spent BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_user_card_shard_wallets_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_card_shard_wallets_balance CHECK (balance >= 0),
    CONSTRAINT ck_user_card_shard_wallets_earned CHECK (lifetime_earned >= 0),
    CONSTRAINT ck_user_card_shard_wallets_spent CHECK (lifetime_spent >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_shard_transactions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    card_id BIGINT NULL,
    cosmetic_code VARCHAR(100) NULL,
    card_quantity INT NULL,
    shard_per_card_snapshot BIGINT NULL,
    amount BIGINT NOT NULL,
    balance_after BIGINT NOT NULL,
    request_id BIGINT NOT NULL,
    line_no INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_card_shard_transactions_request_line (user_id, request_id, line_no),
    KEY idx_card_shard_transactions_user_created (user_id, created_at),
    KEY idx_card_shard_transactions_card (card_id),
    CONSTRAINT fk_card_shard_transactions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_card_shard_transactions_card FOREIGN KEY (card_id) REFERENCES trading_cards (id),
    CONSTRAINT ck_card_shard_transactions_type CHECK (
        transaction_type IN ('DISMANTLE_EARN', 'COSMETIC_SPEND')
    ),
    CONSTRAINT ck_card_shard_transactions_balance CHECK (balance_after >= 0),
    CONSTRAINT ck_card_shard_transactions_payload CHECK (
        (transaction_type = 'DISMANTLE_EARN' AND card_id IS NOT NULL
            AND cosmetic_code IS NULL AND card_quantity > 0
            AND shard_per_card_snapshot > 0 AND amount > 0)
        OR
        (transaction_type = 'COSMETIC_SPEND' AND card_id IS NULL
            AND cosmetic_code IS NOT NULL AND card_quantity IS NULL
            AND shard_per_card_snapshot IS NULL AND amount < 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE user_card_cosmetics (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    cosmetic_code VARCHAR(100) NOT NULL,
    cosmetic_type VARCHAR(20) NOT NULL,
    shard_price_snapshot BIGINT NOT NULL,
    unlocked_at DATETIME(6) NOT NULL,
    equipped_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_card_cosmetics_user_code (user_id, cosmetic_code),
    KEY idx_user_card_cosmetics_user_type (user_id, cosmetic_type),
    CONSTRAINT fk_user_card_cosmetics_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_card_cosmetics_type CHECK (cosmetic_type IN ('TITLE', 'BORDER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
