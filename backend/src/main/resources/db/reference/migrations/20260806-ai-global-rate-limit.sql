-- AI 외부 호출의 전역 예산 창. id=1인 행 하나만 사용하며 사용자별 한도와 별개로 모든 호출을 센다.
-- Hibernate ddl-auto=update를 쓰는 현재 환경에서도 생성되지만, 운영 반영 전에는 이 DDL을 명시적으로 적용한다.
CREATE TABLE ai_global_rate_limit_windows (
    id BIGINT NOT NULL,
    window_started_at DATETIME(6) NOT NULL,
    consumed INT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_ai_global_rate_limit_windows_singleton CHECK (id = 1),
    CONSTRAINT ck_ai_global_rate_limit_windows_consumed CHECK (consumed >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
