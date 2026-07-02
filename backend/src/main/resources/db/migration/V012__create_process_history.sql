CREATE TABLE process_history (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    process_id     UUID        NOT NULL REFERENCES processes(id),
    snapshot_id    UUID        NOT NULL REFERENCES process_snapshots(id),
    description    TEXT        NOT NULL,
    movement_date  DATE        NULL,
    detected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_history_process_id    ON process_history(process_id);
CREATE INDEX idx_history_detected_at   ON process_history(process_id, detected_at DESC);
CREATE INDEX idx_history_movement_date ON process_history(process_id, movement_date DESC);