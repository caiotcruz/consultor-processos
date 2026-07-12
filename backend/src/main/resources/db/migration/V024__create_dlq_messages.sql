CREATE TABLE dlq_messages (
    id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    process_id     UUID         REFERENCES processes(id) ON DELETE SET NULL,
    process_number VARCHAR(25)  NOT NULL,
    court_code     VARCHAR(20)  NOT NULL,
    retry_count    INTEGER      NOT NULL DEFAULT 0,
    error_message  TEXT,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    queued_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at   TIMESTAMPTZ,
    processed_by   VARCHAR(255),

    CONSTRAINT dlq_status_check CHECK (status IN ('PENDING', 'REQUEUED', 'DISCARDED'))
);

CREATE INDEX idx_dlq_status    ON dlq_messages(status);
CREATE INDEX idx_dlq_queued_at ON dlq_messages(queued_at DESC);