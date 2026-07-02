DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'app_consultorprocessos') THEN
        CREATE USER app_consultorprocessos WITH PASSWORD 'dev_app_password';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE consultorprocessos_dev TO app_consultorprocessos;
GRANT USAGE ON SCHEMA public TO app_consultorprocessos;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO app_consultorprocessos;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO app_consultorprocessos;