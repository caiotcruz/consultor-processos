CREATE TABLE password_resets (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT password_resets_hash_unique UNIQUE (token_hash)
);

CREATE UNIQUE INDEX idx_pw_reset_hash    ON password_resets(token_hash);
CREATE        INDEX idx_pw_reset_user_id ON password_resets(user_id);