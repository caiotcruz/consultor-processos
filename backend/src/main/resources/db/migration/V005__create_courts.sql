CREATE TABLE courts (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name               VARCHAR(200) NOT NULL,
    code               VARCHAR(20)  NOT NULL,
    provider_class     VARCHAR(100) NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT false,
    rate_limit_per_min INTEGER      NOT NULL DEFAULT 10,
    min_delay_ms       INTEGER      NOT NULL DEFAULT 1000,
    max_delay_ms       INTEGER      NOT NULL DEFAULT 3000,
    health_score       INTEGER      NOT NULL DEFAULT 100,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT courts_code_unique        UNIQUE (code),
    CONSTRAINT courts_health_score_range CHECK (health_score BETWEEN 0 AND 100),
    CONSTRAINT courts_delay_order        CHECK (min_delay_ms <= max_delay_ms),
    CONSTRAINT courts_rate_limit_pos     CHECK (rate_limit_per_min > 0)
);

CREATE UNIQUE INDEX idx_courts_code   ON courts(code);
CREATE        INDEX idx_courts_active ON courts(active);