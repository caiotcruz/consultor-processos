# 04 — Arquitetura

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Estilo Arquitetural: Monólito Modular

O sistema adota o padrão **Monólito Modular** — um único artefato deployável com fronteiras internas bem definidas entre módulos.

### Por que Monólito Modular e não Microserviços?

| Critério | Monólito Modular | Microserviços |
|----------|-----------------|---------------|
| Complexidade operacional | Baixa | Alta |
| Custo de infra inicial | Baixo (1 container) | Alto (N containers + orquestrador) |
| Latência entre módulos | Zero (chamada direta) | Rede (HTTP/gRPC) |
| Testabilidade | Alta | Moderada |
| Maturidade necessária da equipe | Moderada | Alta |
| Migração futura possível? | Sim | — |

> Os módulos comunicam-se via interfaces Java, nunca por HTTP interno. Isso preserva a opção de separar módulos em serviços independentes no futuro sem mudança de contrato.

---

## 2. Visão de Camadas

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENTES                                 │
│              Angular Web App    /    Ionic Mobile App           │
└──────────────────────────┬──────────────────────────────────────┘
                           │ HTTPS / REST + JWT
┌──────────────────────────▼──────────────────────────────────────┐
│                     API GATEWAY (Nginx)                         │
│             Proxy reverso, SSL termination, rate limit          │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│                   SPRING BOOT APPLICATION                       │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐  │
│  │  AUTH    │ │  USER    │ │ PROCESS  │ │    COURT          │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌───────────────────┐  │
│  │MONITORING│ │SCHEDULER │ │ CRAWLER  │ │  NOTIFICATION     │  │
│  └──────────┘ └──────────┘ └──────────┘ └───────────────────┘  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐                        │
│  │  ADMIN   │ │ PAYMENT  │ │  SHARED  │                        │
│  └──────────┘ └──────────┘ └──────────┘                        │
└─────┬──────────────────────────────────┬───────────────────────┘
      │                                  │
      │ JPA/JDBC                         │ RabbitMQ / Redis
┌─────▼──────┐    ┌────────────┐   ┌────▼────────────────────┐
│ PostgreSQL │    │   Redis    │   │       RabbitMQ          │
│  (dados)   │    │  (cache)   │   │       (filas)           │
└────────────┘    └────────────┘   └─────────────────────────┘
```

---

## 3. Módulos e Responsabilidades

### 3.1 AUTH
- **Responsabilidade exclusiva**: gerenciar identidade e sessão
- Registro, login, logout, refresh token, verificação de e-mail, recuperação de senha
- Emite JWTs; nunca consulta processos ou tribunais
- Depende de: `user` (para buscar entidade User), `shared`

### 3.2 USER
- **Responsabilidade exclusiva**: perfil do usuário e relacionamento com plano
- CRUD de perfil, troca de senha, preferências de notificação
- Consulta o plano do usuário para retornar limites
- Depende de: `plan` (entidade dentro do módulo), `shared`

### 3.3 PROCESS
- **Responsabilidade exclusiva**: representar processos e histórico de movimentações
- CRUD de processos, listagem, histórico
- Não sabe nada sobre como a consulta é feita
- Depende de: `court` (para validar tribunal), `subscription`, `shared`

### 3.4 COURT
- **Responsabilidade exclusiva**: catálogo de tribunais e roteamento para Providers
- Mantém o registro de tribunais, feature flags e health scores
- **Coração do sistema**: é aqui que reside o `CourtProviderFactory`
- Depende de: `shared`

### 3.5 SUBSCRIPTION
- **Responsabilidade exclusiva**: vínculo entre usuário e processo
- Implementa o modelo `Process → [Subscription] → User`
- Garante deduplicação: um processo pode ter N assinantes
- Determina o intervalo efetivo de consulta (mais frequente entre os assinantes)
- Depende de: `process`, `user`, `shared`

### 3.6 MONITORING
- **Responsabilidade exclusiva**: decidir quais processos precisam ser consultados agora
- Não faz a consulta; apenas decide e sinaliza
- Consulta `ultimaConsulta` vs. `intervaloEfetivo` por processo
- Depende de: `subscription`, `process`, `shared`

### 3.7 SCHEDULER
- **Responsabilidade exclusiva**: executar periodicamente e enfileirar consultas
- Chama `monitoring` para obter lista de processos pendentes
- Publica mensagens no RabbitMQ
- Usa Redis para lock distribuído (evita enfileiramento duplo)
- Depende de: `monitoring`, `shared`

### 3.8 CRAWLER
- **Responsabilidade exclusiva**: executar consultas aos tribunais e retornar Snapshots
- Contém Providers, Crawlers, Parsers, Validators, Normalizers, Comparators
- Consome mensagens do RabbitMQ
- Nunca é chamado diretamente pelo usuário
- Depende de: `court`, `process`, `shared`

### 3.9 NOTIFICATION
- **Responsabilidade exclusiva**: enviar notificações pelos canais configurados
- Recebe eventos de domínio; decide canal(s) com base nas preferências do usuário
- Canal atual: e-mail, push. Futuros: webhook, SMS
- Depende de: `user`, `shared`

### 3.10 ADMIN
- **Responsabilidade exclusiva**: operações administrativas e visualização interna
- Endpoints protegidos por `ROLE_ADMIN`
- Lê dados dos outros módulos sem alterar sua lógica
- Depende de: todos os módulos (somente leitura dos serviços)

### 3.11 PAYMENT (fase final)
- **Responsabilidade exclusiva**: assinaturas e cobranças
- Integra com Stripe / Mercado Pago
- Atualiza o plano do usuário após confirmação de pagamento
- Depende de: `user`, `shared`

### 3.12 SHARED
- Utilitários transversais: exceções customizadas, constantes, enums, DTOs base, respostas padronizadas, utilitários de data/string
- **Regra**: nenhuma lógica de negócio aqui; apenas infraestrutura de código

---

## 4. Estrutura de Pacotes

```
src/main/java/com/consultorprocessos/
│
├── auth/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── dto/
│   ├── entity/          (User, RefreshToken, PasswordReset)
│   └── security/        (JwtFilter, JwtService, UserDetailsImpl)
│
├── user/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── dto/
│   └── entity/          (UserProfile, NotificationPreference)
│
├── plan/
│   ├── entity/          (Plan)
│   └── repository/
│
├── process/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── dto/
│   └── entity/          (Process, ProcessHistory)
│
├── court/
│   ├── controller/
│   ├── service/
│   ├── repository/
│   ├── dto/
│   ├── entity/          (Court, CourtFeatureFlag, CourtHealthScore, CourtRequest)
│   └── provider/        (CourtProvider interface, CourtProviderFactory)
│
├── subscription/
│   ├── service/
│   ├── repository/
│   ├── dto/
│   └── entity/          (ProcessSubscription)
│
├── monitoring/
│   └── service/         (MonitoringService)
│
├── scheduler/
│   └── service/         (SchedulerService, QueuePublisher)
│
├── crawler/
│   ├── consumer/        (CrawlerMessageConsumer)
│   ├── provider/
│   │   ├── CourtProvider.java          (interface)
│   │   ├── stf/
│   │   │   ├── STFProvider.java
│   │   │   ├── STFHttpCrawler.java
│   │   │   ├── STFPlaywrightCrawler.java
│   │   │   └── STFParser.java
│   │   ├── eproc/
│   │   │   ├── EprocProvider.java
│   │   │   ├── EprocHttpCrawler.java
│   │   │   └── EprocParser.java
│   │   └── stjrj/
│   │       ├── STJRJProvider.java
│   │       ├── STJRJHttpCrawler.java
│   │       └── STJRJParser.java
│   ├── model/
│   │   ├── ProcessSnapshot.java
│   │   ├── RawResponse.java
│   │   └── CrawlResult.java
│   ├── service/
│   │   ├── SnapshotComparator.java
│   │   ├── HashGenerator.java
│   │   └── CrawlerExecutionRecorder.java
│   ├── repository/
│   │   └── CrawlerExecutionRepository.java
│   └── entity/
│       ├── CrawlerExecution.java
│       └── ParserVersion.java
│
├── notification/
│   ├── service/
│   │   ├── NotificationService.java    (orquestrador)
│   │   ├── EmailNotificationChannel.java
│   │   └── PushNotificationChannel.java
│   ├── repository/
│   ├── entity/          (NotificationHistory)
│   └── template/        (templates de e-mail)
│
├── admin/
│   ├── controller/
│   └── service/
│
├── payment/
│   ├── controller/
│   ├── service/
│   └── entity/          (PaymentSubscription, Invoice)
│
└── shared/
    ├── exception/
    ├── response/         (ApiResponse, PageResponse, ErrorResponse)
    ├── validation/       (CNJValidator, ProcessNumberNormalizer)
    ├── util/
    └── config/           (SecurityConfig, RabbitConfig, RedisConfig, etc.)
```

---

## 5. Fluxo de Consulta (Sequência Detalhada)

```
Scheduler (cron)
    │
    ▼
MonitoringService.findPendingProcesses()
    │  consulta processes com ultimaConsulta < now - intervalo
    ▼
Lista de ProcessId
    │
    ▼
QueuePublisher.publish(CrawlRequestMessage)
    │  mensagem: { processId, courtCode, processNumber }
    ▼
RabbitMQ [queue: crawl.requests]
    │
    ▼
CrawlerMessageConsumer.consume(message)
    │
    ▼
CourtProviderFactory.getProvider(courtCode)
    │  retorna o Provider correto via Spring DI + registro em Map
    ▼
CourtProvider.consultar(processNumber)
    │
    ├─► [Tentativa 1] HttpCrawler
    │       └─► falhou? → próxima estratégia
    │
    ├─► [Tentativa 2] JsoupCrawler
    │       └─► falhou? → próxima estratégia
    │
    ├─► [Tentativa 3] PlaywrightCrawler
    │       └─► falhou? → próxima estratégia
    │
    └─► [Tentativa 4] SeleniumCrawler (último recurso)
            └─► falhou? → lança CrawlException
    │
    ▼
RawResponse (HTML / JSON bruto)
    │
    ▼
Parser.parse(rawResponse) → ParsedData
    │
    ▼
Validator.validate(parsedData)
    │
    ▼
Normalizer.normalize(parsedData) → ProcessSnapshot
    │
    ▼
HashGenerator.generate(snapshot) → hash SHA-256
    │
    ▼
SnapshotComparator.compare(hash, ultimoHashSalvo)
    │
    ├─ IGUAL → atualiza apenas ultimaConsulta
    │
    └─ DIFERENTE
            │
            ▼
        ProcessRepository.save(novoSnapshot)
        ProcessHistoryRepository.save(movimentacoes)
            │
            ▼
        EventPublisher.publish(MovimentacaoDetectadaEvent)
            │
            ▼
        NotificationService.handle(event)
            │
            ├─► EmailNotificationChannel (se habilitado)
            └─► PushNotificationChannel (se habilitado)
```

---

## 6. Estratégia de Cache (Redis)

### 6.1 Cache de Curto Prazo (Deduplicação em Tempo Real)

```
Chave:   crawl:lock:{courtCode}:{processNumber}
Valor:   "PROCESSING"
TTL:     5 minutos
```

Antes de executar qualquer crawl, o worker tenta adquirir esse lock. Se já existir, significa que outro worker está consultando o mesmo processo; a mensagem é descartada ou reagendada.

### 6.2 Cache de Snapshot Recente

```
Chave:   snapshot:{courtCode}:{processNumber}
Valor:   hash SHA-256 do último snapshot
TTL:     intervalo do plano mais frequente do processo
```

Evita consultas desnecessárias ao banco apenas para comparar hash.

### 6.3 Cache de Sessão de Crawler

```
Chave:   crawler:session:{courtCode}
Valor:   cookies / headers de sessão
TTL:     configurável por tribunal (padrão: 30 minutos)
```

Alguns tribunais exigem login ou manutenção de sessão. O Redis persiste essa sessão entre consultas para evitar novo login a cada requisição.

---

## 7. Filas RabbitMQ

| Fila | Propósito | Consumer |
|------|-----------|---------|
| `crawl.requests` | Processos a consultar | `CrawlerMessageConsumer` |
| `crawl.requests.retry` | Falhas temporárias (backoff) | `CrawlerMessageConsumer` |
| `crawl.dlq` | Falhas irrecuperáveis | Administrador (manual) |
| `notifications` | Eventos de movimentação | `NotificationService` |

**Configuração de Retry:**
- Tentativa 1: imediata
- Tentativa 2: 1 minuto
- Tentativa 3: 5 minutos
- Tentativa 4: 30 minutos
- Após 4 falhas: Dead Letter Queue

---

## 8. Ambiente de Desenvolvimento (DEV Mode)

### 8.1 Ativação

```yaml
# application-dev.yml
app:
  dev-mode:
    enabled: true
    auto-login: true
    auto-user-email: dev@consultorprocessos.com
    auto-user-plan: ADVANCED
  courts:
    real-requests-enabled: false   # NUNCA consultar tribunais reais em DEV
    mock-base-url: http://localhost:9000
```

### 8.2 Mock Tribunal (porta 9000)

Servidor Spring Boot separado que simula os tribunais:

```
localhost:9000/mock/stf/{numero}         → HTML simulado do STF
localhost:9000/mock/eproc/{numero}       → HTML simulado do eProc
localhost:9000/mock/stjrj/{numero}       → HTML simulado do STJRJ

localhost:9000/control/inject-change     → Injeta mudança de movimentação
localhost:9000/control/inject-timeout    → Força timeout na próxima consulta
localhost:9000/control/inject-captcha    → Força retorno de CAPTCHA
localhost:9000/control/inject-block      → Simula bloqueio por IP
localhost:9000/control/reset             → Reseta para estado padrão
```

---

## 9. Diagrama de Componentes (CourtProvider)

```
        ┌─────────────────────────────────────────┐
        │          CourtProviderFactory           │
        │  Map<String, CourtProvider>             │
        │  getProvider("STF") → STFProvider       │
        └────────────────────┬────────────────────┘
                             │
             ┌───────────────┼───────────────┐
             │               │               │
    ┌────────▼──────┐ ┌──────▼──────┐ ┌─────▼──────────┐
    │  STFProvider  │ │EprocProvider│ │STJRJProvider   │
    │               │ │             │ │                │
    │ implements    │ │ implements  │ │ implements     │
    │ CourtProvider │ │CourtProvider│ │ CourtProvider  │
    └──────┬────────┘ └──────┬──────┘ └──────┬─────────┘
           │                 │               │
    ┌──────▼──────────────────────────────────▼─────────┐
    │              CourtProvider interface               │
    │                                                    │
    │  ProcessSnapshot consultar(String processNumber)   │
    └────────────────────────────────────────────────────┘
```

---

## 10. Regras Arquiteturais (Invioláveis)

| # | Regra |
|---|-------|
| R01 | Nenhum módulo importa classes de implementação de outro módulo; apenas interfaces e DTOs do `shared` |
| R02 | Controllers nunca contêm lógica de negócio |
| R03 | Repositórios nunca são injetados em controllers |
| R04 | Nenhum `if (tribunal.equals("STF"))` fora do próprio `STFProvider` |
| R05 | `CourtProvider` nunca retorna HTML, DOM nodes, objetos Jsoup ou WebDriver |
| R06 | O scheduler nunca executa crawls diretamente; apenas publica na fila |
| R07 | O crawler nunca notifica o usuário diretamente; apenas publica eventos |
| R08 | Em DEV, nenhuma requisição real é feita a tribunais |
| R09 | Dados de produção nunca são acessíveis em ambiente de desenvolvimento |
| R10 | Toda nova entidade do banco requer uma migração Flyway numerada sequencialmente |
