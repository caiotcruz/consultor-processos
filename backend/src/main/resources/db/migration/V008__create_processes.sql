CREATE TABLE processes (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    process_number      VARCHAR(25) NOT NULL,
    process_number_raw  VARCHAR(50) NOT NULL,
    court_id            UUID        NOT NULL REFERENCES courts(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_checked_at     TIMESTAMPTZ NULL,
    last_movement_at    TIMESTAMPTZ NULL,
    last_snapshot_hash  VARCHAR(64) NULL,
    consecutive_errors  INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT processes_status_check CHECK (status IN (
        'PENDING', 'OK', 'ERROR', 'BLOCKED'
    )),
    CONSTRAINT processes_number_court_unique UNIQUE (process_number, court_id),
    CONSTRAINT processes_errors_non_negative CHECK (consecutive_errors >= 0)
);

CREATE UNIQUE INDEX idx_processes_number_court ON processes(process_number, court_id);
CREATE        INDEX idx_processes_status       ON processes(status);
CREATE        INDEX idx_processes_court_id     ON processes(court_id);
CREATE        INDEX idx_processes_last_checked ON processes(last_checked_at NULLS FIRST) WHERE status IN ('PENDING', 'OK');