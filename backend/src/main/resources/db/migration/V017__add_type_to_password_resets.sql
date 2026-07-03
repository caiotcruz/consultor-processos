ALTER TABLE password_resets
    ADD COLUMN token_type VARCHAR(30) NOT NULL DEFAULT 'PASSWORD_RESET';

ALTER TABLE password_resets
    ADD CONSTRAINT password_resets_token_type_check
    CHECK (token_type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION'));

CREATE INDEX idx_pw_reset_type ON password_resets(token_type, user_id);

UPDATE password_resets
SET token_type = 'EMAIL_VERIFICATION'
WHERE used_at IS NULL
  AND expires_at > NOW()
  AND (expires_at - created_at) > INTERVAL '2 hours';