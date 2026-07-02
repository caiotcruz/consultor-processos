CREATE TABLE process_snapshots (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    process_id        UUID        NOT NULL REFERENCES processes(id),
    content_hash      VARCHAR(64) NOT NULL,
    raw_content       TEXT        NOT NULL,
    parser_version_id UUID        NOT NULL REFERENCES parser_versions(id),
    crawler_strategy  VARCHAR(20) NOT NULL,
    captured_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT snapshots_strategy_check CHECK (crawler_strategy IN (
        'HTTP', 'JSOUP', 'PLAYWRIGHT', 'SELENIUM'
    ))
);

CREATE INDEX idx_snapshots_process_id   ON process_snapshots(process_id);
CREATE INDEX idx_snapshots_captured_at  ON process_snapshots(process_id, captured_at DESC);
CREATE INDEX idx_snapshots_content_hash ON process_snapshots(content_hash);