-- =============================================================================
-- 트레이딩 카드 · 가챠 물리 스키마 (가챠·조각·코스메틱 테이블 8개)
--
-- [정본 분리]
--   이 파일 / 수동 migration SQL : UNIQUE · INDEX · CHECK · DEFAULT 등 물리 스키마 정본
--   ERDCloud                   : 테이블 · 컬럼 · PK · FK 관계 시각화 전용
--   ERDCloud MCP import 시 UNIQUE / INDEX / CHECK 메타데이터는 저장되지 않는다.
--   ERDCloud export 를 운영 DDL 생성 원본으로 사용하지 않는다.
--
-- [테이블 구성]
--   카드·가챠 테이블 8개 : trading_cards / user_card_collections / golden_card_instances
--                          gacha_draws / gacha_draw_items / user_card_shard_wallets
--                          card_shard_transactions / user_card_cosmetics
--   users 는 팀 공용 기존 테이블이며 가챠 테이블 수에 포함하지 않는다.
--   분리 ERD 에서만 id BIGINT PK 단일 컬럼 스텁으로 유지한다.
--
-- [팀 ERD 병합 안내]
--   users 스텁은 복사하지 않는다. 카드·가챠 테이블 8개만 병합하고
--   기존 users.id 에 아래 7개 FK 를 재연결한다.
--     user_card_collections.user_id
--     golden_card_instances.owner_user_id
--     golden_card_instances.origin_user_id
--     gacha_draws.user_id
--     user_card_shard_wallets.user_id
--     card_shard_transactions.user_id
--     user_card_cosmetics.user_id
--
-- [MVP 제외 범위]
--   배지 / 카드별 개별 테두리 / 해금 환불 / 다중 팩 / 팩별 확률 이력
--   미개봉 팩 보관 / 거래 주문 및 체결 이력
--
-- [골든 가챠 재획득 제한]
-- 1. rarity = 'GOLDEN_RARE'인 카드는 사용자별 종류별 가챠 획득 평생 1회로 제한한다.
-- 2. 실제 획득 여부의 정본은 user_card_collections.golden_gacha_acquired_at이다.
-- 3. golden_gacha_acquired_at IS NULL인 골든만 가챠 선택 후보로 사용한다.
-- 4. 거래 구매와 현재 owned_count는 골든 가챠 획득 여부에 영향을 주지 않는다.
-- 5. 골든을 판매하여 owned_count가 0이 되어도 golden_gacha_acquired_at을 NULL로 되돌리지 않는다.
-- 6. 골든 3종을 모두 가챠 획득했다면 GOLDEN_RARE 당첨을 HYPER_RARE로 강등한다.
--
--   가챠 1회 제한 대상 판정에 별도 플래그 컬럼을 두지 않는다.
--   rarity 만으로 판정하며 동일 의미의 컬럼을 다른 이름으로 추가하지 않는다.
--
-- [추첨 절차]
--   최초 등급은 항상 0..2099999 고정 범위에서 추첨하고 roll_value 에 저장한다.
--   전체 확률 구간 재추첨은 금지한다. 제외된 골든 가중치가 다른 등급에 재분배되어
--   사용자마다 최종 등급 확률이 달라진다.
--   사용자별 확률 구간 압축도 금지한다. 같은 roll_value 가 사용자 상태에 따라
--   다른 등급이나 카드를 의미하게 되어 로그 재현이 불가능해진다.
--   골든 제외는 GOLDEN_RARE 등급 내 카드 선택 후보에서만 적용한다.
--
-- [rolled_rarity / final_rarity]
--   일반 추첨            rolled = final
--   미획득 골든 존재     rolled = GOLDEN_RARE / final = GOLDEN_RARE
--   골든 3종 획득 완료   rolled = GOLDEN_RARE / final = HYPER_RARE
--   is_downgraded 는 (rolled_rarity <> final_rarity) 로 파생하며 컬럼을 두지 않는다.
--   등급 내 카드 선택 난수는 별도 서버 난수를 쓰며 MVP 에서는 저장하지 않는다.
--
-- [골든 UI 상태] DB 컬럼을 추가하지 않고 두 값의 조합으로 판정한다.
--   owned_count 0  / golden_gacha_acquired_at NULL      미보유 · 가챠 획득 가능
--   owned_count 1+ / golden_gacha_acquired_at NULL      거래 보유 · 가챠 획득 가능
--   owned_count 0  / golden_gacha_acquired_at NOT NULL  가챠 획득 완료 · 현재 미보유
--   owned_count 1+ / golden_gacha_acquired_at NOT NULL  가챠 획득 완료 · 현재 보유
--
-- [골든 획득 트랜잭션 순서]
--   1. 최초 등급 추첨 → 2. rolled_rarity 결정 → 3. 미획득 골든 후보 조회
--   4. 선택된 카드의 user_card_collections 행 잠금
--   5. golden_gacha_acquired_at IS NULL 조건으로 조건부 갱신
--      갱신 결과 0건이면 중복 지급하지 않고 후보를 다시 확인한다.
--   6. golden_card_instances 생성 → 7. trading_cards 행 잠금 후 순번 발급
--   8. owned_count 증가 → 9. gacha_draw_items 저장 → 10. status = COMPLETED
--   UNIQUE (card_id, golden_origin_rank) 가 순번 중복의 최종 방어선이다.
--
-- [주의] FOREIGN KEY 제약에는 COMMENT 를 붙이지 않는다 (ERDCloud FK 파싱 실패)
-- [주의] 모든 시각은 KST(Asia/Seoul) 기준 DATETIME(6) 으로 저장한다
-- [확률 스케일] 전체 2,100,000
--   등급          등급합계     카드수   카드당 weight   확률
--   COMMON        1,470,000      15         98,000      70%
--   RARE            420,000      14         30,000      20%
--   SUPER_RARE      189,000       8         23,625       9%
--   HYPER_RARE       20,979       3          6,993      0.999%
--   GOLDEN_RARE          21       3              7      0.001%
--   합계          2,100,000      43                     100%
--   같은 등급의 카드는 모두 동일한 draw_weight 를 가진다.
--   활성 카드 전체 draw_weight 합계는 2,100,000 이어야 한다 (애플리케이션 검증).
--
-- [CHECK 제약 21개]
--   trading_cards 3 / user_card_collections 1 / golden_card_instances 1
--   gacha_draws 4 / gacha_draw_items 5 / user_card_shard_wallets 3
--   card_shard_transactions 3 / user_card_cosmetics 1
-- =============================================================================

CREATE TABLE trading_cards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    series_code VARCHAR(50) NOT NULL DEFAULT 'SEASON_01',
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    rarity VARCHAR(20) NOT NULL
        COMMENT 'COMMON / RARE / SUPER_RARE / HYPER_RARE / GOLDEN_RARE',
    description TEXT NULL,
    image_key VARCHAR(500) NULL
        COMMENT 'S3 또는 CDN base URL을 제외한 객체 키',
    draw_weight INT NOT NULL
        COMMENT '현재 단일 팩 기준 1/2100000 가중치. 활성 카드 전체 draw_weight 합계는 2100000이어야 한다.',
    display_order INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE / HIDDEN',
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_trading_cards_code (code),
    KEY idx_trading_cards_series_status_order (
        series_code,
        status,
        display_order
    ),
    KEY idx_trading_cards_rarity_status (
        rarity,
        status
    ),

    CONSTRAINT ck_trading_cards_rarity CHECK (
        rarity IN (
            'COMMON',
            'RARE',
            'SUPER_RARE',
            'HYPER_RARE',
            'GOLDEN_RARE'
        )
    ),
    CONSTRAINT ck_trading_cards_status CHECK (
        status IN ('ACTIVE', 'HIDDEN')
    ),
    CONSTRAINT ck_trading_cards_draw_weight CHECK (
        draw_weight >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE user_card_collections (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    card_id BIGINT NOT NULL,
    owned_count INT NOT NULL DEFAULT 0,
    first_acquired_at DATETIME(6) NOT NULL,
    golden_gacha_acquired_at DATETIME(6) NULL
        COMMENT '종류별 골든 가챠 평생 1회 판정 정본',
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_card_collections_user_card (
        user_id,
        card_id
    ),
    KEY idx_user_card_collections_card (card_id),

    CONSTRAINT fk_user_card_collections_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),

    CONSTRAINT fk_user_card_collections_card
        FOREIGN KEY (card_id)
        REFERENCES trading_cards (id),

    CONSTRAINT ck_user_card_collections_owned_count CHECK (
        owned_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE golden_card_instances (
    id BIGINT NOT NULL AUTO_INCREMENT,
    card_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    origin_user_id BIGINT NOT NULL,
    origin_type VARCHAR(20) NOT NULL
        COMMENT 'GACHA / ADMIN',
    golden_origin_rank BIGINT NULL
        COMMENT '가챠 생성 골든만 부여하며 거래 후에도 유지',
    origin_acquired_at DATETIME(6) NOT NULL,
    current_owner_since DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_golden_card_instances_rank (
        card_id,
        golden_origin_rank
    ),
    KEY idx_golden_card_instances_owner_card (
        owner_user_id,
        card_id
    ),
    KEY idx_golden_card_instances_origin (
        origin_user_id,
        card_id
    ),

    CONSTRAINT fk_golden_card_instances_card
        FOREIGN KEY (card_id)
        REFERENCES trading_cards (id),

    CONSTRAINT fk_golden_card_instances_owner
        FOREIGN KEY (owner_user_id)
        REFERENCES users (id),

    CONSTRAINT fk_golden_card_instances_origin
        FOREIGN KEY (origin_user_id)
        REFERENCES users (id),

    CONSTRAINT ck_golden_card_instances_origin_type CHECK (
        origin_type IN ('GACHA', 'ADMIN')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE gacha_draws (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source_type VARCHAR(20) NOT NULL
        COMMENT 'LOG_REWARD / PURCHASE / EVENT / ADMIN',
    source_id BIGINT NOT NULL
        COMMENT 'LOG_REWARD=idempotency_keys.id, PURCHASE=idempotency_keys.id, ADMIN=idempotency_keys.id',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING / PROCESSING / COMPLETED / RETRYABLE_FAILED / MANUAL_REVIEW / REFUNDED',
    draw_count INT NOT NULL DEFAULT 5,
    rate_version INT NOT NULL,
    result_viewed_at DATETIME(6) NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_retry_at DATETIME(6) NULL,
    last_error_code VARCHAR(100) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_gacha_draws_source (
        source_type,
        source_id
    ),
    KEY idx_gacha_draws_user_created (
        user_id,
        created_at
    ),
    KEY idx_gacha_draws_retry (
        status,
        next_retry_at
    ),

    CONSTRAINT fk_gacha_draws_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),

    CONSTRAINT ck_gacha_draws_source_type CHECK (
        source_type IN (
            'LOG_REWARD',
            'PURCHASE',
            'EVENT',
            'ADMIN'
        )
    ),

    CONSTRAINT ck_gacha_draws_status CHECK (
        status IN (
            'PENDING',
            'PROCESSING',
            'COMPLETED',
            'RETRYABLE_FAILED',
            'MANUAL_REVIEW',
            'REFUNDED'
        )
    ),

    CONSTRAINT ck_gacha_draws_draw_count CHECK (
        draw_count > 0
    ),

    CONSTRAINT ck_gacha_draws_attempt_count CHECK (
        attempt_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE gacha_draw_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    gacha_draw_id BIGINT NOT NULL,
    draw_seq INT NOT NULL,
    card_id BIGINT NOT NULL,
    roll_value INT NOT NULL
        COMMENT '0..2099999',
    rolled_rarity VARCHAR(20) NOT NULL,
    final_rarity VARCHAR(20) NOT NULL,
    owned_count_after INT NOT NULL,
    golden_instance_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_gacha_draw_items_sequence (
        gacha_draw_id,
        draw_seq
    ),
    UNIQUE KEY uk_gacha_draw_items_golden_instance (
        golden_instance_id
    ),
    KEY idx_gacha_draw_items_card_created (
        card_id,
        created_at
    ),

    CONSTRAINT fk_gacha_draw_items_draw
        FOREIGN KEY (gacha_draw_id)
        REFERENCES gacha_draws (id),

    CONSTRAINT fk_gacha_draw_items_card
        FOREIGN KEY (card_id)
        REFERENCES trading_cards (id),

    CONSTRAINT fk_gacha_draw_items_golden_instance
        FOREIGN KEY (golden_instance_id)
        REFERENCES golden_card_instances (id),

    CONSTRAINT ck_gacha_draw_items_seq CHECK (
        draw_seq > 0
    ),

    CONSTRAINT ck_gacha_draw_items_roll CHECK (
        roll_value BETWEEN 0 AND 2099999
    ),

    CONSTRAINT ck_gacha_draw_items_owned_count CHECK (
        owned_count_after > 0
    ),

    CONSTRAINT ck_gacha_draw_items_rolled_rarity CHECK (
        rolled_rarity IN (
            'COMMON',
            'RARE',
            'SUPER_RARE',
            'HYPER_RARE',
            'GOLDEN_RARE'
        )
    ),

    CONSTRAINT ck_gacha_draw_items_final_rarity CHECK (
        final_rarity IN (
            'COMMON',
            'RARE',
            'SUPER_RARE',
            'HYPER_RARE',
            'GOLDEN_RARE'
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


CREATE TABLE user_card_shard_wallets (
    user_id BIGINT NOT NULL,
    balance BIGINT NOT NULL DEFAULT 0,
    lifetime_earned BIGINT NOT NULL DEFAULT 0,
    lifetime_spent BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    PRIMARY KEY (user_id),

    CONSTRAINT fk_user_card_shard_wallets_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),

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

    CONSTRAINT fk_card_shard_transactions_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),
    CONSTRAINT fk_card_shard_transactions_card
        FOREIGN KEY (card_id)
        REFERENCES trading_cards (id),

    CONSTRAINT ck_card_shard_transactions_type CHECK (
        transaction_type IN ('DISMANTLE_EARN', 'COSMETIC_SPEND')
    ),
    CONSTRAINT ck_card_shard_transactions_balance CHECK (balance_after >= 0),
    CONSTRAINT ck_card_shard_transactions_payload CHECK (
        (transaction_type = 'DISMANTLE_EARN'
            AND card_id IS NOT NULL
            AND cosmetic_code IS NULL
            AND card_quantity > 0
            AND shard_per_card_snapshot > 0
            AND amount > 0)
        OR
        (transaction_type = 'COSMETIC_SPEND'
            AND card_id IS NULL
            AND cosmetic_code IS NOT NULL
            AND card_quantity IS NULL
            AND shard_per_card_snapshot IS NULL
            AND amount < 0)
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

    CONSTRAINT fk_user_card_cosmetics_user
        FOREIGN KEY (user_id)
        REFERENCES users (id),

    CONSTRAINT ck_user_card_cosmetics_type CHECK (
        cosmetic_type IN ('TITLE', 'BORDER')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
