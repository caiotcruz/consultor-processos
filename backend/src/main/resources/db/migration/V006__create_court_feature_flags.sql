CREATE TABLE court_feature_flags (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id    UUID         NOT NULL REFERENCES courts(id) ON DELETE CASCADE,
    flag_key    VARCHAR(100) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT false,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(255) NULL,

    CONSTRAINT court_flags_unique UNIQUE (court_id, flag_key)
);

CREATE UNIQUE INDEX idx_court_flags_court_key ON court_feature_flags(court_id, flag_key);