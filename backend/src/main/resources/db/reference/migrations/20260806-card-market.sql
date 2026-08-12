-- 카드 거래소(card-market) 물리 스키마 정본.
-- 기존 가챠·포인트 테이블은 변경하지 않으며 운영 반영 전 MySQL 8에서 명시적으로 실행한다.

CREATE TABLE card_market_listings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    seller_user_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL,
    golden_instance_id BIGINT NULL,
    asset_type VARCHAR(20) NOT NULL,
    asking_price BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    closed_reason VARCHAR(30) NULL,
    expires_at DATETIME(6) NOT NULL,
    sold_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    active_golden_instance_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'OPEN' THEN golden_instance_id ELSE NULL END
    ) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uk_card_market_listings_active_golden (active_golden_instance_id),
    KEY idx_card_market_listings_status_created (status, created_at, id),
    KEY idx_card_market_listings_status_price (status, asking_price, id),
    KEY idx_card_market_listings_card_status_price (card_id, status, asking_price, id),
    KEY idx_card_market_listings_seller_status (seller_user_id, status, created_at, id),
    KEY idx_card_market_listings_expiry (status, expires_at),

    CONSTRAINT fk_card_market_listings_seller
        FOREIGN KEY (seller_user_id) REFERENCES users (id),
    CONSTRAINT fk_card_market_listings_card
        FOREIGN KEY (card_id) REFERENCES trading_cards (id),
    CONSTRAINT fk_card_market_listings_golden
        FOREIGN KEY (golden_instance_id) REFERENCES golden_card_instances (id),
    CONSTRAINT ck_card_market_listings_asset_type
        CHECK (asset_type IN ('HYPER_RARE', 'GOLDEN_RARE')),
    CONSTRAINT ck_card_market_listings_status
        CHECK (status IN ('OPEN', 'SOLD', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_card_market_listings_price CHECK (asking_price BETWEEN 100 AND 99999999),
    CONSTRAINT ck_card_market_listings_version CHECK (version >= 0),
    CONSTRAINT ck_card_market_listings_asset_reference CHECK (
        (asset_type = 'HYPER_RARE' AND golden_instance_id IS NULL)
        OR (asset_type = 'GOLDEN_RARE' AND golden_instance_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_market_negotiations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'NEGOTIATING',
    turn VARCHAR(10) NOT NULL,
    current_proposer_type VARCHAR(10) NOT NULL,
    current_price BIGINT NOT NULL,
    escrowed_paid_point BIGINT NOT NULL DEFAULT 0,
    seller_counter_count INT NOT NULL DEFAULT 0,
    next_sequence INT NOT NULL DEFAULT 1,
    expires_at DATETIME(6) NOT NULL,
    closed_reason VARCHAR(30) NULL,
    closed_at DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    active_buyer_user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'NEGOTIATING' THEN buyer_user_id ELSE NULL END
    ) STORED,

    PRIMARY KEY (id),
    UNIQUE KEY uk_card_market_negotiations_active_buyer (listing_id, active_buyer_user_id),
    KEY idx_card_market_negotiations_listing_status (listing_id, status, created_at),
    KEY idx_card_market_negotiations_buyer_status (buyer_user_id, status, updated_at),
    KEY idx_card_market_negotiations_expiry (status, expires_at),

    CONSTRAINT fk_card_market_negotiations_listing
        FOREIGN KEY (listing_id) REFERENCES card_market_listings (id),
    CONSTRAINT fk_card_market_negotiations_buyer
        FOREIGN KEY (buyer_user_id) REFERENCES users (id),
    CONSTRAINT ck_card_market_negotiations_status CHECK (
        status IN ('NEGOTIATING', 'ACCEPTED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'LISTING_CLOSED')
    ),
    CONSTRAINT ck_card_market_negotiations_turn CHECK (turn IN ('BUYER', 'SELLER')),
    CONSTRAINT ck_card_market_negotiations_proposer CHECK (current_proposer_type IN ('BUYER', 'SELLER')),
    CONSTRAINT ck_card_market_negotiations_price CHECK (current_price BETWEEN 100 AND 99999999),
    CONSTRAINT ck_card_market_negotiations_escrow CHECK (escrowed_paid_point >= 0),
    CONSTRAINT ck_card_market_negotiations_counter CHECK (seller_counter_count >= 0),
    CONSTRAINT ck_card_market_negotiations_sequence CHECK (next_sequence >= 1),
    CONSTRAINT ck_card_market_negotiations_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_market_proposals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    negotiation_id BIGINT NOT NULL,
    proposer_user_id BIGINT NOT NULL,
    proposer_type VARCHAR(10) NOT NULL,
    sequence_no INT NOT NULL,
    proposed_price BIGINT NOT NULL,
    message_code VARCHAR(30) NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_card_market_proposals_sequence (negotiation_id, sequence_no),

    CONSTRAINT fk_card_market_proposals_negotiation
        FOREIGN KEY (negotiation_id) REFERENCES card_market_negotiations (id),
    CONSTRAINT fk_card_market_proposals_user
        FOREIGN KEY (proposer_user_id) REFERENCES users (id),
    CONSTRAINT ck_card_market_proposals_type CHECK (proposer_type IN ('BUYER', 'SELLER')),
    CONSTRAINT ck_card_market_proposals_sequence CHECK (sequence_no >= 1),
    CONSTRAINT ck_card_market_proposals_price CHECK (proposed_price BETWEEN 100 AND 99999999),
    CONSTRAINT ck_card_market_proposals_message CHECK (
        message_code IS NULL OR message_code IN (
            'PRICE_ADJUST_REQUEST', 'READY_TO_BUY', 'MAXIMUM_OFFER', 'CONSIDERING'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE card_market_trades (
    id BIGINT NOT NULL AUTO_INCREMENT,
    listing_id BIGINT NOT NULL,
    negotiation_id BIGINT NULL,
    trade_type VARCHAR(20) NOT NULL,
    seller_user_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL,
    golden_instance_id BIGINT NULL,
    card_code_snapshot VARCHAR(100) NOT NULL,
    card_name_snapshot VARCHAR(100) NOT NULL,
    rarity_snapshot VARCHAR(20) NOT NULL,
    image_key_snapshot VARCHAR(500) NULL,
    asking_price_snapshot BIGINT NOT NULL,
    trade_price BIGINT NOT NULL,
    fee_rate_bps INT NOT NULL,
    fee_point BIGINT NOT NULL,
    seller_received_point BIGINT NOT NULL,
    completed_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_card_market_trades_listing (listing_id),
    UNIQUE KEY uk_card_market_trades_negotiation (negotiation_id),
    KEY idx_card_market_trades_buyer_completed (buyer_user_id, completed_at, id),
    KEY idx_card_market_trades_seller_completed (seller_user_id, completed_at, id),
    KEY idx_card_market_trades_card_completed (card_id, completed_at, id),

    CONSTRAINT fk_card_market_trades_listing
        FOREIGN KEY (listing_id) REFERENCES card_market_listings (id),
    CONSTRAINT fk_card_market_trades_negotiation
        FOREIGN KEY (negotiation_id) REFERENCES card_market_negotiations (id),
    CONSTRAINT fk_card_market_trades_seller
        FOREIGN KEY (seller_user_id) REFERENCES users (id),
    CONSTRAINT fk_card_market_trades_buyer
        FOREIGN KEY (buyer_user_id) REFERENCES users (id),
    CONSTRAINT fk_card_market_trades_card
        FOREIGN KEY (card_id) REFERENCES trading_cards (id),
    CONSTRAINT fk_card_market_trades_golden
        FOREIGN KEY (golden_instance_id) REFERENCES golden_card_instances (id),
    CONSTRAINT ck_card_market_trades_type CHECK (trade_type IN ('BUY_NOW', 'NEGOTIATED')),
    CONSTRAINT ck_card_market_trades_rarity CHECK (rarity_snapshot IN ('HYPER_RARE', 'GOLDEN_RARE')),
    CONSTRAINT ck_card_market_trades_asking_price CHECK (asking_price_snapshot BETWEEN 100 AND 99999999),
    CONSTRAINT ck_card_market_trades_price CHECK (trade_price BETWEEN 100 AND 99999999),
    CONSTRAINT ck_card_market_trades_fee_rate CHECK (fee_rate_bps BETWEEN 0 AND 10000),
    CONSTRAINT ck_card_market_trades_fee CHECK (fee_point >= 0),
    CONSTRAINT ck_card_market_trades_receivable CHECK (seller_received_point >= 0),
    CONSTRAINT ck_card_market_trades_amount CHECK (trade_price = fee_point + seller_received_point),
    CONSTRAINT ck_card_market_trades_users CHECK (seller_user_id <> buyer_user_id),
    CONSTRAINT ck_card_market_trades_negotiation CHECK (
        (trade_type = 'BUY_NOW' AND negotiation_id IS NULL)
        OR (trade_type = 'NEGOTIATED' AND negotiation_id IS NOT NULL)
    ),
    CONSTRAINT ck_card_market_trades_asset CHECK (
        (rarity_snapshot = 'HYPER_RARE' AND golden_instance_id IS NULL)
        OR (rarity_snapshot = 'GOLDEN_RARE' AND golden_instance_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
