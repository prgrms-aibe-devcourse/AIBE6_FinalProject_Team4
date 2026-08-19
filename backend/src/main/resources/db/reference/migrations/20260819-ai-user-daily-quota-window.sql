-- 사용자 한 명이 모든 AI 기능에서 하루 동안 소비한 외부 호출 수를 합산한다.
-- 별도 AI 제한 트랜잭션의 부모 행 락 경합을 피하기 위해 users FK는 두지 않는다.
CREATE TABLE ai_user_daily_quota_windows (
    user_id BIGINT NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    consumed INT NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT ck_ai_user_daily_quota_windows_consumed CHECK (consumed >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
