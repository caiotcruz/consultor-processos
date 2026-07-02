DELETE FROM notification_history;
DELETE FROM process_history;
DELETE FROM process_snapshots;
DELETE FROM crawler_executions;
DELETE FROM court_health_scores;
DELETE FROM process_subscriptions;
DELETE FROM processes;
DELETE FROM court_requests;
DELETE FROM password_resets;
DELETE FROM refresh_tokens;
DELETE FROM users WHERE email LIKE '%@dev.consultorprocessos.com.br';

INSERT INTO users (id, name, email, password_hash, plan_id, status, email_verified_at)
SELECT
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Dev User',
    'dev@consultorprocessos.com.br',
    '$2a$12$placeholder_hash_substituir_na_fase_2',
    p.id,
    'ACTIVE',
    NOW()
FROM plans p
WHERE p.name = 'AVANCADO'
ON CONFLICT DO NOTHING;