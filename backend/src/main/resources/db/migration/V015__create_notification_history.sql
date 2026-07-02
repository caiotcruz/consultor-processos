CREATE TABLE notification_history (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    process_id    UUID         NULL REFERENCES processes(id) ON DELETE SET NULL,
    channel       VARCHAR(20)  NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500) NULL,
    sent_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT notif_channel_check CHECK (channel IN (
        'EMAIL', 'PUSH', 'SMS', 'WEBHOOK'
    )),
    CONSTRAINT notif_status_check CHECK (status IN (
        'SENT', 'FAILED', 'SKIPPED'
    ))
);

CREATE INDEX idx_notif_user_id ON notification_history(user_id);
CREATE INDEX idx_notif_sent_at ON notification_history(user_id, sent_at DESC);
CREATE INDEX idx_notif_status  ON notification_history(status) WHERE status = 'FAILED';