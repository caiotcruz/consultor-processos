CREATE TABLE court_requests (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NULL REFERENCES users(id) ON DELETE SET NULL,
    court_name      VARCHAR(200) NOT NULL,
    court_code      VARCHAR(20)  NULL,
    process_number  VARCHAR(30)  NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    admin_notes     TEXT         NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT court_requests_status_check CHECK (status IN (
        'PENDING', 'IN_PROGRESS', 'DONE', 'REJECTED'
    ))
);

CREATE INDEX idx_court_requests_status     ON court_requests(status);
CREATE INDEX idx_court_requests_court_code ON court_requests(court_code);
CREATE INDEX idx_court_requests_user_id    ON court_requests(user_id);