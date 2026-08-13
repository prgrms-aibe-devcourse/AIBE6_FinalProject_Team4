-- 계정별 KST 일일 성장 일지 보상 판정 기록이다. journal_id는 일지 삭제 뒤에도 기록을 보존하기 위한 논리 참조다.
CREATE TABLE IF NOT EXISTS daily_journal_rewards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    reward_date DATE NOT NULL,
    journal_id BIGINT NULL,
    reward_amount BIGINT NOT NULL,
    gacha_draw_id BIGINT NULL,
    rewarded_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_daily_journal_rewards_user_date UNIQUE (user_id, reward_date),
    INDEX idx_daily_journal_rewards_reward_date (reward_date),
    CONSTRAINT fk_daily_journal_rewards_user
        FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 기존 식물별 보상 이력은 사용자·보상일 기준으로 하나로 통합한다. 과거 행은 최초 원본 일지를
-- 안전하게 특정할 수 없어 journal_id와 gacha_draw_id를 비워 둔다.
INSERT INTO daily_journal_rewards
    (user_id, reward_date, journal_id, reward_amount, rewarded_at)
SELECT
    user_id,
    DATE(journal_reward_granted_at),
    NULL,
    100,
    MAX(journal_reward_granted_at)
FROM plant_profile
WHERE journal_reward_granted_at IS NOT NULL
GROUP BY user_id, DATE(journal_reward_granted_at)
ON DUPLICATE KEY UPDATE id = id;

-- expand 단계: plant_profile.journal_reward_granted_at은 구버전 인스턴스 호환을 위해 유지한다.
-- 모든 구버전 인스턴스가 종료되고 이관 검증이 끝난 뒤 별도 contract migration에서 제거한다.
