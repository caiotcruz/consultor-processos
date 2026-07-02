# 08 — Fluxos de Negócio

**Consultor de Processos**
Software Design Document · v1.0

---

> Este documento descreve os fluxos principais do sistema em formato de sequência textual.
> Cada fluxo cobre o caminho feliz e os desvios relevantes (erros, condições alternativas).
> A referência entre parênteses indica o RF correspondente.

---

## 1. Fluxo: Cadastro de Usuário

**RF-AUTH-001 a RF-AUTH-005**

```
Cliente (Web/Mobile)              API                    Banco          E-mail (SMTP)
        │                          │                       │                  │
        │── POST /auth/register ──▶│                       │                  │
        │   { name, email, pwd }   │                       │                  │
        │                          │── valida campos       │                  │
        │                          │── verifica e-mail ──▶│                  │
        │                          │◀─ email livre ────────│                  │
        │                          │── gera hash bcrypt    │                  │
        │                          │── cria User(PENDING) ▶│                  │
        │                          │── gera token verify   │                  │
        │                          │── envia e-mail ──────────────────────────▶│
        │◀─ 201 { id, email } ─────│                       │                  │
        │                          │                       │                  │
        │   [usuário clica no link do e-mail]              │                  │
        │                          │                       │                  │
        │── POST /auth/verify ────▶│                       │                  │
        │   { token }              │── valida token        │                  │
        │                          │── User(ACTIVE) ──────▶│                  │
        │◀─ 200 { message } ───────│                       │                  │
```

**Desvios:**

| Condição | Resposta |
|----------|----------|
| E-mail já cadastrado | `409 EMAIL_ALREADY_EXISTS` |
| Campos inválidos | `400 VALIDATION_ERROR` com detalhes por campo |
| Token de verificação expirado (> 24h) | `400 INVALID_TOKEN` |
| Token já usado | `400 INVALID_TOKEN` |
| Usuário tenta verificar conta já verificada | `200 OK` (idempotente) |

---

## 2. Fluxo: Login e Renovação de Sessão

**RF-AUTH-006 a RF-AUTH-011, RF-AUTH-013**

```
Cliente                           API                    Banco           Redis
   │                               │                       │                │
   │── POST /auth/login ──────────▶│                       │                │
   │   { email, password }         │── busca User ────────▶│                │
   │                               │◀─ User ───────────────│                │
   │                               │── verifica status     │                │
   │                               │── compara hash bcrypt │                │
   │                               │── gera JWT (15min)    │                │
   │                               │── gera RefreshToken   │                │
   │                               │── salva hash RT ─────▶│                │
   │                               │── zera loginFailCount▶│                │
   │◀─ 200 { accessToken,          │                       │                │
   │         refreshToken } ───────│                       │                │
   │                               │                       │                │
   │   [15 minutos depois]         │                       │                │
   │                               │                       │                │
   │── POST /auth/refresh ────────▶│                       │                │
   │   { refreshToken }            │── calcula hash RT     │                │
   │                               │── busca RT no banco ─▶│                │
   │                               │◀─ RT válido ──────────│                │
   │                               │── revoga RT antigo ──▶│                │
   │                               │── gera novo par       │                │
   │                               │── salva novo RT hash ▶│                │
   │◀─ 200 { novo accessToken,     │                       │                │
   │         novo refreshToken } ──│                       │                │
```

**Desvios do Login:**

| Condição | Resposta |
|----------|----------|
| E-mail não encontrado | `401 INVALID_CREDENTIALS` (mensagem genérica) |
| Senha incorreta | `401 INVALID_CREDENTIALS` + incrementa `loginFailureCount` |
| `loginFailureCount >= 5` | `401 ACCOUNT_LOCKED` com `lockedUntil` |
| Conta `PENDING_VERIFICATION` | `401 EMAIL_NOT_VERIFIED` |
| Conta `SUSPENDED` | `401 ACCOUNT_SUSPENDED` |
| Conta `DELETED` | `401 INVALID_CREDENTIALS` (não revelar que existiu) |

**Desvios do Refresh:**

| Condição | Resposta |
|----------|----------|
| Token não encontrado | `401 INVALID_TOKEN` |
| Token já revogado | `401 INVALID_TOKEN` |
| Token expirado | `401 INVALID_TOKEN` |

---

## 3. Fluxo: Recuperação de Senha

**RF-AUTH-009, RF-AUTH-010**

```
Cliente                           API                    Banco          E-mail
   │                               │                       │               │
   │── POST /auth/forgot-password ▶│                       │               │
   │   { email }                   │── busca User ────────▶│               │
   │                               │                       │               │
   │                               │   [se existir]        │               │
   │                               │── revoga resets antigos▶│             │
   │                               │── gera token único    │               │
   │                               │── salva hash token ──▶│               │
   │                               │── envia e-mail ───────────────────────▶│
   │                               │                       │               │
   │                               │   [se não existir]    │               │
   │                               │── não faz nada        │               │
   │                               │   (silencioso)        │               │
   │                               │                       │               │
   │◀─ 200 { message genérico } ───│                       │               │
   │                               │                       │               │
   │   [usuário clica no link]     │                       │               │
   │                               │                       │               │
   │── POST /auth/reset-password ─▶│                       │               │
   │   { token, newPassword }      │── calcula hash token  │               │
   │                               │── busca PasswordReset▶│               │
   │                               │◀─ token válido ───────│               │
   │                               │── valida nova senha   │               │
   │                               │── gera novo hash bcrypt               │
   │                               │── atualiza User.pwd ─▶│               │
   │                               │── marca token como usado▶│            │
   │                               │── revoga todos RT ───▶│               │
   │◀─ 200 { message } ────────────│                       │               │
```

**Desvios:**

| Condição | Resposta |
|----------|----------|
| E-mail não cadastrado | `200 OK` (idêntico ao sucesso — anti-enumeração) |
| Token inválido/expirado | `400 INVALID_TOKEN` |
| Token já usado | `400 INVALID_TOKEN` |
| Nova senha igual à atual | `400 VALIDATION_ERROR` |

---

## 4. Fluxo: Cadastro de Processo

**RF-PROC-001 a RF-PROC-013**

Este é o fluxo central da aplicação. Possui três desvios principais.

### 4.1 Caminho Feliz — Processo novo, tribunal disponível

```
Cliente                    API                      Banco              Redis
   │                        │                          │                  │
   │── POST /processes ────▶│                          │                  │
   │   { processNumber,     │                          │                  │
   │     courtCode,         │                          │                  │
   │     alias }            │                          │                  │
   │                        │── 1. valida campos       │                  │
   │                        │── 2. normaliza número CNJ│                  │
   │                        │── 3. busca Court ───────▶│                  │
   │                        │◀─ Court(active=true) ────│                  │
   │                        │── 4. conta subscriptions▶│                  │
   │                        │◀─ count < maxProcesses ──│                  │
   │                        │── 5. busca Process ─────▶│                  │
   │                        │◀─ não existe ────────────│                  │
   │                        │── 6. cria Process ──────▶│                  │
   │                        │── 7. cria Subscription ─▶│                  │
   │◀─ 201 { subscription } │                          │                  │
```

### 4.2 Desvio — Processo já existe (deduplicação)

```
   │                        │                          │
   │── POST /processes ────▶│                          │
   │                        │── 5. busca Process ─────▶│
   │                        │◀─ Process EXISTE ─────────│
   │                        │── verifica se usuário    │
   │                        │   já tem subscription ──▶│
   │                        │◀─ NÃO tem ────────────────│
   │                        │── 7. cria Subscription ─▶│
   │                        │   (reutiliza Process)    │
   │◀─ 201 { subscription } │                          │
   │   (mesmo processId     │                          │
   │    de outro usuário)   │                          │
```

### 4.3 Desvio — Tribunal não disponível

```
   │                        │                          │               E-mail
   │── POST /processes ────▶│                          │                  │
   │                        │── 3. busca Court ───────▶│                  │
   │                        │◀─ NOT FOUND / active=false│                 │
   │                        │── cria CourtRequest ────▶│                  │
   │                        │── conta requests p/ tribunal▶│              │
   │                        │   [count == threshold]   │                  │
   │                        │── envia alerta ao admin ─────────────────────▶│
   │◀─ 202 { message,       │                          │                  │
   │         estimatedDays }│                          │                  │
```

**Desvios gerais:**

| Condição | Resposta |
|----------|----------|
| Número de processo inválido (não normalizável) | `400 PROCESS_NUMBER_INVALID` |
| Usuário já assina este processo | `409 SUBSCRIPTION_ALREADY_EXISTS` |
| Limite do plano atingido | `422 PROCESS_LIMIT_REACHED` |

---

## 5. Fluxo: Ciclo Completo de Monitoramento

Este é o fluxo mais importante do sistema — o que acontece nos bastidores, sem interação do usuário.

```
Scheduler (cron)           MonitoringService         RabbitMQ           Redis
      │                           │                      │                  │
      │── executa a cada Xmin ───▶│                      │                  │
      │                           │── busca processos    │                  │
      │                           │   pendentes (SQL) ───────────────────────────────▶ Banco
      │                           │◀─ lista de processos ─────────────────────────────│
      │                           │                      │                  │
      │                           │   [para cada processo]                  │
      │                           │── tenta adquirir lock ──────────────────▶│
      │                           │◀─ lock OK ──────────────────────────────│
      │                           │── publica mensagem ─▶│                  │
      │                           │   { processId,        │                  │
      │                           │     courtCode,        │                  │
      │                           │     processNumber }   │                  │
      │                           │                      │                  │
      │                           │   [lock já existe]   │                  │
      │                           │── ignora processo    │                  │
      │                           │   (já enfileirado)   │                  │
```

```
RabbitMQ              CrawlerConsumer        CourtProviderFactory      CourtProvider
    │                       │                         │                      │
    │── entrega mensagem ──▶│                         │                      │
    │                       │── getProvider(code) ───▶│                      │
    │                       │◀─ STFProvider ───────────│                      │
    │                       │── consultar(number) ─────────────────────────▶│
    │                       │                         │                      │
    │                       │                    [STFProvider internamente]  │
    │                       │                         │── tenta HTTP Crawler │
    │                       │                         │   └─ falhou?         │
    │                       │                         │── tenta Jsoup Crawler│
    │                       │                         │   └─ falhou?         │
    │                       │                         │── tenta Playwright   │
    │                       │                         │── retorna RawResponse│
    │                       │                         │                      │
    │                       │◀─ ProcessSnapshot ───────────────────────────│
```

```
CrawlerConsumer         SnapshotComparator           Banco              NotifQueue
      │                        │                       │                     │
      │── compare(snapshot) ──▶│                       │                     │
      │                        │── busca último hash ─▶│                     │
      │                        │◀─ hash anterior ───────│                     │
      │                        │── SHA256(snapshot)    │                     │
      │                        │── compara hashes      │                     │
      │                        │                       │                     │
      │                  [IGUAL — sem mudança]          │                     │
      │                        │── atualiza lastChecked▶│                     │
      │                        │── libera Redis lock ──────────────────────────────▶ Redis
      │                        │                       │                     │
      │                  [DIFERENTE — nova movimentação]│                     │
      │                        │── salva ProcessSnapshot▶│                   │
      │                        │── salva ProcessHistory ▶│                   │
      │                        │── atualiza Process ───▶│                     │
      │                        │── publica evento ────────────────────────────▶│
      │                        │── libera Redis lock ──────────────────────────────▶ Redis
```

```
NotifQueue           NotificationService      EmailChannel        PushChannel
    │                       │                      │                   │
    │── MovimentacaoEvt ───▶│                      │                   │
    │                       │── busca subscriptions│                   │
    │                       │   do processo        │                   │
    │                       │   [para cada assinante]                  │
    │                       │── busca preferências │                   │
    │                       │                      │                   │
    │                       │   [emailEnabled=true]│                   │
    │                       │── send(email) ───────▶│                   │
    │                       │◀─ OK ────────────────│                   │
    │                       │── registra NotifHist │                   │
    │                       │                      │                   │
    │                       │   [pushEnabled=true] │                   │
    │                       │── send(push) ─────────────────────────▶│
    │                       │◀─ OK ──────────────────────────────────│
    │                       │── registra NotifHist │                   │
```

---

## 6. Fluxo: Tratamento de Falha de Crawling

**RF-MON-004, RF-MON-005, RF-SCH-005**

```
CrawlerConsumer                Banco              RetryQueue            DLQ
      │                          │                    │                   │
      │   [crawl falhou]         │                    │                   │
      │── incrementa errors ────▶│                    │                   │
      │── registra CrawlerExec ─▶│                    │                   │
      │                          │                    │                   │
      │   [consecutiveErrors < 3]│                    │                   │
      │── calcula backoff delay  │                    │                   │
      │   (1min, 5min, 30min)    │                    │                   │
      │── republica na fila ─────────────────────────▶│                   │
      │                          │                    │                   │
      │   [consecutiveErrors >= 3]                    │                   │
      │── status = ERROR ───────▶│                    │                   │
      │── publica em DLQ ────────────────────────────────────────────────▶│
      │── publica evento CRAWL_ERROR                  │                   │
      │                          │                    │                   │
      │◀──────────────────── NotificationService notifica usuário ────────│
```

**Política de backoff exponencial com jitter:**

| Tentativa | Delay base | Jitter (±20%) | Delay efetivo aproximado |
|-----------|-----------|--------------|--------------------------|
| 1ª retenativa | 1 min | ±12s | 48s – 72s |
| 2ª retentativa | 5 min | ±60s | 4min – 6min |
| 3ª retentativa | 30 min | ±6min | 24min – 36min |
| 4ª tentativa | → DLQ | — | — |

---

## 7. Fluxo: Solicitação de Novo Tribunal

**RF-COURT-004, RF-COURT-005**

```
Cliente              API                    Banco               E-mail (Admin)
   │                  │                       │                       │
   │── POST /processes │                       │                       │
   │   courtCode=TRF2 │                       │                       │
   │                  │── busca Court(TRF2) ─▶│                       │
   │                  │◀─ NOT FOUND ───────────│                       │
   │                  │── cria CourtRequest ──▶│                       │
   │                  │── conta requests TRF2 ▶│                       │
   │                  │◀─ count (ex: 1) ───────│                       │
   │                  │                       │                       │
   │                  │   [count >= threshold] │                       │
   │                  │── envia e-mail admin ───────────────────────────▶│
   │                  │   "TRF2: 1 solicitação(ões)"                  │
   │                  │                       │                       │
   │◀─ 202 { message, │                       │                       │
   │   estimatedDays }│                       │                       │
```

> O threshold padrão é 1 (qualquer solicitação gera e-mail imediato). Pode ser ajustado por variável de ambiente para tribunais muito solicitados, evitando spam ao admin.

---

## 8. Fluxo: Pausa e Reativação de Acompanhamento

**RF-PROC-009, RF-PROC-010**

```
Cliente               API                      Banco
   │                   │                          │
   │── POST /processes/{id}/deactivate ──────────▶│
   │                   │── busca Subscription ───▶│
   │                   │── verifica ownership     │
   │                   │── active = false ────────▶│
   │                   │── deactivatedAt = now ───▶│
   │◀─ 200 { active: false } ─────────────────────│
   │                   │                          │
   │   [mais tarde]    │                          │
   │                   │                          │
   │── POST /processes/{id}/reactivate ──────────▶│
   │                   │── busca Subscription ───▶│
   │                   │── conta subs ativas ─────▶│
   │                   │◀─ count < maxProcesses ───│
   │                   │── active = true ─────────▶│
   │                   │── deactivatedAt = null ──▶│
   │◀─ 200 { active: true } ──────────────────────│
```

**Desvio:** Se reativar excederia o limite do plano → `422 PROCESS_LIMIT_REACHED`.

> Quando uma subscription é desativada, o processo continua sendo monitorado **se houver outros assinantes ativos**. O processo para de ser consultado apenas quando **todos os assinantes estiverem inativos**.

---

## 9. Fluxo: Health Score do Tribunal

**RF-COURT-006**

Este fluxo ocorre automaticamente após cada execução de crawling.

```
CrawlerConsumer        HealthScoreService             Banco
      │                        │                        │
      │   [após cada crawl]    │                        │
      │── notifica execução ──▶│                        │
      │                        │── busca últimas 100    │
      │                        │   execuções do tribunal▶│
      │                        │◀─ execuções ───────────│
      │                        │── calcula métricas:    │
      │                        │   successRate          │
      │                        │   avgDurationMs        │
      │                        │   retryRate            │
      │                        │── aplica fórmula score │
      │                        │── salva CourtHealthScore▶│
      │                        │── atualiza Court.score ▶│
      │                        │                        │
      │                        │   [score < 70]         │
      │                        │── publica alerta admin │
```

---

## 10. Fluxo: Reprocessamento Manual via Admin

**RF-ADM-002**

```
Admin (Painel)           API                  RabbitMQ            CrawlerConsumer
      │                   │                      │                       │
      │── POST /admin/processes/{id}/reprocess ─▶│                       │
      │                   │── busca Process ─────────────────────────────────▶ Banco
      │                   │── ignora lastCheckedAt│                       │
      │                   │── publica em crawl.requests (prioridade ALTA)▶│
      │◀─ 202 Accepted ───│                      │                       │
      │                   │                      │── consome mensagem ───▶│
      │                   │                      │   (fluxo normal de crawl)
```

---

## 11. Fluxo: Exclusão de Conta

**RF-USER-007**

```
Cliente               API                         Banco
   │                   │                             │
   │── DELETE /users/me│                             │
   │   { password,     │                             │
   │     confirmPhrase}│                             │
   │                   │── valida confirmPhrase      │
   │                   │── verifica senha atual      │
   │                   │── desativa todas subs ─────▶│
   │                   │── anonimiza dados:          │
   │                   │   name = "Usuário Removido" │
   │                   │   email = "deleted_{id}@"   │
   │                   │   passwordHash = ""         │
   │                   │── status = DELETED ─────────▶│
   │                   │── revoga todos RT ──────────▶│
   │◀─ 200 { message } │                             │
```

> Histórico de movimentações (`ProcessHistory`) e logs de crawling são preservados dissociados do usuário para fins de integridade do banco.

---

## 12. Fluxo: DEV Mode (Ambiente Controlado)

**RF-DEV-001 a RF-DEV-006**

```
Desenvolvedor         API (perfil DEV)          Mock Tribunal (:9000)
      │                      │                          │
      │── qualquer request ─▶│                          │
      │   (sem JWT)          │── DevModeFilter intercepta│
      │                      │── injeta User dev fixo   │
      │                      │── injeta Plano ADVANCED  │
      │                      │                          │
      │── POST /processes ──▶│                          │
      │   { number, court }  │── valida e salva         │
      │◀─ 201 ───────────────│                          │
      │                      │                          │
      │   [scheduler roda]   │                          │
      │                      │── consultar(number) ─────▶│
      │                      │                          │── retorna HTML simulado
      │                      │◀─ ProcessSnapshot ────────│
      │                      │── compara hash           │
      │                      │── [mudança detectada]    │
      │                      │── notifica (log apenas)  │
      │                      │                          │
      │── POST /control/inject-change ───────────────────▶│
      │◀─ OK ─────────────────────────────────────────────│
      │   [próxima consulta detectará mudança real]      │
```

**Garantia de isolamento:**
- A propriedade `app.courts.real-requests-enabled=false` em `application-dev.yml` faz com que `CourtProviderFactory` substitua todos os providers reais por `MockCourtProvider`
- Essa substituição é feita via Spring `@Profile("dev")` com `@Primary` no bean mock
- É fisicamente impossível que o perfil `dev` faça requisições reais se o código estiver correto

---

## 13. Matriz de Eventos de Domínio

Eventos publicados internamente (Spring ApplicationEvent ou RabbitMQ) que disparam outros fluxos:

| Evento | Publicado por | Consumido por | Ação |
|--------|--------------|--------------|------|
| `MovimentacaoDetectadaEvent` | `SnapshotComparator` | `NotificationService` | Notifica todos os assinantes |
| `CrawlErrorEvent` | `CrawlerConsumer` | `NotificationService` | Notifica usuário sobre falha |
| `ProcessBlockedEvent` | `CrawlerConsumer` | `AdminAlertService` | Alerta admin de bloqueio |
| `CourtRequestCreatedEvent` | `ProcessService` | `AdminNotificationService` | E-mail ao admin |
| `HealthScoreLowEvent` | `HealthScoreService` | `AdminAlertService` | Alerta admin de degradação |
| `PlanLimitReachedEvent` | `SubscriptionService` | `NotificationService` | Avisa usuário do limite |
| `CrawlRetryExhaustedEvent` | `CrawlerConsumer` | `DLQService` + `NotificationService` | Move para DLQ + notifica |
