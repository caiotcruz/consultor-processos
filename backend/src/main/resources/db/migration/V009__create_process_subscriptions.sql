CREATE TABLE process_subscriptions (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    process_id     UUID         NOT NULL REFERENCES processes(id),
    active         BOOLEAN      NOT NULL DEFAULT true,
    alias          VARCHAR(200) NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deactivated_at TIMESTAMPTZ  NULL,

    CONSTRAINT process_subs_user_process_unique UNIQUE (user_id, process_id)
);

CREATE UNIQUE INDEX idx_subs_user_process ON process_subscriptions(user_id, process_id);
CREATE        INDEX idx_subs_user_id      ON process_subscriptions(user_id)
    WHERE active = true;
CREATE        INDEX idx_subs_process_id   ON process_subscriptions(process_id)
    WHERE active = true;