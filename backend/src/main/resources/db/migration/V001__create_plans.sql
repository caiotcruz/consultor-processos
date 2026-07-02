CREATE TABLE plans (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(50)  NOT NULL UNIQUE,
    display_name         VARCHAR(100) NOT NULL,
    max_processes        INTEGER      NULL,
    check_interval_hours INTEGER      NOT NULL,
    price                NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    active               BOOLEAN      NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT plans_check_interval_positive CHECK (check_interval_hours > 0),
    CONSTRAINT plans_price_non_negative      CHECK (price >= 0)
);

CREATE UNIQUE INDEX idx_plans_name ON plans(name);