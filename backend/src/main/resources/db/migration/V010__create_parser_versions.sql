CREATE TABLE parser_versions (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id     UUID         NOT NULL REFERENCES courts(id) ON DELETE CASCADE,
    version      VARCHAR(20)  NOT NULL,
    description  TEXT         NULL,
    active       BOOLEAN      NOT NULL DEFAULT false,
    released_at  TIMESTAMPTZ  NULL,
    released_by  VARCHAR(255) NULL,

    CONSTRAINT parser_versions_court_version_unique UNIQUE (court_id, version)
);

CREATE UNIQUE INDEX idx_parser_versions_court_version ON parser_versions(court_id, version);
CREATE        INDEX idx_parser_versions_active        ON parser_versions(court_id, active) WHERE active = true;