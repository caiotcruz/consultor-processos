# 06 — Banco de Dados

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Convenções

| Item | Convenção |
|------|-----------|
| Nomes de tabelas | `snake_case`, plural: `users`, `processes`, `court_requests` |
| Nomes de colunas | `snake_case`: `created_at`, `process_number` |
| Chaves primárias | `UUID` gerado pelo banco (`gen_random_uuid()`) |
| Chaves estrangeiras | `{entidade_referenciada}_id`, ex: `court_id`, `user_id` |
| Timestamps | `TIMESTAMPTZ` (com timezone), nunca `TIMESTAMP` |
| Datas sem hora | `DATE` |
| Booleanos | `BOOLEAN NOT NULL DEFAULT false` |
| Textos curtos | `VARCHAR(N)` com limite explícito |
| Textos longos | `TEXT` |
| Valores monetários | `NUMERIC(10, 2)` |
| Soft delete | Nunca deletar; usar `status` ou campo `deleted_at` |
| Migrações | Flyway, arquivo: `V{numero}__{descricao}.sql` |

---

## 2. Diagrama de Tabelas (ERD simplificado)

```
plans ◀──────── users ───────────────────────────────────────────┐
  │               │                                              │
  │         ┌─────┴──────────────────────────────────┐          │
  │    refresh_tokens   password_resets   court_requests         │
  │                                                              │
courts ◀─── processes ◀──── process_subscriptions ──────────────┘
  │              │
  │    ┌─────────┼──────────────────────────┐
  │  process_   process_   crawler_    parser_
  │  snapshots  history    executions  versions
  │
  ├── court_feature_flags
  └── court_health_scores
  
users ◀── notification_history ──▶ processes
```

---

## 3. Esquema Completo das Tabelas

---

### 3.1 `plans`

```sql
CREATE TABLE plans (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(50)    NOT NULL UNIQUE,
    display_name         VARCHAR(100)   NOT NULL,
    max_processes        INTEGER        NULL,          -- NULL = ilimitado
    check_interval_hours INTEGER        NOT NULL,
    price                NUMERIC(10,2)  NOT NULL DEFAULT 0.00,
    active               BOOLEAN        NOT NULL DEFAULT true,
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT plans_check_interval_positive CHECK (check_interval_hours > 0),
    CONSTRAINT plans_price_non_negative      CHECK (price >= 0)
);

-- Dados iniciais (via Flyway seed)
INSERT INTO plans (name, display_name, max_processes, check_interval_hours, price) VALUES
    ('GRATUITO', 'Plano Gratuito',   5,    12, 0.00),
    ('BASICO',   'Plano Básico',     10,   8,  0.00),  -- preço a definir
    ('AVANCADO', 'Plano Avançado',   NULL, 4,  0.00);  -- preço a definir
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_plans_name ON plans(name);
```

---

### 3.2 `users`

```sql
CREATE TABLE users (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(150) NOT NULL,
    email                VARCHAR(255) NOT NULL,
    password_hash        VARCHAR(72)  NOT NULL,      -- bcrypt max 72 chars
    plan_id              UUID         NOT NULL REFERENCES plans(id),
    status               VARCHAR(30)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at    TIMESTAMPTZ  NULL,
    last_login_at        TIMESTAMPTZ  NULL,
    login_failure_count  INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ  NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT users_email_unique   UNIQUE (email),
    CONSTRAINT users_status_check   CHECK (status IN (
        'PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DELETED'
    )),
    CONSTRAINT users_failure_count  CHECK (login_failure_count >= 0)
);
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_users_email      ON users(email);
CREATE        INDEX idx_users_plan_id    ON users(plan_id);
CREATE        INDEX idx_users_status     ON users(status);
```

---

### 3.3 `refresh_tokens`

```sql
CREATE TABLE refresh_tokens (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash   VARCHAR(64)  NOT NULL,     -- SHA-256 hex (64 chars)
    expires_at   TIMESTAMPTZ  NOT NULL,
    revoked_at   TIMESTAMPTZ  NULL,
    user_agent   VARCHAR(512) NULL,
    ip_address   VARCHAR(45)  NULL,         -- suporta IPv6
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT refresh_tokens_hash_unique UNIQUE (token_hash)
);
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_refresh_tokens_hash    ON refresh_tokens(token_hash);
CREATE        INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
-- Limpeza periódica de tokens expirados (job)
CREATE        INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at)
    WHERE revoked_at IS NULL;
```

---

### 3.4 `password_resets`

```sql
CREATE TABLE password_resets (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT password_resets_hash_unique UNIQUE (token_hash)
);
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_pw_reset_hash    ON password_resets(token_hash);
CREATE        INDEX idx_pw_reset_user_id ON password_resets(user_id);
```

---

### 3.5 `courts`

```sql
CREATE TABLE courts (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    code                VARCHAR(20)  NOT NULL,
    provider_class      VARCHAR(100) NOT NULL,
    active              BOOLEAN      NOT NULL DEFAULT false,
    rate_limit_per_min  INTEGER      NOT NULL DEFAULT 10,
    min_delay_ms        INTEGER      NOT NULL DEFAULT 1000,
    max_delay_ms        INTEGER      NOT NULL DEFAULT 3000,
    health_score        INTEGER      NOT NULL DEFAULT 100,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT courts_code_unique       UNIQUE (code),
    CONSTRAINT courts_health_score_range CHECK (health_score BETWEEN 0 AND 100),
    CONSTRAINT courts_delay_order        CHECK (min_delay_ms <= max_delay_ms),
    CONSTRAINT courts_rate_limit_pos     CHECK (rate_limit_per_min > 0)
);

-- Tribunais iniciais
INSERT INTO courts (name, code, provider_class, active, rate_limit_per_min, min_delay_ms, max_delay_ms) VALUES
    ('Supremo Tribunal Federal',              'STF',   'STFProvider',   true, 5,  2000, 5000),
    ('eProc - Processo Eletrônico',           'EPROC', 'EprocProvider', true, 5,  2000, 5000),
    ('Superior Tribunal de Justiça - RJ',     'STJRJ', 'STJRJProvider', true, 5,  2000, 5000);
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_courts_code   ON courts(code);
CREATE        INDEX idx_courts_active ON courts(active);
```

---

### 3.6 `court_feature_flags`

```sql
CREATE TABLE court_feature_flags (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id    UUID         NOT NULL REFERENCES courts(id) ON DELETE CASCADE,
    flag_key    VARCHAR(100) NOT NULL,
    enabled     BOOLEAN      NOT NULL DEFAULT false,
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_by  VARCHAR(255) NULL,

    CONSTRAINT court_flags_unique UNIQUE (court_id, flag_key)
);

-- Flags iniciais para cada tribunal
INSERT INTO court_feature_flags (court_id, flag_key, enabled)
SELECT id, 'PLAYWRIGHT_ENABLED', false FROM courts WHERE code IN ('STF', 'EPROC', 'STJRJ');

INSERT INTO court_feature_flags (court_id, flag_key, enabled)
SELECT id, 'SELENIUM_ENABLED', false FROM courts WHERE code IN ('STF', 'EPROC', 'STJRJ');

INSERT INTO court_feature_flags (court_id, flag_key, enabled)
SELECT id, 'EXTRA_RETRY_ENABLED', true FROM courts WHERE code IN ('STF', 'EPROC', 'STJRJ');
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_court_flags_court_key ON court_feature_flags(court_id, flag_key);
```

---

### 3.7 `court_requests`

```sql
CREATE TABLE court_requests (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id) ON DELETE SET NULL,
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
```

**Índices:**
```sql
CREATE INDEX idx_court_requests_status     ON court_requests(status);
CREATE INDEX idx_court_requests_court_code ON court_requests(court_code);
CREATE INDEX idx_court_requests_user_id    ON court_requests(user_id);
```

---

### 3.8 `processes`

```sql
CREATE TABLE processes (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    process_number      VARCHAR(25) NOT NULL,      -- formato CNJ normalizado
    process_number_raw  VARCHAR(50) NOT NULL,      -- como o usuário digitou
    court_id            UUID        NOT NULL REFERENCES courts(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    last_checked_at     TIMESTAMPTZ NULL,
    last_movement_at    TIMESTAMPTZ NULL,
    last_snapshot_hash  VARCHAR(64) NULL,           -- SHA-256 hex
    consecutive_errors  INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT processes_status_check CHECK (status IN (
        'PENDING', 'OK', 'ERROR', 'BLOCKED'
    )),
    CONSTRAINT processes_number_court_unique UNIQUE (process_number, court_id),
    CONSTRAINT processes_errors_non_negative CHECK (consecutive_errors >= 0)
);
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_processes_number_court ON processes(process_number, court_id);
CREATE        INDEX idx_processes_status       ON processes(status);
CREATE        INDEX idx_processes_court_id     ON processes(court_id);
-- Índice para o scheduler: processos ativos há mais tempo sem consulta
CREATE        INDEX idx_processes_last_checked ON processes(last_checked_at NULLS FIRST)
    WHERE status IN ('PENDING', 'OK');
```

---

### 3.9 `process_subscriptions`

```sql
CREATE TABLE process_subscriptions (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    process_id       UUID         NOT NULL REFERENCES processes(id),
    active           BOOLEAN      NOT NULL DEFAULT true,
    alias            VARCHAR(200) NULL,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deactivated_at   TIMESTAMPTZ  NULL,

    CONSTRAINT process_subs_user_process_unique UNIQUE (user_id, process_id)
);
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_subs_user_process ON process_subscriptions(user_id, process_id);
CREATE        INDEX idx_subs_user_id      ON process_subscriptions(user_id) WHERE active = true;
CREATE        INDEX idx_subs_process_id   ON process_subscriptions(process_id) WHERE active = true;
```

---

### 3.10 `parser_versions`

```sql
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

-- Versões iniciais
INSERT INTO parser_versions (court_id, version, description, active, released_at)
SELECT id, '1.0.0', 'Parser inicial', true, NOW() FROM courts WHERE code = 'STF';

INSERT INTO parser_versions (court_id, version, description, active, released_at)
SELECT id, '1.0.0', 'Parser inicial', true, NOW() FROM courts WHERE code = 'EPROC';

INSERT INTO parser_versions (court_id, version, description, active, released_at)
SELECT id, '1.0.0', 'Parser inicial', true, NOW() FROM courts WHERE code = 'STJRJ';
```

**Índices:**
```sql
CREATE UNIQUE INDEX idx_parser_versions_court_version ON parser_versions(court_id, version);
CREATE        INDEX idx_parser_versions_active        ON parser_versions(court_id, active)
    WHERE active = true;
```

---

### 3.11 `process_snapshots`

```sql
CREATE TABLE process_snapshots (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    process_id        UUID        NOT NULL REFERENCES processes(id),
    content_hash      VARCHAR(64) NOT NULL,
    raw_content       TEXT        NOT NULL,   -- JSON estruturado
    parser_version_id UUID        NOT NULL REFERENCES parser_versions(id),
    crawler_strategy  VARCHAR(20) NOT NULL,
    captured_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT snapshots_strategy_check CHECK (crawler_strategy IN (
        'HTTP', 'JSOUP', 'PLAYWRIGHT', 'SELENIUM'
    ))
);
```

**Índices:**
```sql
CREATE INDEX idx_snapshots_process_id    ON process_snapshots(process_id);
CREATE INDEX idx_snapshots_captured_at   ON process_snapshots(process_id, captured_at DESC);
CREATE INDEX idx_snapshots_content_hash  ON process_snapshots(content_hash);
```

---

### 3.12 `process_history`

```sql
CREATE TABLE process_history (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    process_id     UUID        NOT NULL REFERENCES processes(id),
    snapshot_id    UUID        NOT NULL REFERENCES process_snapshots(id),
    description    TEXT        NOT NULL,
    movement_date  DATE        NULL,        -- data da movimentação no tribunal
    detected_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

**Índices:**
```sql
CREATE INDEX idx_history_process_id   ON process_history(process_id);
CREATE INDEX idx_history_detected_at  ON process_history(process_id, detected_at DESC);
CREATE INDEX idx_history_movement_date ON process_history(process_id, movement_date DESC);
```

---

### 3.13 `crawler_executions`

```sql
CREATE TABLE crawler_executions (
    id                UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    process_id        UUID         NOT NULL REFERENCES processes(id),
    court_id          UUID         NOT NULL REFERENCES courts(id),
    strategy          VARCHAR(20)  NOT NULL,
    success           BOOLEAN      NOT NULL,
    duration_ms       BIGINT       NOT NULL,
    http_status_code  INTEGER      NULL,
    error_type        VARCHAR(50)  NULL,
    error_message     VARCHAR(500) NULL,
    parser_version_id UUID         NULL REFERENCES parser_versions(id),
    executed_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT exec_strategy_check CHECK (strategy IN (
        'HTTP', 'JSOUP', 'PLAYWRIGHT', 'SELENIUM'
    )),
    CONSTRAINT exec_error_type_check CHECK (error_type IN (
        'TIMEOUT', 'PARSE_ERROR', 'BLOCKED', 'CAPTCHA',
        'HTTP_ERROR', 'CONNECTION_ERROR', 'UNKNOWN', NULL
    )),
    CONSTRAINT exec_duration_positive CHECK (duration_ms >= 0)
);
```

**Índices:**
```sql
CREATE INDEX idx_exec_court_id    ON crawler_executions(court_id);
CREATE INDEX idx_exec_process_id  ON crawler_executions(process_id);
CREATE INDEX idx_exec_executed_at ON crawler_executions(court_id, executed_at DESC);
CREATE INDEX idx_exec_success     ON crawler_executions(court_id, success, executed_at DESC);
```

---

### 3.14 `court_health_scores`

```sql
CREATE TABLE court_health_scores (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    court_id        UUID        NOT NULL REFERENCES courts(id) ON DELETE CASCADE,
    score           INTEGER     NOT NULL,
    success_rate    NUMERIC(5,4) NOT NULL,   -- 0.0000 a 1.0000
    avg_duration_ms BIGINT      NOT NULL,
    retry_rate      NUMERIC(5,4) NOT NULL,
    calculated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT health_score_range  CHECK (score BETWEEN 0 AND 100),
    CONSTRAINT health_success_rate CHECK (success_rate BETWEEN 0 AND 1),
    CONSTRAINT health_retry_rate   CHECK (retry_rate BETWEEN 0 AND 1)
);
```

**Índices:**
```sql
CREATE INDEX idx_health_court_id      ON court_health_scores(court_id);
CREATE INDEX idx_health_calculated_at ON court_health_scores(court_id, calculated_at DESC);
```

---

### 3.15 `notification_history`

```sql
CREATE TABLE notification_history (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    process_id    UUID         NULL REFERENCES processes(id) ON DELETE SET NULL,
    channel       VARCHAR(20)  NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    error_message VARCHAR(500) NULL,
    sent_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT notif_channel_check CHECK (channel IN (
        'EMAIL', 'PUSH', 'SMS', 'WEBHOOK'
    )),
    CONSTRAINT notif_status_check CHECK (status IN (
        'SENT', 'FAILED', 'SKIPPED'
    ))
);
```

**Índices:**
```sql
CREATE INDEX idx_notif_user_id  ON notification_history(user_id);
CREATE INDEX idx_notif_sent_at  ON notification_history(user_id, sent_at DESC);
CREATE INDEX idx_notif_status   ON notification_history(status) WHERE status = 'FAILED';
```

---

## 4. Política de Retenção de Dados

| Tabela | Retenção | Ação |
|--------|---------|------|
| `refresh_tokens` (expirados/revogados) | 90 dias | Deletar via job periódico |
| `password_resets` (usados/expirados) | 30 dias | Deletar via job periódico |
| `crawler_executions` | 6 meses | Arquivar em tabela histórica |
| `court_health_scores` | 3 meses (granular) | Agregar por semana após 30 dias |
| `process_snapshots` | 1 ano | Configurável; arquivar os mais antigos |
| `process_history` | Indefinido | Nunca deletar (auditoria) |
| `notification_history` | 1 ano | Deletar registros antigos bem-sucedidos |

---

## 5. Migrações Flyway

### Estrutura de arquivos

```
src/main/resources/db/migration/
│
├── V001__create_plans.sql
├── V002__create_users.sql
├── V003__create_refresh_tokens.sql
├── V004__create_password_resets.sql
├── V005__create_courts.sql
├── V006__create_court_feature_flags.sql
├── V007__create_court_requests.sql
├── V008__create_processes.sql
├── V009__create_process_subscriptions.sql
├── V010__create_parser_versions.sql
├── V011__create_process_snapshots.sql
├── V012__create_process_history.sql
├── V013__create_crawler_executions.sql
├── V014__create_court_health_scores.sql
├── V015__create_notification_history.sql
│
└── V100__seed_initial_data.sql  -- planos e tribunais iniciais
```

### Regras para migrações

1. **Nunca alterar** um arquivo de migração já executado em qualquer ambiente
2. Toda alteração de schema → novo arquivo `V{próximo_número}__descricao.sql`
3. Nomes em inglês, descritivos: `V016__add_alias_to_subscriptions.sql`
4. Migrações destrutivas (DROP, DELETE em massa) → revisão obrigatória em pull request
5. Seeds de dados de desenvolvimento ficam em `src/main/resources/db/dev/` e nunca rodam em `prod`

---

## 6. Configuração do Banco de Dados

### Usuários e permissões

```sql
-- Usuário da aplicação (permissões mínimas)
CREATE USER app_consultorprocessos WITH PASSWORD '...';
GRANT CONNECT ON DATABASE consultorprocessos TO app_consultorprocessos;
GRANT USAGE ON SCHEMA public TO app_consultorprocessos;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO app_consultorprocessos;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO app_consultorprocessos;

-- Usuário de migration (Flyway)
CREATE USER flyway_consultorprocessos WITH PASSWORD '...';
GRANT CONNECT ON DATABASE consultorprocessos TO flyway_consultorprocessos;
GRANT CREATE ON SCHEMA public TO flyway_consultorprocessos;
```

### `application.yml` (banco)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

  jpa:
    hibernate:
      ddl-auto: validate      # nunca create ou update em produção
    open-in-view: false       # sempre false; evita lazy loading acidental
    show-sql: false

  flyway:
    enabled: true
    baseline-on-migrate: false
    locations: classpath:db/migration
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    user: ${FLYWAY_USER}
    password: ${FLYWAY_PASSWORD}
```

---

## 7. Queries Críticas (com Explicação)

### 7.1 Processos pendentes de consulta (Scheduler)

```sql
-- Busca todos os processos que precisam ser consultados agora.
-- Junta com o intervalo efetivo: menor checkIntervalHours entre assinantes ativos.
SELECT DISTINCT ON (p.id)
    p.id,
    p.process_number,
    p.court_id,
    c.code AS court_code,
    MIN(pl.check_interval_hours) AS effective_interval_hours
FROM processes p
INNER JOIN courts c ON c.id = p.court_id AND c.active = true
INNER JOIN process_subscriptions ps ON ps.process_id = p.id AND ps.active = true
INNER JOIN users u ON u.id = ps.user_id AND u.status = 'ACTIVE'
INNER JOIN plans pl ON pl.id = u.plan_id
WHERE
    p.status IN ('PENDING', 'OK')
    AND (
        p.last_checked_at IS NULL
        OR p.last_checked_at < NOW() - (MIN(pl.check_interval_hours) || ' hours')::INTERVAL
    )
GROUP BY p.id, p.process_number, p.court_id, c.code
ORDER BY p.id, p.last_checked_at ASC NULLS FIRST;
```

### 7.2 Contagem de processos ativos por usuário (validação de plano)

```sql
SELECT COUNT(*)
FROM process_subscriptions ps
WHERE ps.user_id = :userId
  AND ps.active = true;
```

### 7.3 Verificar deduplicação antes de criar processo

```sql
-- Retorna o Process existente se já houver cadastro com o mesmo número + tribunal
SELECT id
FROM processes
WHERE process_number = :normalizedNumber
  AND court_id = :courtId;
```

### 7.4 Últimas movimentações de um processo (paginado)

```sql
SELECT ph.id, ph.description, ph.movement_date, ph.detected_at
FROM process_history ph
WHERE ph.process_id = :processId
ORDER BY ph.detected_at DESC
LIMIT :pageSize OFFSET :offset;
```

### 7.5 Health score: execuções das últimas 100 por tribunal

```sql
SELECT
    success,
    strategy,
    AVG(duration_ms) AS avg_duration_ms,
    COUNT(*) AS total
FROM crawler_executions
WHERE court_id = :courtId
ORDER BY executed_at DESC
LIMIT 100;
```
