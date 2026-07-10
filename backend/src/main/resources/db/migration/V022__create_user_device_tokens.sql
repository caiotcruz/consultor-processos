CREATE TABLE user_device_tokens (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token        TEXT        NOT NULL,
    platform     VARCHAR(20) NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT udt_token_unique    UNIQUE (token),
    CONSTRAINT udt_platform_check  CHECK (platform IN ('ANDROID', 'IOS', 'WEB'))
);

CREATE INDEX idx_udt_user_id ON user_device_tokens(user_id);

COMMENT ON TABLE user_device_tokens IS
    'Tokens FCM de dispositivos dos usuários para push notifications.';