CREATE TABLE court_health_scores (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id        UUID         NOT NULL REFERENCES courts(id) ON DELETE CASCADE,
    score           INTEGER      NOT NULL,
    success_rate    NUMERIC(5,4) NOT NULL,
    avg_duration_ms BIGINT       NOT NULL,
    retry_rate      NUMERIC(5,4) NOT NULL,
    calculated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT health_score_range  CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT health_success_rate CHECK (success_rate BETWEEN 0 AND 1),
    CONSTRAINT health_retry_rate   CHECK (retry_rate BETWEEN 0 AND 1)
);

CREATE INDEX idx_health_court_id       ON court_health_scores(court_id);
CREATE INDEX idx_health_calculated_at  ON court_health_scores(court_id, calculated_at DESC);