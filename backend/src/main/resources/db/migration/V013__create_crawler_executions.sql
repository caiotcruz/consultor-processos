CREATE TABLE crawler_executions (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    process_id         UUID         NOT NULL REFERENCES processes(id),
    court_id           UUID         NOT NULL REFERENCES courts(id),
    strategy           VARCHAR(20)  NOT NULL,
    success            BOOLEAN      NOT NULL,
    duration_ms        BIGINT       NOT NULL,
    http_status_code   INTEGER      NULL,
    error_type         VARCHAR(50)  NULL,
    error_message      VARCHAR(500) NULL,
    parser_version_id  UUID         NULL REFERENCES parser_versions(id),
    executed_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT exec_strategy_check CHECK (strategy IN (
        'HTTP', 'JSOUP', 'PLAYWRIGHT', 'SELENIUM'
    )),
    CONSTRAINT exec_error_type_check CHECK (error_type IN (
        'TIMEOUT', 'PARSE_ERROR', 'BLOCKED', 'CAPTCHA',
        'HTTP_ERROR', 'CONNECTION_ERROR', 'UNKNOWN', NULL
    )),
    CONSTRAINT exec_duration_positive CHECK (duration_ms >= 0)
);

CREATE INDEX idx_exec_court_id    ON crawler_executions(court_id);
CREATE INDEX idx_exec_process_id  ON crawler_executions(process_id);
CREATE INDEX idx_exec_executed_at ON crawler_executions(court_id, executed_at DESC);
CREATE INDEX idx_exec_success     ON crawler_executions(court_id, success, executed_at DESC);