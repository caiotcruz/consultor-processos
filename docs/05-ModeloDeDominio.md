# 05 — Modelo de Domínio

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Visão Geral das Entidades

```
┌──────────┐        ┌──────────────┐        ┌─────────┐
│   User   │───────▶│     Plan     │        │  Court  │
└────┬─────┘  N:1   └──────────────┘        └────┬────┘
     │                                           │
     │  1:N                                      │ 1:N
     │                                           │
┌────▼───────────────┐              ┌────────────▼────────────┐
│ ProcessSubscription│◀────────────▶│        Process          │
└────────────────────┘    N:M       └────────────┬────────────┘
                                                 │
                        ┌────────────────────────┼──────────────┐
                        │                        │              │
               ┌────────▼────────┐  ┌────────────▼──────┐  ┌───▼──────────────┐
               │ ProcessSnapshot │  │  ProcessHistory   │  │ CrawlerExecution │
               └─────────────────┘  └───────────────────┘  └──────────────────┘
```

---

## 2. Entidades do Domínio

---

### 2.1 `User`

Representa o usuário da plataforma.

**Responsabilidade:** Armazenar identidade, credenciais e vínculo com plano.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | Identificador único | PK, gerado automaticamente |
| `name` | String | Nome completo | Não nulo, 2–150 chars |
| `email` | String | E-mail de acesso | Único, não nulo, validado |
| `passwordHash` | String | Senha com bcrypt | Não nulo, nunca exposto em API |
| `plan` | Plan | Plano atual | FK obrigatória |
| `status` | UserStatus | Status da conta | `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DELETED` |
| `emailVerifiedAt` | Instant | Data de verificação | Nulo até verificar |
| `lastLoginAt` | Instant | Último login | Atualizado a cada login bem-sucedido |
| `loginFailureCount` | Integer | Tentativas falhas consecutivas | Reset ao logar com sucesso |
| `lockedUntil` | Instant | Bloqueio temporário | Nulo se não bloqueado |
| `createdAt` | Instant | Data de criação | Preenchido automaticamente |
| `updatedAt` | Instant | Última atualização | Preenchido automaticamente |

**Regras de negócio:**
- Um usuário com `status = PENDING_VERIFICATION` não pode fazer login
- `loginFailureCount >= 5` → `lockedUntil = now + 15min` e count resetado
- Ao deletar conta (`status = DELETED`), os dados pessoais são anonimizados, mas o histórico de processos é mantido por obrigação legal
- O e-mail deve ser único no sistema; verificar antes de salvar

---

### 2.2 `Plan`

Define os limites e configurações de cada plano de assinatura.

**Responsabilidade:** Ser a fonte única da verdade sobre limites de uso. Nunca hardcoded.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | Identificador único | PK |
| `name` | String | Nome do plano | Ex: `GRATUITO`, `BASICO`, `AVANCADO` |
| `displayName` | String | Nome para exibição | Ex: "Plano Gratuito" |
| `maxProcesses` | Integer | Máx. de processos | `null` = ilimitado |
| `checkIntervalHours` | Integer | Intervalo de consulta em horas | 4, 8 ou 12 |
| `price` | BigDecimal | Preço mensal | `0.00` para gratuito |
| `active` | Boolean | Disponível para novos usuários | Planos descontinuados ficam `false` |
| `createdAt` | Instant | Data de criação | Automático |

**Planos iniciais:**

| name | maxProcesses | checkIntervalHours | price |
|------|--------------|--------------------|-------|
| `GRATUITO` | 5 | 12 | 0.00 |
| `BASICO` | 10 | 8 | TBD |
| `AVANCADO` | null | 4 | TBD |

**Regras de negócio:**
- `maxProcesses = null` indica processos ilimitados
- Planos não são deletados; apenas desativados (`active = false`)
- Um usuário pode ter um plano desativado se já estava assinante quando o plano foi descontinuado

---

### 2.3 `RefreshToken`

Representa tokens de sessão de longa duração.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | Identificador único | PK |
| `user` | User | Proprietário do token | FK não nula |
| `tokenHash` | String | Hash do token (SHA-256) | Único; token real nunca armazenado |
| `expiresAt` | Instant | Data de expiração | now + 7 dias |
| `revokedAt` | Instant | Data de revogação | Nulo se ainda válido |
| `createdAt` | Instant | Data de criação | Automático |
| `userAgent` | String | Navegador/dispositivo | Para auditoria |
| `ipAddress` | String | IP de origem | Para auditoria |

**Regras de negócio:**
- A cada uso do refresh token, o token atual é revogado e um novo é emitido (rotation)
- Um usuário pode ter múltiplos refresh tokens ativos (múltiplos dispositivos)
- Refresh tokens expirados ou revogados nunca são deletados; permanecem para auditoria

---

### 2.4 `PasswordReset`

Controla o fluxo de recuperação de senha.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | Identificador único | PK |
| `user` | User | Usuário solicitante | FK não nula |
| `tokenHash` | String | Hash do token enviado por e-mail | Único |
| `expiresAt` | Instant | Expiração do link | now + 1 hora |
| `usedAt` | Instant | Data de uso | Nulo se ainda não usado |
| `createdAt` | Instant | Data de criação | Automático |

**Regras de negócio:**
- Token é válido apenas se `usedAt = null` AND `expiresAt > now`
- Ao usar o token, `usedAt` é preenchido; qualquer uso subsequente é rejeitado
- Ao solicitar novo reset, tokens anteriores não usados são revogados (ou mantidos, a definir)

---

### 2.5 `Court`

Representa um tribunal cadastrado no sistema.

**Responsabilidade:** Catálogo de tribunais e configurações de consulta.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | Identificador único | PK |
| `name` | String | Nome completo | Ex: "Supremo Tribunal Federal" |
| `code` | String | Código único interno | Ex: `STF`, `EPROC`, `STJRJ`; Único, uppercase |
| `providerClass` | String | Nome da classe Provider | Ex: `STFProvider`; usado pelo Factory |
| `active` | Boolean | Disponível para usuários | Tribunais inativos não aceitam novos processos |
| `rateLimitPerMinute` | Integer | Requisições máx. por minuto | Configurável por tribunal |
| `minDelayMs` | Integer | Delay mínimo entre requisições (ms) | Aleatório entre min e max |
| `maxDelayMs` | Integer | Delay máximo entre requisições (ms) | Aleatório entre min e max |
| `healthScore` | Integer | Score de saúde 0–100 | Recalculado a cada execução |
| `createdAt` | Instant | Data de criação | Automático |
| `updatedAt` | Instant | Última atualização | Automático |

**Regras de negócio:**
- `code` é imutável após a criação; é a chave de roteamento para o Provider
- Desativar um tribunal não interrompe o monitoramento de processos já cadastrados; apenas impede novos cadastros
- O `providerClass` deve corresponder a um bean Spring registrado

---

### 2.6 `CourtFeatureFlag`

Feature flags configuráveis por tribunal sem necessidade de deploy.

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | UUID | PK |
| `court` | Court | Tribunal associado |
| `flagKey` | String | Ex: `PLAYWRIGHT_ENABLED`, `PARSER_V2`, `RETRY_EXTRA` |
| `enabled` | Boolean | Estado atual |
| `updatedAt` | Instant | Última alteração |
| `updatedBy` | String | Admin que alterou |

---

### 2.7 `CourtRequest`

Registra solicitações de usuários por tribunais não disponíveis.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `user` | User | Usuário solicitante | FK |
| `courtName` | String | Nome informado pelo usuário | Como o usuário descreveu |
| `courtCode` | String | Código inferido pelo sistema | Pode ser nulo se não identificado |
| `processNumber` | String | Número do processo que tentou cadastrar | |
| `status` | CourtRequestStatus | `PENDING`, `IN_PROGRESS`, `DONE`, `REJECTED` | |
| `adminNotes` | String | Observações do admin | Opcional |
| `createdAt` | Instant | Data da solicitação | Automático |

**Regras de negócio:**
- Quando criada, dispara verificação: se for a primeira solicitação para aquele tribunal, envia e-mail imediato ao admin
- O admin pode agrupar múltiplas solicitações do mesmo tribunal e priorizá-las

---

### 2.8 `Process`

Representa um processo judicial único no sistema (independente de quem o acompanha).

**Responsabilidade:** Identidade única de um processo — número + tribunal.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `processNumber` | String | Número normalizado (CNJ) | Único por tribunal |
| `processNumberRaw` | String | Número como o usuário digitou | Preservado para auditoria |
| `court` | Court | Tribunal | FK não nula |
| `status` | ProcessStatus | `PENDING`, `OK`, `ERROR`, `BLOCKED` | |
| `lastCheckedAt` | Instant | Data da última consulta | Nulo se nunca consultado |
| `lastMovementAt` | Instant | Data da última movimentação detectada | Nulo se nenhuma ainda |
| `lastSnapshotHash` | String | Hash SHA-256 do último snapshot | Para comparação rápida |
| `consecutiveErrors` | Integer | Erros seguidos sem sucesso | Resetado ao sucesso |
| `createdAt` | Instant | Automático | |
| `updatedAt` | Instant | Automático | |

**Regras de negócio:**
- `processNumber + court` formam uma chave natural única: dois cadastros do mesmo processo no mesmo tribunal são deduplados
- `status = BLOCKED` indica que o tribunal detectou acesso suspeito; requer atenção manual
- `consecutiveErrors >= 3` → `status = ERROR` e notificação aos assinantes

---

### 2.9 `ProcessSubscription`

Vínculo entre um usuário e um processo. Implementa o modelo de deduplicação.

**Responsabilidade:** Representar que um usuário está acompanhando um processo.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `user` | User | Assinante | FK não nula |
| `process` | Process | Processo acompanhado | FK não nula |
| `active` | Boolean | Se o acompanhamento está ativo | `false` = pausado pelo usuário |
| `alias` | String | Apelido do processo (opcional) | Ex: "Meu processo trabalhista" |
| `createdAt` | Instant | Data de início do acompanhamento | Automático |
| `deactivatedAt` | Instant | Data de pausa | Nulo se ativo |

**Regras de negócio:**
- `user + process` devem ser únicos (um usuário não pode assinar o mesmo processo duas vezes)
- Ao desativar, `active = false` e `deactivatedAt = now`; o processo continua sendo consultado se houver outros assinantes ativos
- Um processo sem nenhum assinante ativo não é enfileirado para consulta
- O limite de processos do plano é contado por subscriptions ativas, não por processos únicos

---

### 2.10 `ProcessSnapshot`

Fotografia imutável do estado de um processo em um momento específico.

**Responsabilidade:** Histórico auditável; base para detecção de mudanças.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `process` | Process | Processo ao qual pertence | FK não nula |
| `contentHash` | String | Hash SHA-256 do conteúdo | Usado para comparação |
| `rawContent` | Text | Dados brutos extraídos (JSON) | Nunca HTML; já parseado |
| `parserVersion` | ParserVersion | Versão do parser que gerou | FK não nula |
| `crawlerStrategy` | String | Estratégia usada: `HTTP`, `JSOUP`, `PLAYWRIGHT`, `SELENIUM` | |
| `capturedAt` | Instant | Momento da captura | Automático |

**Regras de negócio:**
- Snapshots são **imutáveis**; nunca atualizados após criação
- O `rawContent` é um JSON estruturado, nunca HTML bruto
- A comparação de mudança é sempre via `contentHash`, nunca comparação textual do `rawContent`
- Snapshots antigos podem ser arquivados/purgados por política de retenção (configurável)

---

### 2.11 `ProcessHistory`

Registro granular de cada movimentação detectada.

**Responsabilidade:** Histórico navegável pelo usuário.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `process` | Process | Processo relacionado | FK não nula |
| `snapshot` | ProcessSnapshot | Snapshot que gerou esta movimentação | FK não nula |
| `description` | String | Descrição da movimentação | Extraída e normalizada pelo parser |
| `movementDate` | LocalDate | Data da movimentação no tribunal | Pode diferir de `detectedAt` |
| `detectedAt` | Instant | Quando o sistema detectou | Automático |

**Regras de negócio:**
- Movimentações nunca são deletadas ou sobrescritas
- Uma movimentação pode ter sido publicada dias antes de ser detectada (problema de atraso em tribunais)
- `movementDate` é o dado do tribunal; `detectedAt` é o dado do sistema

---

### 2.12 `ParserVersion`

Controla o versionamento dos parsers por tribunal.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `court` | Court | Tribunal ao qual o parser pertence | FK não nula |
| `version` | String | Identificador semântico | Ex: `1.0.0`, `1.1.0` |
| `description` | String | O que mudou nessa versão | |
| `active` | Boolean | Parser atualmente em uso | Apenas um ativo por tribunal |
| `releasedAt` | Instant | Data de ativação | |
| `releasedBy` | String | Admin que ativou | |

---

### 2.13 `CrawlerExecution`

Log de cada tentativa de consulta a um tribunal.

**Responsabilidade:** Observabilidade e base para cálculo do health score.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `process` | Process | Processo consultado | FK não nula |
| `court` | Court | Tribunal consultado | FK não nula |
| `strategy` | String | Estratégia utilizada: `HTTP`, `JSOUP`, `PLAYWRIGHT`, `SELENIUM` | |
| `success` | Boolean | Se retornou resultado válido | |
| `durationMs` | Long | Duração em milissegundos | |
| `httpStatusCode` | Integer | Código HTTP retornado | Nulo se não aplicável |
| `errorType` | String | Tipo de erro: `TIMEOUT`, `PARSE_ERROR`, `BLOCKED`, `CAPTCHA`, etc. | Nulo se sucesso |
| `errorMessage` | String | Mensagem de erro truncada | Nulo se sucesso |
| `parserVersion` | ParserVersion | Versão do parser usada | FK, pode ser nula se falhou antes do parse |
| `executedAt` | Instant | Momento da execução | Automático |

---

### 2.14 `NotificationHistory`

Registro de cada notificação enviada.

| Campo | Tipo | Descrição | Regras |
|-------|------|-----------|--------|
| `id` | UUID | PK | |
| `user` | User | Destinatário | FK não nula |
| `process` | Process | Processo relacionado | FK, pode ser nulo (ex: notificação de erro de sistema) |
| `channel` | NotificationChannel | `EMAIL`, `PUSH`, `SMS`, `WEBHOOK` | |
| `eventType` | String | Ex: `MOVEMENT_DETECTED`, `CRAWL_ERROR`, `PLAN_LIMIT_REACHED` | |
| `status` | NotificationStatus | `SENT`, `FAILED`, `SKIPPED` | |
| `errorMessage` | String | Erro se falhou | Nulo se sucesso |
| `sentAt` | Instant | Quando foi enviado | Automático |

---

### 2.15 `CourtHealthScore`

Histórico de health scores por tribunal (série temporal).

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `id` | UUID | PK |
| `court` | Court | Tribunal avaliado |
| `score` | Integer | Score de 0 a 100 |
| `successRate` | Double | Taxa de sucesso (últimas 100 execuções) |
| `avgDurationMs` | Long | Tempo médio de resposta |
| `retryRate` | Double | Taxa de uso de fallback |
| `calculatedAt` | Instant | Quando foi calculado |

**Fórmula do score:**
```
score = (successRate * 60) + (speedScore * 25) + (stabilityScore * 15)

speedScore    = max(0, 100 - (avgDurationMs / 300))   // penaliza acima de 30s
stabilityScore = max(0, 100 - (retryRate * 100))       // penaliza por uso de fallback
```

---

## 3. Enumerações

### `UserStatus`
```java
PENDING_VERIFICATION  // cadastrado, aguardando verificação de e-mail
ACTIVE                // conta normal e operacional
SUSPENDED             // suspensa por inadimplência ou violação
DELETED               // excluída pelo usuário (dados anonimizados)
```

### `ProcessStatus`
```java
PENDING    // cadastrado, aguardando primeira consulta
OK         // última consulta bem-sucedida
ERROR      // falhou N vezes consecutivas
BLOCKED    // tribunal detectou acesso suspeito
```

### `CourtRequestStatus`
```java
PENDING      // aguardando análise do admin
IN_PROGRESS  // admin iniciou implementação
DONE         // tribunal disponível
REJECTED     // tribunal não será implementado (com justificativa)
```

### `NotificationChannel`
```java
EMAIL
PUSH
SMS         // futuro
WEBHOOK     // futuro
```

### `NotificationStatus`
```java
SENT
FAILED
SKIPPED   // usuário desabilitou o canal
```

---

## 4. Relacionamentos

```
User            ──N:1──▶  Plan
User            ──1:N──▶  RefreshToken
User            ──1:N──▶  PasswordReset
User            ──1:N──▶  ProcessSubscription
User            ──1:N──▶  NotificationHistory
User            ──1:N──▶  CourtRequest

Plan            ──1:N──▶  User

Court           ──1:N──▶  Process
Court           ──1:N──▶  CourtFeatureFlag
Court           ──1:N──▶  CourtHealthScore
Court           ──1:N──▶  ParserVersion
Court           ──1:N──▶  CrawlerExecution

Process         ──N:1──▶  Court
Process         ──1:N──▶  ProcessSubscription
Process         ──1:N──▶  ProcessSnapshot
Process         ──1:N──▶  ProcessHistory
Process         ──1:N──▶  CrawlerExecution

ProcessSubscription ──N:1──▶ User
ProcessSubscription ──N:1──▶ Process

ProcessSnapshot ──N:1──▶  Process
ProcessSnapshot ──N:1──▶  ParserVersion
ProcessSnapshot ──1:N──▶  ProcessHistory

ProcessHistory  ──N:1──▶  Process
ProcessHistory  ──N:1──▶  ProcessSnapshot

CrawlerExecution ──N:1──▶ Process
CrawlerExecution ──N:1──▶ Court
CrawlerExecution ──N:1──▶ ParserVersion (nullable)

ParserVersion   ──N:1──▶  Court

NotificationHistory ──N:1──▶ User
NotificationHistory ──N:1──▶ Process (nullable)
```

---

## 5. Invariantes do Domínio

| # | Invariante |
|---|-----------|
| I01 | Um processo é identificado unicamente por `(processNumber, court_id)` |
| I02 | Um usuário não pode ter duas subscriptions ativas para o mesmo processo |
| I03 | O número de subscriptions ativas de um usuário não pode exceder `plan.maxProcesses` (exceto quando `null`) |
| I04 | Um `ProcessSnapshot` nunca é alterado após sua criação |
| I05 | Um `ProcessHistory` nunca é alterado ou deletado após sua criação |
| I06 | Apenas um `ParserVersion` pode estar ativo por tribunal por vez |
| I07 | Um `User` com `status != ACTIVE` não pode criar novas subscriptions |
| I08 | Um `Court` com `active = false` não aceita novas subscriptions, mas mantém consultas das existentes |
| I09 | O `contentHash` de um `ProcessSnapshot` é sempre SHA-256 do `rawContent` serializado canonicamente |
| I10 | Nenhuma consulta real a tribunal deve ocorrer quando o perfil `dev` está ativo |
