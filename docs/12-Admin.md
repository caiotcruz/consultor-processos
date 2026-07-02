# 12 — Painel Administrativo

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Visão Geral

O módulo Admin é a interface de operação e observabilidade do sistema. Ele **lê dados de todos os outros módulos** sem duplicar lógica — cada serviço expõe suas próprias queries e o Admin orquestra a visualização.

**Princípios:**
- Admin nunca contém lógica de negócio própria; delega para os serviços dos outros módulos
- Todos os endpoints exigem `ROLE_ADMIN`
- Ações destrutivas exigem confirmação explícita no payload
- Toda ação administrativa é registrada em `audit_log`

---

## 2. Controle de Acesso

### 2.1 Role ADMIN

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/v1/admin/**").hasRole("ADMIN")
            .requestMatchers("/v1/auth/**").permitAll()
            .anyRequest().authenticated()
        );
        return http.build();
    }
}
```

### 2.2 Criação de usuários admin

Usuários admin **não são criados pelo fluxo de registro normal**. São promovidos manualmente via script SQL ou endpoint dedicado com autenticação de serviço:

```sql
-- Adiciona role ADMIN a um usuário existente
INSERT INTO user_roles (user_id, role)
VALUES ('{uuid}', 'ROLE_ADMIN');
```

### 2.3 Tabela `user_roles`

```sql
CREATE TABLE user_roles (
    user_id  UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role     VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, role)
);

CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
```

---

## 3. Audit Log

Toda ação administrativa fica registrada — quem fez, o quê, quando e com qual payload.

### 3.1 Tabela `audit_log`

```sql
CREATE TABLE audit_log (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id     UUID         NOT NULL REFERENCES users(id),
    action       VARCHAR(100) NOT NULL,   -- ex: COURT_ACTIVATED, DLQ_REPROCESSED
    entity_type  VARCHAR(50)  NULL,       -- ex: COURT, PROCESS, USER
    entity_id    VARCHAR(100) NULL,       -- UUID ou código da entidade afetada
    payload      JSONB        NULL,       -- snapshot do request recebido
    ip_address   VARCHAR(45)  NULL,
    performed_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_admin_id     ON audit_log(admin_id);
CREATE INDEX idx_audit_performed_at ON audit_log(performed_at DESC);
CREATE INDEX idx_audit_action       ON audit_log(action);
```

### 3.2 `AuditLogService`

```java
@Service
public class AuditLogService {

    private final AuditLogRepository repository;

    public void record(UUID adminId, String action,
                       String entityType, String entityId,
                       Object payload, String ipAddress) {
        AuditLog entry = new AuditLog();
        entry.setAdminId(adminId);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setPayload(toJsonb(payload));
        entry.setIpAddress(ipAddress);
        entry.setPerformedAt(Instant.now());
        repository.save(entry);
    }
}
```

### 3.3 Anotação de conveniência

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audited {
    String action();
    String entityType() default "";
}

// Uso:
@Audited(action = "COURT_ACTIVATED", entityType = "COURT")
@PatchMapping("/admin/courts/{code}")
public ResponseEntity<?> updateCourt(...) { ... }
```

Um `@Aspect` intercepta métodos anotados, extrai o admin autenticado do `SecurityContext` e grava o log automaticamente.

---

## 4. Dashboard Principal

### 4.1 `GET /admin/dashboard`

Snapshot do estado atual do sistema. Consumido pela tela inicial do painel admin.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "users": {
      "total":        1250,
      "active":       1100,
      "newLast7Days": 48,
      "byPlan": {
        "GRATUITO": 900,
        "BASICO":   250,
        "AVANCADO": 100
      }
    },
    "processes": {
      "total":              4800,
      "activeSubscriptions": 5200,
      "byStatus": {
        "PENDING": 50,
        "OK":      4600,
        "ERROR":   130,
        "BLOCKED": 20
      }
    },
    "courts": {
      "total":  3,
      "active": 3,
      "degraded": [
        { "code": "EPROC", "healthScore": 62 }
      ]
    },
    "queues": {
      "crawlRequests":  42,
      "crawlRetry":      8,
      "crawlDlq":        3,
      "notifications":   0
    },
    "crawlerLast24h": {
      "totalExecutions": 28400,
      "successRate":     0.961,
      "avgDurationMs":   2100,
      "byStrategy": {
        "HTTP":       22000,
        "JSOUP":       4800,
        "PLAYWRIGHT":  1500,
        "SELENIUM":     100
      }
    },
    "courtRequests": {
      "pending": 3
    }
  }
}
```

---

## 5. Gestão de Tribunais

### 5.1 Listagem com detalhes completos

`GET /admin/courts` — descrito em `07-API.md`, seção 7.

### 5.2 Ativação / Desativação

```
PATCH /admin/courts/{code}
Body: { "active": true }
```

**Comportamento ao desativar:**
- Novos cadastros de processo para o tribunal são bloqueados
- Processos já cadastrados **continuam sendo monitorados** (subscriptions existentes não são afetadas)
- O tribunal aparece como "indisponível" para os usuários na listagem

**Comportamento ao reativar:**
- Imediatamente disponível para novos cadastros

**Auditoria registrada:** `COURT_ACTIVATED` / `COURT_DEACTIVATED`

### 5.3 Feature Flags em tempo real

```
PUT /admin/courts/{code}/feature-flags
Body: { "PLAYWRIGHT_ENABLED": true, "SELENIUM_ENABLED": false }
```

As flags são lidas pelo `CourtProvider` a cada execução via cache Redis com TTL curto (30 segundos), garantindo propagação rápida sem overhead de banco por consulta.

```java
@Component
public class FeatureFlagService {

    private final CourtFeatureFlagRepository repository;
    private final RedisTemplate<String, Boolean> redis;
    private static final Duration FLAG_CACHE_TTL = Duration.ofSeconds(30);

    public boolean isEnabled(String courtCode, String flagKey) {
        String cacheKey = "flag:" + courtCode + ":" + flagKey;

        Boolean cached = redis.opsForValue().get(cacheKey);
        if (cached != null) return cached;

        boolean value = repository
            .findByCourtCodeAndFlagKey(courtCode, flagKey)
            .map(CourtFeatureFlag::isEnabled)
            .orElse(false);

        redis.opsForValue().set(cacheKey, value, FLAG_CACHE_TTL);
        return value;
    }

    public void update(String courtCode, String flagKey, boolean enabled, String adminEmail) {
        repository.upsert(courtCode, flagKey, enabled, adminEmail);
        // Invalida cache imediatamente
        redis.delete("flag:" + courtCode + ":" + flagKey);
    }
}
```

### 5.4 Versões de Parser

```
GET  /admin/courts/{code}/parser-versions
POST /admin/courts/{code}/parser-versions
Body: { "version": "1.2.0", "description": "Ajuste após atualização de março/2025" }
```

Ao criar nova versão:
1. A versão anterior é marcada `active = false`
2. A nova versão é marcada `active = true`
3. Cache de feature flags do tribunal é invalidado
4. Auditoria registrada: `PARSER_VERSION_RELEASED`

### 5.5 Health Score — Histórico

```
GET /admin/courts/{code}/health?from=2025-03-01&to=2025-03-15
```

Retorna série temporal dos health scores para visualização em gráfico no painel.

---

## 6. Gestão de Processos (Admin)

### 6.1 Reprocessamento manual

```
POST /admin/processes/{processId}/reprocess
```

Força enfileiramento imediato, ignorando `lastCheckedAt`. Útil para:
- Testar um novo parser imediatamente após deploy
- Recuperar um processo que entrou em ERROR incorretamente
- Debug de comportamento de um tribunal específico

**Auditoria:** `PROCESS_REQUEUED`

### 6.2 Forçar status

```
PATCH /admin/processes/{processId}/status
Body: { "status": "OK", "reason": "Reativado manualmente após falha transitória do tribunal" }
```

Permite ao admin resetar um processo de `ERROR` para `OK` quando a causa foi externa (ex: tribunal ficou offline por manutenção).

**Auditoria:** `PROCESS_STATUS_OVERRIDDEN`

### 6.3 Busca de processo pelo admin

```
GET /admin/processes?processNumber=0001234-55.2020.8.26.0001&courtCode=STF
```

Retorna dados internos do processo (não visíveis ao usuário comum): número de subscriptions, histórico de crawling, todos os snapshots, versões de parser utilizadas.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id":                  "proc-uuid-1",
    "processNumber":       "0001234-55.2020.8.26.0001",
    "court":               { "code": "STF", "name": "Supremo Tribunal Federal" },
    "status":              "OK",
    "lastCheckedAt":       "2025-03-15T12:00:00Z",
    "lastMovementAt":      "2025-03-10T09:00:00Z",
    "lastSnapshotHash":    "a3f5c2...",
    "consecutiveErrors":   0,
    "activeSubscriptions": 3,
    "totalSubscriptions":  5,
    "recentExecutions": [
      {
        "strategy":    "HTTP",
        "success":     true,
        "durationMs":  1820,
        "executedAt":  "2025-03-15T12:00:00Z"
      }
    ]
  }
}
```

---

## 7. Gestão da Dead Letter Queue (DLQ)

### 7.1 Listagem

```
GET /admin/dlq?page=0&size=20
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "messageId":      "msg-uuid-1",
      "processId":      "proc-uuid-1",
      "processNumber":  "0001234-55.2020.8.26.0001",
      "courtCode":      "EPROC",
      "retryCount":     3,
      "failureReason":  "TIMEOUT após 3 tentativas. Último erro: Connection timed out após 30s.",
      "enqueuedAt":     "2025-03-15T08:00:00Z"
    }
  ],
  "meta": { "totalElements": 3 }
}
```

### 7.2 Reprocessar mensagem individual

```
POST /admin/dlq/{messageId}/reprocess
```

Remove da DLQ e republica em `crawl.requests` com `retryCount = 0`.

**Auditoria:** `DLQ_MESSAGE_REPROCESSED`

### 7.3 Reprocessar todas as mensagens da DLQ

```
POST /admin/dlq/reprocess-all
Body: { "confirm": true }
```

Reprocessa todas as mensagens da DLQ de uma vez. Requer `"confirm": true` no body para evitar execução acidental.

**Auditoria:** `DLQ_BULK_REPROCESSED` com `payload: { count: N }`

### 7.4 Descartar mensagem

```
DELETE /admin/dlq/{messageId}
Body: { "reason": "Processo encerrado; consulta não é mais necessária." }
```

Remove permanentemente da DLQ sem reprocessar.

**Auditoria:** `DLQ_MESSAGE_DISCARDED`

---

## 8. Gestão de Solicitações de Tribunais

### 8.1 Listagem agrupada por tribunal

```
GET /admin/court-requests?status=PENDING
```

Agrupa por `courtCode` para facilitar priorização:

```json
{
  "success": true,
  "data": [
    {
      "courtName":     "TRF da 2ª Região",
      "courtCode":     "TRF2",
      "requestCount":  14,
      "status":        "PENDING",
      "lastRequestAt": "2025-03-14T20:00:00Z",
      "requests": [
        {
          "id":            "req-uuid-1",
          "userId":        "user-uuid-1",
          "processNumber": "5001234-88.2024.4.02.5001",
          "createdAt":     "2025-03-10T14:00:00Z"
        }
      ]
    }
  ]
}
```

### 8.2 Atualizar status

```
PATCH /admin/court-requests/{id}
Body: { "status": "IN_PROGRESS", "adminNotes": "Iniciando implementação. Previsão: 5 dias." }
```

Quando o status muda para `DONE`, todos os usuários que solicitaram o tribunal recebem notificação por e-mail.

**Auditoria:** `COURT_REQUEST_STATUS_UPDATED`

---

## 9. Gestão de Usuários (Admin)

### 9.1 Listagem com filtros

```
GET /admin/users?status=ACTIVE&plan=GRATUITO&search=joao&page=0&size=20
```

### 9.2 Detalhe de usuário

```
GET /admin/users/{userId}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id":            "user-uuid-1",
    "name":          "João Silva",
    "email":         "joao@email.com",
    "plan":          "GRATUITO",
    "status":        "ACTIVE",
    "createdAt":     "2025-01-10T09:00:00Z",
    "lastLoginAt":   "2025-03-15T14:30:00Z",
    "loginFailureCount": 0,
    "usage": {
      "activeSubscriptions": 3,
      "maxProcesses":        5
    },
    "notificationPreferences": {
      "emailEnabled": true,
      "pushEnabled":  false
    }
  }
}
```

> E-mail é exibido completo apenas neste endpoint admin — com log de auditoria de que foi acessado.

### 9.3 Suspender / Reativar usuário

```
POST /admin/users/{userId}/suspend
Body: { "reason": "Violação dos termos de uso — scraping massivo." }

POST /admin/users/{userId}/reactivate
Body: { "reason": "Situação regularizada." }
```

**Auditoria:** `USER_SUSPENDED` / `USER_REACTIVATED`

### 9.4 Alterar plano manualmente

```
PATCH /admin/users/{userId}/plan
Body: { "planName": "AVANCADO", "reason": "Cortesia por erro de sistema." }
```

**Auditoria:** `USER_PLAN_CHANGED`

---

## 10. Execuções de Crawling

### 10.1 Listagem filtrada

```
GET /admin/crawler-executions
  ?courtCode=EPROC
  &success=false
  &strategy=PLAYWRIGHT
  &from=2025-03-15T00:00:00Z
  &to=2025-03-15T23:59:59Z
  &page=0&size=50
```

Retorna execuções com todos os campos de diagnóstico: duração, estratégia usada, tipo de erro, versão do parser, IP de saída (se aplicável).

### 10.2 Estatísticas por tribunal

```
GET /admin/crawler-executions/stats?courtCode=STF&period=24h
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "courtCode":     "STF",
    "period":        "24h",
    "total":         9600,
    "successful":    9312,
    "failed":         288,
    "successRate":   0.970,
    "avgDurationMs": 1840,
    "byStrategy": {
      "HTTP":       8000,
      "JSOUP":      1000,
      "PLAYWRIGHT":  550,
      "SELENIUM":     50
    },
    "errorBreakdown": {
      "TIMEOUT":      200,
      "PARSE_ERROR":   60,
      "BLOCKED":       18,
      "CAPTCHA":       10
    }
  }
}
```

---

## 11. Logs Estruturados

### 11.1 Endpoint de logs

```
GET /admin/logs
  ?level=ERROR
  &module=crawler
  &from=2025-03-15T10:00:00Z
  &to=2025-03-15T12:00:00Z
  &traceId=abc123
  &page=0&size=100
```

O backend expõe logs armazenados no banco (não lê arquivos de log do sistema). Para isso, um `DatabaseLogAppender` persiste logs de nível `WARN` e acima na tabela `system_logs`.

### 11.2 Tabela `system_logs`

```sql
CREATE TABLE system_logs (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    level       VARCHAR(10)  NOT NULL,    -- DEBUG, INFO, WARN, ERROR
    module      VARCHAR(100) NOT NULL,    -- ex: crawler, scheduler, notification
    trace_id    VARCHAR(64)  NULL,
    message     TEXT         NOT NULL,
    context     JSONB        NULL,        -- dados estruturados adicionais
    logged_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_logs_level     ON system_logs(level, logged_at DESC);
CREATE INDEX idx_logs_module    ON system_logs(module, logged_at DESC);
CREATE INDEX idx_logs_trace_id  ON system_logs(trace_id) WHERE trace_id IS NOT NULL;
CREATE INDEX idx_logs_logged_at ON system_logs(logged_at DESC);
```

> Apenas `WARN` e `ERROR` são persistidos no banco para evitar volume excessivo. Logs de `INFO` e `DEBUG` ficam apenas no stdout (Docker logs / CloudWatch / Loki).

### 11.3 Retenção de logs

- `ERROR`: 90 dias
- `WARN`: 30 dias
- Job de limpeza: todo domingo às 4h

---

## 12. Audit Log — Consulta

```
GET /admin/audit-log
  ?adminId={uuid}
  &action=COURT_ACTIVATED
  &entityType=COURT
  &from=2025-03-01
  &to=2025-03-31
  &page=0&size=50
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":          "audit-uuid-1",
      "admin": {
        "id":    "admin-uuid-1",
        "email": "admin@consultorprocessos.com.br"
      },
      "action":      "COURT_ACTIVATED",
      "entityType":  "COURT",
      "entityId":    "TRF2",
      "payload":     { "active": true },
      "ipAddress":   "189.xxx.xxx.xxx",
      "performedAt": "2025-03-15T16:00:00Z"
    }
  ],
  "meta": { "totalElements": 1 }
}
```

---

## 13. `AdminService` — Estrutura Interna

O `AdminService` não reimplementa lógica — delega para os serviços corretos de cada módulo:

```java
@Service
@RequiredArgsConstructor
public class AdminService {

    private final CourtService         courtService;
    private final ProcessRepository    processRepository;
    private final DlqService           dlqService;
    private final UserService          userService;
    private final CrawlerExecutionRepository crawlerExecutionRepository;
    private final AuditLogService      auditLogService;
    private final QueuePublisher       queuePublisher;
    private final FeatureFlagService   featureFlagService;

    public void reprocessProcess(UUID processId, UUID adminId, String ip) {
        Process process = processRepository.findById(processId)
            .orElseThrow(() -> new NotFoundException("Processo não encontrado"));

        queuePublisher.forcePublish(new PendingProcess(
            process.getId(), process.getCourt().getCode(),
            process.getProcessNumber()
        ));

        auditLogService.record(adminId, "PROCESS_REQUEUED",
            "PROCESS", processId.toString(), null, ip);
    }

    public void overrideProcessStatus(UUID processId, ProcessStatus newStatus,
                                      String reason, UUID adminId, String ip) {
        processRepository.updateStatus(processId, newStatus);
        processRepository.resetConsecutiveErrors(processId);

        auditLogService.record(adminId, "PROCESS_STATUS_OVERRIDDEN",
            "PROCESS", processId.toString(),
            Map.of("newStatus", newStatus, "reason", reason), ip);
    }

    public void updateCourtActive(String courtCode, boolean active,
                                  UUID adminId, String ip) {
        courtService.setActive(courtCode, active);
        featureFlagService.invalidateCache(courtCode);

        String action = active ? "COURT_ACTIVATED" : "COURT_DEACTIVATED";
        auditLogService.record(adminId, action, "COURT", courtCode,
            Map.of("active", active), ip);
    }
}
```

---

## 14. `AdminController`

```java
@RestController
@RequestMapping("/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardDto>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboard()));
    }

    @PostMapping("/processes/{processId}/reprocess")
    @Audited(action = "PROCESS_REQUEUED", entityType = "PROCESS")
    public ResponseEntity<ApiResponse<MessageDto>> reprocessProcess(
            @PathVariable UUID processId,
            Authentication auth,
            HttpServletRequest request) {
        adminService.reprocessProcess(processId,
            extractAdminId(auth), request.getRemoteAddr());
        return ResponseEntity.accepted()
            .body(ApiResponse.success(new MessageDto("Processo enfileirado para reprocessamento.")));
    }

    @PatchMapping("/courts/{code}")
    @Audited(action = "COURT_UPDATED", entityType = "COURT")
    public ResponseEntity<ApiResponse<CourtDto>> updateCourt(
            @PathVariable String code,
            @RequestBody @Valid UpdateCourtRequest body,
            Authentication auth,
            HttpServletRequest request) {
        CourtDto updated = adminService.updateCourt(code, body,
            extractAdminId(auth), request.getRemoteAddr());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    // demais endpoints seguem o mesmo padrão
}
```

---

## 15. Segurança Adicional do Painel Admin

| Medida | Implementação |
|--------|--------------|
| Todos os endpoints exigem `ROLE_ADMIN` | `@PreAuthorize("hasRole('ADMIN')")` no controller |
| IP de origem registrado em todo audit log | `HttpServletRequest.getRemoteAddr()` |
| Rate limiting mais permissivo para admins | 120 req/min vs 60 para usuários comuns |
| Ações bulk exigem `"confirm": true` no payload | Validação manual no service |
| E-mails de usuários mascarados por padrão | Mascaramento na query de listagem |
| E-mail completo visível apenas em `GET /admin/users/{id}` | Endpoint separado + audit log automático |
| Sessão admin com TTL de JWT reduzido | `admin.jwt.ttl-minutes: 30` (vs 15 padrão) |

---

## 16. Resumo de Ações e Auditoria

| Ação | Endpoint | Auditoria |
|------|----------|-----------|
| Ativar/desativar tribunal | `PATCH /admin/courts/{code}` | `COURT_ACTIVATED` / `COURT_DEACTIVATED` |
| Atualizar feature flags | `PUT /admin/courts/{code}/feature-flags` | `FEATURE_FLAG_UPDATED` |
| Registrar versão de parser | `POST /admin/courts/{code}/parser-versions` | `PARSER_VERSION_RELEASED` |
| Reprocessar processo | `POST /admin/processes/{id}/reprocess` | `PROCESS_REQUEUED` |
| Forçar status de processo | `PATCH /admin/processes/{id}/status` | `PROCESS_STATUS_OVERRIDDEN` |
| Reprocessar mensagem DLQ | `POST /admin/dlq/{id}/reprocess` | `DLQ_MESSAGE_REPROCESSED` |
| Reprocessar toda DLQ | `POST /admin/dlq/reprocess-all` | `DLQ_BULK_REPROCESSED` |
| Descartar mensagem DLQ | `DELETE /admin/dlq/{id}` | `DLQ_MESSAGE_DISCARDED` |
| Atualizar status court request | `PATCH /admin/court-requests/{id}` | `COURT_REQUEST_STATUS_UPDATED` |
| Suspender usuário | `POST /admin/users/{id}/suspend` | `USER_SUSPENDED` |
| Reativar usuário | `POST /admin/users/{id}/reactivate` | `USER_REACTIVATED` |
| Alterar plano de usuário | `PATCH /admin/users/{id}/plan` | `USER_PLAN_CHANGED` |
| Acessar e-mail completo de usuário | `GET /admin/users/{id}` | `USER_PII_ACCESSED` |
