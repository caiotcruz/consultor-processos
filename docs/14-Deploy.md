# 14 — Deploy e Infraestrutura

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Visão Geral da Infraestrutura

O sistema é completamente containerizado. Qualquer ambiente — local, staging ou produção — sobe com um único comando. Não existe dependência de configuração manual do sistema operacional.

```
┌─────────────────────────────────────────────────────────────┐
│                      PRODUÇÃO (VPS)                         │
│                                                             │
│  ┌──────────┐    ┌──────────────────────────────────────┐  │
│  │  Nginx   │    │         Docker Network               │  │
│  │  :443    │    │                                      │  │
│  │  :80     ├───▶│  ┌─────────┐  ┌─────┐  ┌────────┐  │  │
│  │  (proxy) │    │  │ backend │  │ db  │  │ redis  │  │  │
│  └──────────┘    │  │  :8080  │  │5432 │  │  6379  │  │  │
│                  │  └─────────┘  └─────┘  └────────┘  │  │
│  ┌──────────┐    │  ┌──────────┐  ┌──────────────────┐ │  │
│  │ Certbot  │    │  │ rabbitmq │  │  mock-tribunal   │ │  │
│  │  (SSL)   │    │  │  :5672   │  │  :9000 (DEV only)│ │  │
│  └──────────┘    │  └──────────┘  └──────────────────┘ │  │
│                  └──────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Estrutura de Arquivos Docker

```
projeto/
├── docker-compose.yml          ← produção
├── docker-compose.dev.yml      ← desenvolvimento local
├── docker-compose.test.yml     ← CI / testes de integração
│
├── backend/
│   ├── Dockerfile
│   └── .dockerignore
│
├── mock-tribunal/
│   └── Dockerfile
│
└── infra/
    ├── nginx/
    │   ├── nginx.conf
    │   └── sites/
    │       ├── api.conf
    │       └── mock.conf       ← DEV apenas
    ├── postgres/
    │   └── init.sql            ← cria usuários e banco
    └── scripts/
        ├── backup.sh
        └── restore.sh
```

---

## 3. Dockerfile do Backend

```dockerfile
# ── Estágio 1: Build ──────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copia apenas o pom.xml primeiro para aproveitar cache de layers
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN ./mvnw dependency:go-offline -q

# Copia o código e compila
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Estágio 2: Runtime ────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Usuário não-root para segurança
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copia apenas o JAR final
COPY --from=builder /app/target/*.jar app.jar

# Configurações de JVM para container
ENV JAVA_OPTS="\
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:prod}"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/v1/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 4. Docker Compose — Produção

```yaml
# docker-compose.yml
version: "3.9"

services:

  backend:
    build:
      context: ./backend
      target: runtime
    image: consultorprocessos/backend:${APP_VERSION:-latest}
    container_name: cp-backend
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST:                 db
      DB_PORT:                 5432
      DB_NAME:                 ${DB_NAME}
      DB_USER:                 ${DB_USER}
      DB_PASSWORD:             ${DB_PASSWORD}
      FLYWAY_USER:             ${FLYWAY_USER}
      FLYWAY_PASSWORD:         ${FLYWAY_PASSWORD}
      REDIS_HOST:              redis
      REDIS_PORT:              6379
      REDIS_PASSWORD:          ${REDIS_PASSWORD}
      RABBITMQ_HOST:           rabbitmq
      RABBITMQ_PORT:           5672
      RABBITMQ_USER:           ${RABBITMQ_USER}
      RABBITMQ_PASSWORD:       ${RABBITMQ_PASSWORD}
      JWT_PRIVATE_KEY:         ${JWT_PRIVATE_KEY}
      JWT_PUBLIC_KEY:          ${JWT_PUBLIC_KEY}
      SMTP_HOST:               ${SMTP_HOST}
      SMTP_PORT:               ${SMTP_PORT}
      SMTP_USER:               ${SMTP_USER}
      SMTP_PASSWORD:           ${SMTP_PASSWORD}
      ADMIN_EMAIL:             ${ADMIN_EMAIL}
      APP_BASE_URL:            ${APP_BASE_URL}
      FIREBASE_CREDENTIALS_PATH: /app/secrets/firebase.json
      FIREBASE_PROJECT_ID:     ${FIREBASE_PROJECT_ID}
      CRAWLER_WORKERS_MIN:     ${CRAWLER_WORKERS_MIN:-3}
      CRAWLER_WORKERS_MAX:     ${CRAWLER_WORKERS_MAX:-10}
    volumes:
      - ./secrets/firebase.json:/app/secrets/firebase.json:ro
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - internal
    expose:
      - "8080"

  db:
    image: postgres:16-alpine
    container_name: cp-db
    restart: unless-stopped
    environment:
      POSTGRES_DB:       ${DB_NAME}
      POSTGRES_USER:     ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - db_data:/var/lib/postgresql/data
      - ./infra/postgres/init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - internal

  redis:
    image: redis:7-alpine
    container_name: cp-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD} --maxmemory 256mb --maxmemory-policy allkeys-lru
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "-a", "${REDIS_PASSWORD}", "ping"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - internal

  rabbitmq:
    image: rabbitmq:3-management-alpine
    container_name: cp-rabbitmq
    restart: unless-stopped
    environment:
      RABBITMQ_DEFAULT_USER:  ${RABBITMQ_USER}
      RABBITMQ_DEFAULT_PASS:  ${RABBITMQ_PASSWORD}
      RABBITMQ_DEFAULT_VHOST: /
    volumes:
      - rabbitmq_data:/var/lib/rabbitmq
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "ping"]
      interval: 15s
      timeout: 10s
      retries: 5
    networks:
      - internal

  nginx:
    image: nginx:alpine
    container_name: cp-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./infra/nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./infra/nginx/sites:/etc/nginx/conf.d:ro
      - certbot_certs:/etc/letsencrypt:ro
      - certbot_webroot:/var/www/certbot:ro
    depends_on:
      - backend
    networks:
      - internal

  certbot:
    image: certbot/certbot
    container_name: cp-certbot
    volumes:
      - certbot_certs:/etc/letsencrypt
      - certbot_webroot:/var/www/certbot
    entrypoint: >
      sh -c "trap exit TERM;
             while :; do
               certbot renew --webroot -w /var/www/certbot --quiet;
               sleep 12h;
             done"

volumes:
  db_data:
  redis_data:
  rabbitmq_data:
  certbot_certs:
  certbot_webroot:

networks:
  internal:
    driver: bridge
```

---

## 5. Docker Compose — Desenvolvimento

```yaml
# docker-compose.dev.yml
version: "3.9"

services:

  backend:
    build:
      context: ./backend
      target: builder        # usa estágio de build para hot-reload
    container_name: cp-backend-dev
    environment:
      SPRING_PROFILES_ACTIVE: dev
      DB_HOST:       db
      REDIS_HOST:    redis
      RABBITMQ_HOST: rabbitmq
      # Valores fixos para DEV — nunca em produção
      DB_NAME:           consultorprocessos_dev
      DB_USER:           dev
      DB_PASSWORD:       dev
      FLYWAY_USER:       dev
      FLYWAY_PASSWORD:   dev
      REDIS_PASSWORD:    ""
      RABBITMQ_USER:     dev
      RABBITMQ_PASSWORD: dev
    volumes:
      # Hot-reload: monta o código fonte dentro do container
      - ./backend/src:/app/src
      - ./backend/target:/app/target
      - maven_cache:/root/.m2
    ports:
      - "8080:8080"
      - "5005:5005"    # porta de debug remoto (JVM)
    depends_on:
      - db
      - redis
      - rabbitmq
      - mock-tribunal
    networks:
      - dev-network

  mock-tribunal:
    build:
      context: ./mock-tribunal
    container_name: cp-mock-tribunal
    ports:
      - "9000:9000"
    networks:
      - dev-network

  db:
    image: postgres:16-alpine
    container_name: cp-db-dev
    environment:
      POSTGRES_DB:       consultorprocessos_dev
      POSTGRES_USER:     dev
      POSTGRES_PASSWORD: dev
    ports:
      - "5432:5432"    # exposto para ferramentas como DBeaver
    volumes:
      - db_dev_data:/var/lib/postgresql/data
    networks:
      - dev-network

  redis:
    image: redis:7-alpine
    container_name: cp-redis-dev
    ports:
      - "6379:6379"    # exposto para Redis Insight
    networks:
      - dev-network

  rabbitmq:
    image: rabbitmq:3-management-alpine
    container_name: cp-rabbitmq-dev
    environment:
      RABBITMQ_DEFAULT_USER: dev
      RABBITMQ_DEFAULT_PASS: dev
    ports:
      - "5672:5672"
      - "15672:15672"  # management UI: http://localhost:15672
    networks:
      - dev-network

volumes:
  db_dev_data:
  maven_cache:

networks:
  dev-network:
    driver: bridge
```

**Comandos DEV:**
```bash
# Subir ambiente completo de desenvolvimento
docker compose -f docker-compose.dev.yml up -d

# Ver logs do backend em tempo real
docker compose -f docker-compose.dev.yml logs -f backend

# Acessar banco via psql
docker exec -it cp-db-dev psql -U dev -d consultorprocessos_dev

# Parar tudo
docker compose -f docker-compose.dev.yml down
```

---

## 6. Configuração do Nginx

```nginx
# infra/nginx/sites/api.conf

upstream backend {
    server backend:8080;
    keepalive 32;
}

# Redireciona HTTP para HTTPS
server {
    listen 80;
    server_name api.consultorprocessos.com.br;

    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name api.consultorprocessos.com.br;

    ssl_certificate     /etc/letsencrypt/live/api.consultorprocessos.com.br/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.consultorprocessos.com.br/privkey.pem;

    # Configurações SSL seguras
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers   ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256:ECDHE-ECDSA-AES256-GCM-SHA384:ECDHE-RSA-AES256-GCM-SHA384;
    ssl_prefer_server_ciphers off;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 1d;

    # Headers de segurança
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
    add_header X-Content-Type-Options    "nosniff"                                      always;
    add_header X-Frame-Options           "DENY"                                         always;
    add_header X-XSS-Protection          "1; mode=block"                                always;
    add_header Referrer-Policy           "strict-origin-when-cross-origin"              always;

    # Rate limiting — definido no nginx.conf principal
    limit_req zone=api_general burst=20 nodelay;

    # Proxy para o backend
    location /v1/ {
        proxy_pass         http://backend;
        proxy_http_version 1.1;
        proxy_set_header   Host              $host;
        proxy_set_header   X-Real-IP         $remote_addr;
        proxy_set_header   X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header   X-Forwarded-Proto $scheme;
        proxy_set_header   Connection        "";

        proxy_connect_timeout 10s;
        proxy_read_timeout    60s;
        proxy_send_timeout    60s;

        # Desabilita buffering para SSE / streaming futuro
        proxy_buffering off;
    }

    # Health check — sem rate limit
    location /v1/health {
        proxy_pass http://backend;
        limit_req  off;
    }
}
```

```nginx
# infra/nginx/nginx.conf (configurações globais)

worker_processes auto;
error_log /var/log/nginx/error.log warn;

events {
    worker_connections 1024;
}

http {
    # Rate limiting — 60 req/min por IP para endpoints gerais
    limit_req_zone $binary_remote_addr zone=api_general:10m rate=1r/s;

    # Rate limiting mais restritivo para auth
    limit_req_zone $binary_remote_addr zone=api_auth:10m rate=10r/m;

    # Logs em formato JSON para facilitar parsing
    log_format json_combined escape=json
        '{'
            '"time":"$time_iso8601",'
            '"remote_addr":"$remote_addr",'
            '"method":"$request_method",'
            '"uri":"$request_uri",'
            '"status":$status,'
            '"body_bytes":$body_bytes_sent,'
            '"request_time":$request_time,'
            '"upstream_response_time":"$upstream_response_time"'
        '}';

    access_log /var/log/nginx/access.log json_combined;

    include /etc/nginx/conf.d/*.conf;
}
```

---

## 7. Variáveis de Ambiente

Todas as configurações sensíveis vivem em variáveis de ambiente. O arquivo `.env` **nunca é comitado**.

### 7.1 `.env.example` (comitado no repositório)

```bash
# ── Banco de Dados ─────────────────────────────────────────────
DB_NAME=consultorprocessos
DB_USER=app_consultorprocessos
DB_PASSWORD=
FLYWAY_USER=flyway_consultorprocessos
FLYWAY_PASSWORD=

# ── Redis ──────────────────────────────────────────────────────
REDIS_PASSWORD=

# ── RabbitMQ ───────────────────────────────────────────────────
RABBITMQ_USER=cp_app
RABBITMQ_PASSWORD=

# ── JWT (RS256 — chaves geradas com openssl) ───────────────────
# openssl genrsa -out private.pem 2048
# openssl rsa -in private.pem -pubout -out public.pem
JWT_PRIVATE_KEY=
JWT_PUBLIC_KEY=

# ── SMTP ───────────────────────────────────────────────────────
SMTP_HOST=smtp.sendgrid.net
SMTP_PORT=587
SMTP_USER=apikey
SMTP_PASSWORD=

# ── Firebase Cloud Messaging ───────────────────────────────────
FIREBASE_PROJECT_ID=
# Arquivo firebase.json montado via volume

# ── Aplicação ──────────────────────────────────────────────────
APP_BASE_URL=https://app.consultorprocessos.com.br
APP_VERSION=1.0.0
ADMIN_EMAIL=admin@consultorprocessos.com.br

# ── Crawler Workers ────────────────────────────────────────────
CRAWLER_WORKERS_MIN=3
CRAWLER_WORKERS_MAX=10
```

### 7.2 Geração de chaves JWT

```bash
# Gera par de chaves RS256
openssl genrsa -out private.pem 2048
openssl rsa -in private.pem -pubout -out public.pem

# Converte para formato inline (uma linha) para uso na variável de ambiente
JWT_PRIVATE_KEY=$(cat private.pem | base64 -w 0)
JWT_PUBLIC_KEY=$(cat public.pem | base64 -w 0)
```

---

## 8. Pipeline de CI/CD

### 8.1 Estrutura (GitHub Actions)

```
.github/workflows/
├── ci.yml        ← roda em todo PR e push para main
└── deploy.yml    ← roda apenas em push para main (após CI verde)
```

### 8.2 `ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      # Testcontainers gerencia seus próprios containers
      # mas Docker precisa estar disponível no runner

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Run unit and integration tests
        run: ./mvnw test -Dgroups="unit,integration,security" --no-transfer-progress

      - name: Check code coverage
        run: ./mvnw jacoco:check --no-transfer-progress

      - name: Build Docker image (sem push)
        run: docker build -t consultorprocessos/backend:ci ./backend

  lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven
      - name: Run Checkstyle
        run: ./mvnw checkstyle:check --no-transfer-progress
```

### 8.3 `deploy.yml`

```yaml
name: Deploy

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    needs: [] # ci.yml deve ter passado

    steps:
      - uses: actions/checkout@v4

      - name: Set up Java 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
          cache: maven

      - name: Build JAR
        run: ./mvnw package -DskipTests --no-transfer-progress

      - name: Log in to Docker Hub
        uses: docker/login-action@v3
        with:
          username: ${{ secrets.DOCKERHUB_USER }}
          password: ${{ secrets.DOCKERHUB_TOKEN }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: true
          tags: |
            consultorprocessos/backend:latest
            consultorprocessos/backend:${{ github.sha }}

      - name: Deploy to VPS via SSH
        uses: appleboy/ssh-action@v1
        with:
          host:     ${{ secrets.VPS_HOST }}
          username: ${{ secrets.VPS_USER }}
          key:      ${{ secrets.VPS_SSH_KEY }}
          script: |
            cd /opt/consultorprocessos
            docker compose pull backend
            docker compose up -d --no-deps backend
            docker system prune -f
```

---

## 9. Processo de Deploy

### 9.1 Zero-downtime deploy

O deploy substitui apenas o container `backend` sem derrubar banco, Redis ou RabbitMQ:

```bash
# No servidor VPS
cd /opt/consultorprocessos

# Puxa a nova imagem
docker compose pull backend

# Sobe o novo container (o antigo continua servindo durante o pull)
docker compose up -d --no-deps backend

# Nginx continua roteando — o healthcheck garante que o novo
# container só recebe tráfego quando estiver pronto
```

O `HEALTHCHECK` no Dockerfile (`/v1/health`) garante que o Nginx só encaminha tráfego quando o Spring Boot estiver totalmente inicializado (incluindo migrações Flyway).

### 9.2 Rollback

```bash
# Rollback para a versão anterior usando o SHA do commit anterior
docker compose stop backend
docker run -d \
  --name cp-backend \
  --network consultorprocessos_internal \
  --env-file .env \
  consultorprocessos/backend:{SHA_ANTERIOR}
```

### 9.3 Primeiro deploy (setup do servidor)

```bash
# 1. No VPS: instalar Docker e Docker Compose
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 2. Clonar o repositório
git clone https://github.com/org/consultorprocessos /opt/consultorprocessos
cd /opt/consultorprocessos

# 3. Criar arquivo .env com as variáveis de produção
cp .env.example .env
nano .env  # preencher todas as variáveis

# 4. Criar diretório de secrets e adicionar credenciais Firebase
mkdir -p secrets
# Fazer upload do firebase.json via scp
scp firebase.json user@vps:/opt/consultorprocessos/secrets/

# 5. Emitir certificado SSL (antes de subir o Nginx)
docker compose run --rm certbot certonly \
  --webroot -w /var/www/certbot \
  -d api.consultorprocessos.com.br \
  --email admin@consultorprocessos.com.br \
  --agree-tos --non-interactive

# 6. Subir toda a stack
docker compose up -d

# 7. Verificar saúde
docker compose ps
curl https://api.consultorprocessos.com.br/v1/health
```

---

## 10. Backup

### 10.1 Script de backup (`infra/scripts/backup.sh`)

```bash
#!/bin/bash
set -e

BACKUP_DIR="/opt/backups/consultorprocessos"
DATE=$(date +%Y-%m-%d_%H-%M-%S)
DB_BACKUP="$BACKUP_DIR/db_$DATE.sql.gz"

mkdir -p "$BACKUP_DIR"

# Dump do PostgreSQL comprimido
docker exec cp-db pg_dump \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  --no-password \
  | gzip > "$DB_BACKUP"

echo "Backup criado: $DB_BACKUP"

# Remove backups com mais de 30 dias
find "$BACKUP_DIR" -name "db_*.sql.gz" -mtime +30 -delete
echo "Backups antigos removidos."

# Opcional: enviar para S3
# aws s3 cp "$DB_BACKUP" "s3://consultorprocessos-backups/db/$DATE.sql.gz"
```

### 10.2 Agendamento via cron (no servidor)

```bash
# Backup diário às 2h da manhã
0 2 * * * /opt/consultorprocessos/infra/scripts/backup.sh >> /var/log/cp-backup.log 2>&1
```

### 10.3 Script de restore (`infra/scripts/restore.sh`)

```bash
#!/bin/bash
set -e

BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
    echo "Uso: ./restore.sh <arquivo_backup.sql.gz>"
    exit 1
fi

echo "ATENÇÃO: Isso irá sobrescrever o banco de dados atual."
read -p "Confirmar? (yes/no): " confirm
[ "$confirm" != "yes" ] && exit 0

# Para o backend durante o restore
docker compose stop backend

# Restore
gunzip -c "$BACKUP_FILE" | docker exec -i cp-db psql \
  -U "$DB_USER" \
  -d "$DB_NAME"

# Reinicia o backend
docker compose start backend
echo "Restore concluído."
```

---

## 11. Observabilidade em Produção

### 11.1 Logs do Docker

```bash
# Logs do backend em tempo real
docker compose logs -f backend

# Últimas 100 linhas com timestamp
docker compose logs --tail=100 -t backend

# Filtrar por nível (com jq)
docker compose logs backend | grep '"level":"ERROR"' | jq .
```

### 11.2 Métricas com Prometheus + Grafana (futuro)

O backend já expõe métricas via Micrometer em `/actuator/prometheus`. Para habilitar dashboard:

```yaml
# Adicionar ao docker-compose.yml quando necessário
  prometheus:
    image: prom/prometheus:latest
    volumes:
      - ./infra/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
```

### 11.3 Verificação de saúde manual

```bash
# Health check geral
curl -s https://api.consultorprocessos.com.br/v1/health | jq .

# Health check detalhado (requer JWT admin)
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  https://api.consultorprocessos.com.br/v1/health/detailed | jq .

# Status das filas RabbitMQ
docker exec cp-rabbitmq rabbitmqctl list_queues name messages consumers

# Tamanho da DLQ especificamente
docker exec cp-rabbitmq rabbitmqctl list_queues name messages \
  | grep dlq
```

---

## 12. Requisitos Mínimos do Servidor

| Recurso | Mínimo (fase inicial) | Recomendado (crescimento) |
|---------|----------------------|--------------------------|
| CPU | 2 vCPUs | 4 vCPUs |
| RAM | 4 GB | 8 GB |
| Disco | 40 GB SSD | 80 GB SSD |
| SO | Ubuntu 22.04 LTS | Ubuntu 22.04 LTS |
| Docker | 24+ | 24+ |
| Banda | 100 Mbps | 200 Mbps |

> Com 4 GB de RAM: PostgreSQL (~512 MB), Redis (~256 MB), RabbitMQ (~256 MB), Backend Spring (~512–768 MB, depende de workers Playwright), Nginx (~32 MB). Total estimado: ~1,8 GB, com margem para picos.

---

## 13. Checklist de Deploy

### Pré-deploy
- [ ] CI passou (todos os testes verdes)
- [ ] Migrações Flyway testadas em staging
- [ ] Variáveis de ambiente do `.env` atualizadas se necessário
- [ ] Backup do banco realizado

### Deploy
- [ ] `docker compose pull backend`
- [ ] `docker compose up -d --no-deps backend`
- [ ] Aguardar healthcheck verde: `docker compose ps`
- [ ] Verificar logs por 2 minutos: `docker compose logs -f backend`

### Pós-deploy
- [ ] `GET /v1/health` retorna `200 OK`
- [ ] `GET /v1/health/detailed` (admin) confirma todas as dependências UP
- [ ] Scheduler executou ao menos uma vez (checar logs)
- [ ] Nenhum erro novo na DLQ: `GET /admin/dlq`
- [ ] Health scores dos tribunais estáveis
