ALTER TABLE users
    ADD COLUMN notif_email_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN notif_push_enabled  BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN users.notif_email_enabled IS
    'Se o usuário deseja receber notificações por e-mail';
COMMENT ON COLUMN users.notif_push_enabled IS
    'Se o usuário deseja receber push notifications (requer dispositivo registrado)';