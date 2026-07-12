CREATE TABLE audit_log (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID         REFERENCES users(id) ON DELETE SET NULL,
    actor_email VARCHAR(255) NOT NULL,
    action      VARCHAR(100) NOT NULL,
    entity_type VARCHAR(50)  NOT NULL,
    entity_id   VARCHAR(100) NOT NULL,
    old_value   TEXT,
    new_value   TEXT,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_actor     ON audit_log(actor_id);
CREATE INDEX idx_audit_log_entity    ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_log_created   ON audit_log(created_at DESC);
CREATE INDEX idx_audit_log_action    ON audit_log(action);