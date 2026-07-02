INSERT INTO plans (name, display_name, max_processes, check_interval_hours, price)
VALUES
    ('GRATUITO', 'Plano Gratuito',  5,    12, 0.00),
    ('BASICO',   'Plano Básico',    10,   8,  0.00),
    ('AVANCADO', 'Plano Avançado',  NULL, 4,  0.00);

INSERT INTO courts (name, code, provider_class, active, rate_limit_per_min, min_delay_ms, max_delay_ms)
VALUES
    ('Supremo Tribunal Federal',          'STF',   'STFProvider',   false, 5, 2000, 5000),
    ('eProc - Processo Eletrônico',       'EPROC', 'EprocProvider', false, 5, 2000, 5000),
    ('Superior Tribunal de Justiça - RJ', 'STJRJ', 'STJRJProvider', false, 5, 2000, 5000);

INSERT INTO court_feature_flags (court_id, flag_key, enabled)
SELECT id, 'PLAYWRIGHT_ENABLED',  false FROM courts WHERE code IN ('STF', 'EPROC', 'STJRJ');

INSERT INTO court_feature_flags (court_id, flag_key, enabled)
SELECT id, 'SELENIUM_ENABLED',    false FROM courts WHERE code IN ('STF', 'EPROC', 'STJRJ');

INSERT INTO court_feature_flags (court_id, flag_key, enabled)
SELECT id, 'EXTRA_RETRY_ENABLED', true  FROM courts WHERE code IN ('STF', 'EPROC', 'STJRJ');

INSERT INTO parser_versions (court_id, version, description, active, released_at)
SELECT id, '1.0.0', 'Parser inicial — implementação pendente (Fase 6)', false, NULL
FROM courts WHERE code = 'STF';

INSERT INTO parser_versions (court_id, version, description, active, released_at)
SELECT id, '1.0.0', 'Parser inicial — implementação pendente (Fase 6)', false, NULL
FROM courts WHERE code = 'EPROC';

INSERT INTO parser_versions (court_id, version, description, active, released_at)
SELECT id, '1.0.0', 'Parser inicial — implementação pendente (Fase 6)', false, NULL
FROM courts WHERE code = 'STJRJ';