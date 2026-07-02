CREATE TABLE users (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(150) NOT NULL,
    email               VARCHAR(255) NOT NULL,
    password_hash       VARCHAR(72)  NOT NULL,
    plan_id             UUID         NOT NULL REFERENCES plans(id),
    status              VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at   TIMESTAMPTZ  NULL,
    last_login_at       TIMESTAMPTZ  NULL,
    login_failure_count INTEGER      NOT NULL DEFAULT 0,
    locked_until        TIMESTAMPTZ  NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT users_status_check CHECK (status IN (
        'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED'
    )),
    CONSTRAINT users_failure_count_non_negative CHECK (login_failure_count >= 0)
);

CREATE UNIQUE INDEX idx_users_email   ON users(email);
CREATE        INDEX idx_users_plan_id ON users(plan_id);
CREATE        INDEX idx_users_status  ON users(status);