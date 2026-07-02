# 07 — API REST

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Convenções Globais

### 1.1 Base URL

```
Produção:    https://api.consultorprocessos.com.br/v1
Staging:     https://api-staging.consultorprocessos.com.br/v1
Dev local:   http://localhost:8080/v1
```

### 1.2 Autenticação

Todos os endpoints — exceto os marcados com `🔓 público` — exigem o header:

```
Authorization: Bearer {JWT}
```

O JWT tem validade de **15 minutos**. Ao expirar, o cliente deve usar o Refresh Token para obter um novo par.

Endpoints marcados com `🔑 admin` exigem adicionalmente o role `ROLE_ADMIN` no JWT.

### 1.3 Formato de Requisições e Respostas

- Todas as requisições e respostas usam `Content-Type: application/json`
- Timestamps sempre em ISO 8601 UTC: `"2025-03-15T14:30:00Z"`
- Datas sem hora: `"2025-03-15"`
- UUIDs em formato padrão: `"550e8400-e29b-41d4-a716-446655440000"`
- Valores monetários como string decimal: `"29.90"`

### 1.4 Envelope de Resposta

**Sucesso:**
```json
{
  "success": true,
  "data": { ... },
  "meta": {              // presente apenas em respostas paginadas
    "page": 0,
    "pageSize": 20,
    "totalElements": 143,
    "totalPages": 8
  }
}
```

**Erro:**
```json
{
  "success": false,
  "error": {
    "code": "PROCESS_LIMIT_REACHED",
    "message": "Você atingiu o limite de 5 processos do plano Gratuito.",
    "details": []         // array de erros de validação, quando aplicável
  }
}
```

**Erro de validação (400):**
```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Um ou mais campos são inválidos.",
    "details": [
      { "field": "email",   "message": "Formato de e-mail inválido." },
      { "field": "password","message": "Senha deve ter no mínimo 8 caracteres." }
    ]
  }
}
```

### 1.5 Códigos de Erro de Negócio

| Código | Significado |
|--------|-------------|
| `VALIDATION_ERROR` | Um ou mais campos inválidos |
| `UNAUTHORIZED` | Token ausente, inválido ou expirado |
| `FORBIDDEN` | Token válido, mas sem permissão |
| `NOT_FOUND` | Recurso não encontrado |
| `EMAIL_ALREADY_EXISTS` | E-mail já cadastrado |
| `EMAIL_NOT_VERIFIED` | Conta não verificada |
| `ACCOUNT_LOCKED` | Conta bloqueada temporariamente |
| `ACCOUNT_SUSPENDED` | Conta suspensa |
| `INVALID_CREDENTIALS` | E-mail ou senha incorretos |
| `INVALID_TOKEN` | Token inválido ou expirado |
| `PROCESS_LIMIT_REACHED` | Limite de processos do plano atingido |
| `PROCESS_NUMBER_INVALID` | Número de processo em formato inválido |
| `COURT_NOT_AVAILABLE` | Tribunal não disponível; solicitação registrada |
| `SUBSCRIPTION_ALREADY_EXISTS` | Usuário já acompanha este processo |
| `RATE_LIMIT_EXCEEDED` | Muitas requisições em pouco tempo |
| `INTERNAL_ERROR` | Erro interno inesperado |

### 1.6 Paginação

Endpoints de listagem aceitam os parâmetros de query:

| Parâmetro | Padrão | Descrição |
|-----------|--------|-----------|
| `page` | `0` | Página (base 0) |
| `size` | `20` | Itens por página (máx: 100) |
| `sort` | varia | Campo e direção: `createdAt,desc` |

### 1.7 Rate Limiting

| Tipo de endpoint | Limite |
|-----------------|--------|
| Auth (login, register, reset) | 10 req/min por IP |
| API geral (autenticada) | 60 req/min por usuário |
| Admin | 120 req/min por usuário admin |

Header de resposta quando o limite é atingido (`429 Too Many Requests`):
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1710508200
```

---

## 2. Módulo AUTH

---

### `POST /auth/register` 🔓

Cria uma nova conta de usuário. Envia e-mail de verificação automaticamente.

**Request Body:**
```json
{
  "name":     "João Silva",
  "email":    "joao@email.com",
  "password": "MinhaS3nha!"
}
```

**Validações:**
| Campo | Regras |
|-------|--------|
| `name` | Obrigatório, 2–150 caracteres |
| `email` | Obrigatório, formato válido, único no sistema |
| `password` | Obrigatório, mínimo 8 caracteres, pelo menos 1 número |

**Resposta `201 Created`:**
```json
{
  "success": true,
  "data": {
    "id":        "550e8400-e29b-41d4-a716-446655440000",
    "name":      "João Silva",
    "email":     "joao@email.com",
    "status":    "PENDING_VERIFICATION",
    "createdAt": "2025-03-15T14:30:00Z"
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `400` | `VALIDATION_ERROR` | Campos inválidos |
| `409` | `EMAIL_ALREADY_EXISTS` | E-mail já cadastrado |

---

### `POST /auth/verify-email` 🔓

Verifica o e-mail do usuário a partir do token recebido por e-mail.

**Request Body:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR..."
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "E-mail verificado com sucesso. Você já pode fazer login."
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `400` | `INVALID_TOKEN` | Token inválido, expirado ou já usado |

---

### `POST /auth/login` 🔓

Autentica o usuário e retorna JWT + Refresh Token.

**Request Body:**
```json
{
  "email":    "joao@email.com",
  "password": "MinhaS3nha!"
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "accessToken":  "eyJhbGciOiJSUzI1NiIs...",
    "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2...",
    "expiresIn":    900,
    "tokenType":    "Bearer",
    "user": {
      "id":          "550e8400-e29b-41d4-a716-446655440000",
      "name":        "João Silva",
      "email":       "joao@email.com",
      "plan":        "GRATUITO",
      "planDisplay": "Plano Gratuito"
    }
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `400` | `VALIDATION_ERROR` | Campos ausentes |
| `401` | `INVALID_CREDENTIALS` | E-mail ou senha incorretos (mensagem genérica, sem indicar qual campo) |
| `401` | `EMAIL_NOT_VERIFIED` | Conta não verificada |
| `401` | `ACCOUNT_LOCKED` | Bloqueada por excesso de tentativas; inclui `lockedUntil` no erro |
| `401` | `ACCOUNT_SUSPENDED` | Conta suspensa |

---

### `POST /auth/refresh` 🔓

Gera novo par de tokens a partir do Refresh Token. O token antigo é revogado (rotation).

**Request Body:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2..."
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "accessToken":  "eyJhbGciOiJSUzI1NiIs...",
    "refreshToken": "bm93IHRoaXMgaXMgbmV3...",
    "expiresIn":    900,
    "tokenType":    "Bearer"
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `401` | `INVALID_TOKEN` | Token inválido, expirado ou já revogado |

---

### `POST /auth/logout` 🔒

Revoga o Refresh Token informado.

**Request Body:**
```json
{
  "refreshToken": "dGhpcyBpcyBhIHJlZnJlc2..."
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "Sessão encerrada com sucesso."
  }
}
```

> Mesmo que o token não exista ou já esteja revogado, a resposta é `200 OK` para não vazar informações.

---

### `POST /auth/forgot-password` 🔓

Inicia o fluxo de recuperação de senha. Envia e-mail com link de redefinição.

**Request Body:**
```json
{
  "email": "joao@email.com"
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "Se este e-mail estiver cadastrado, você receberá as instruções em breve."
  }
}
```

> A resposta é sempre idêntica, independente de o e-mail existir ou não (anti-enumeração).

---

### `POST /auth/reset-password` 🔓

Redefine a senha usando o token recebido por e-mail.

**Request Body:**
```json
{
  "token":       "abc123def456...",
  "newPassword": "NovaSenha@2025"
}
```

**Validações:**
| Campo | Regras |
|-------|--------|
| `token` | Obrigatório |
| `newPassword` | Obrigatório, mínimo 8 caracteres, pelo menos 1 número |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "Senha redefinida com sucesso."
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `400` | `INVALID_TOKEN` | Token inválido, expirado ou já usado |
| `400` | `VALIDATION_ERROR` | Nova senha inválida |

---

### `POST /auth/resend-verification` 🔓

Reenvia o e-mail de verificação.

**Request Body:**
```json
{
  "email": "joao@email.com"
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "Se este e-mail estiver cadastrado e não verificado, o link foi reenviado."
  }
}
```

---

## 3. Módulo USER

---

### `GET /users/me` 🔒

Retorna o perfil completo do usuário autenticado.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id":          "550e8400-e29b-41d4-a716-446655440000",
    "name":        "João Silva",
    "email":       "joao@email.com",
    "status":      "ACTIVE",
    "plan": {
      "name":                "GRATUITO",
      "displayName":         "Plano Gratuito",
      "maxProcesses":        5,
      "checkIntervalHours":  12
    },
    "usage": {
      "activeProcesses":     3,
      "remainingProcesses":  2
    },
    "notifications": {
      "emailEnabled": true,
      "pushEnabled":  false
    },
    "createdAt":   "2025-01-10T09:00:00Z",
    "lastLoginAt": "2025-03-15T14:30:00Z"
  }
}
```

---

### `PATCH /users/me` 🔒

Atualiza dados do perfil. Apenas os campos enviados são alterados.

**Request Body:**
```json
{
  "name":                "João M. Silva",
  "notifications": {
    "emailEnabled": true,
    "pushEnabled":  true
  }
}
```

**Validações:**
| Campo | Regras |
|-------|--------|
| `name` | Opcional, 2–150 caracteres se informado |
| `notifications.emailEnabled` | Opcional, boolean |
| `notifications.pushEnabled` | Opcional, boolean |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id":    "550e8400-e29b-41d4-a716-446655440000",
    "name":  "João M. Silva",
    "email": "joao@email.com",
    "notifications": {
      "emailEnabled": true,
      "pushEnabled":  true
    },
    "updatedAt": "2025-03-15T15:00:00Z"
  }
}
```

---

### `POST /users/me/change-password` 🔒

Altera a senha do usuário autenticado.

**Request Body:**
```json
{
  "currentPassword": "MinhaS3nha!",
  "newPassword":     "NovaSenha@2025"
}
```

**Validações:**
| Campo | Regras |
|-------|--------|
| `currentPassword` | Obrigatório |
| `newPassword` | Obrigatório, mínimo 8 caracteres, pelo menos 1 número, diferente da atual |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "Senha alterada com sucesso. Todas as outras sessões foram encerradas."
  }
}
```

> Ao alterar a senha, todos os Refresh Tokens do usuário são revogados, exceto o da sessão atual.

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `400` | `INVALID_CREDENTIALS` | Senha atual incorreta |
| `400` | `VALIDATION_ERROR` | Nova senha inválida |

---

### `DELETE /users/me` 🔒

Solicita exclusão da conta. Anonimiza dados pessoais e desativa todas as subscriptions.

**Request Body:**
```json
{
  "password":       "MinhaS3nha!",
  "confirmPhrase":  "DELETAR MINHA CONTA"
}
```

**Validações:**
| Campo | Regras |
|-------|--------|
| `password` | Obrigatório, deve ser a senha atual |
| `confirmPhrase` | Deve ser exatamente `"DELETAR MINHA CONTA"` |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "Conta excluída. Seus dados pessoais foram removidos."
  }
}
```

---

## 4. Módulo COURTS

---

### `GET /courts` 🔒

Lista todos os tribunais disponíveis.

**Query params opcionais:**
- `active=true` — filtra apenas ativos (padrão: retorna todos)

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":          "court-uuid-1",
      "name":        "Supremo Tribunal Federal",
      "code":        "STF",
      "active":      true,
      "healthScore": 98
    },
    {
      "id":          "court-uuid-2",
      "name":        "eProc - Processo Eletrônico",
      "code":        "EPROC",
      "active":      true,
      "healthScore": 91
    },
    {
      "id":          "court-uuid-3",
      "name":        "Superior Tribunal de Justiça - RJ",
      "code":        "STJRJ",
      "active":      true,
      "healthScore": 85
    }
  ]
}
```

---

### `GET /courts/{code}` 🔒

Retorna detalhes de um tribunal específico.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id":          "court-uuid-1",
    "name":        "Supremo Tribunal Federal",
    "code":        "STF",
    "active":      true,
    "healthScore": 98
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `404` | `NOT_FOUND` | Tribunal não encontrado |

---

## 5. Módulo PROCESS

---

### `POST /processes` 🔒

Cadastra um novo processo para acompanhamento.

**Request Body:**
```json
{
  "processNumber": "0001234-55.2020.8.26.0001",
  "courtCode":     "STF",
  "alias":         "Meu processo trabalhista"
}
```

**Validações:**
| Campo | Regras |
|-------|--------|
| `processNumber` | Obrigatório; aceita formatos variados e normaliza para CNJ |
| `courtCode` | Obrigatório; deve existir no cadastro de tribunais |
| `alias` | Opcional, máximo 200 caracteres |

**Fluxo interno:**
1. Valida e normaliza `processNumber` para formato CNJ
2. Verifica se `courtCode` existe e está ativo
3. Se tribunal não disponível → cria `CourtRequest` → retorna `202 Accepted` com aviso
4. Verifica se usuário atingiu limite de processos do plano
5. Verifica se já existe `Process` com mesmo `(processNumber, courtCode)` (deduplicação)
6. Cria `Process` (se novo) ou reutiliza existente
7. Cria `ProcessSubscription` vinculando usuário ao processo
8. Retorna `201 Created`

**Resposta `201 Created` — tribunal disponível:**
```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-uuid-1",
    "processId":      "proc-uuid-1",
    "processNumber":  "0001234-55.2020.8.26.0001",
    "alias":          "Meu processo trabalhista",
    "court": {
      "code": "STF",
      "name": "Supremo Tribunal Federal"
    },
    "status":    "PENDING",
    "active":    true,
    "createdAt": "2025-03-15T14:30:00Z"
  }
}
```

**Resposta `202 Accepted` — tribunal não disponível:**
```json
{
  "success": true,
  "data": {
    "message": "O tribunal informado ainda não está disponível. Registramos sua solicitação e nossa equipe iniciará a implementação em até 1 semana.",
    "courtRequestId": "req-uuid-1",
    "estimatedDays":  7
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `400` | `VALIDATION_ERROR` | Campos inválidos |
| `400` | `PROCESS_NUMBER_INVALID` | Número de processo não reconhecível |
| `409` | `SUBSCRIPTION_ALREADY_EXISTS` | Usuário já acompanha este processo |
| `422` | `PROCESS_LIMIT_REACHED` | Limite do plano atingido |

---

### `GET /processes` 🔒

Lista todos os processos acompanhados pelo usuário autenticado.

**Query params:**
| Param | Padrão | Descrição |
|-------|--------|-----------|
| `active` | (todos) | `true` ou `false` para filtrar por status da subscription |
| `status` | (todos) | `PENDING`, `OK`, `ERROR`, `BLOCKED` |
| `page` | `0` | Página |
| `size` | `20` | Itens por página |
| `sort` | `createdAt,desc` | Ordenação |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "subscriptionId":   "sub-uuid-1",
      "processId":        "proc-uuid-1",
      "processNumber":    "0001234-55.2020.8.26.0001",
      "alias":            "Meu processo trabalhista",
      "court": {
        "code": "STF",
        "name": "Supremo Tribunal Federal"
      },
      "status":           "OK",
      "active":           true,
      "lastCheckedAt":    "2025-03-15T12:00:00Z",
      "lastMovementAt":   "2025-03-10T09:00:00Z",
      "lastMovementDesc": "Conclusos para julgamento.",
      "createdAt":        "2025-01-20T10:00:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "pageSize": 20,
    "totalElements": 3,
    "totalPages": 1
  }
}
```

---

### `GET /processes/{subscriptionId}` 🔒

Retorna detalhes de um processo acompanhado pelo usuário.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "subscriptionId":  "sub-uuid-1",
    "processId":       "proc-uuid-1",
    "processNumber":   "0001234-55.2020.8.26.0001",
    "alias":           "Meu processo trabalhista",
    "court": {
      "code":        "STF",
      "name":        "Supremo Tribunal Federal",
      "healthScore": 98
    },
    "status":          "OK",
    "active":          true,
    "lastCheckedAt":   "2025-03-15T12:00:00Z",
    "lastMovementAt":  "2025-03-10T09:00:00Z",
    "consecutiveErrors": 0,
    "createdAt":       "2025-01-20T10:00:00Z"
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `404` | `NOT_FOUND` | Subscription não encontrada ou não pertence ao usuário |

---

### `PATCH /processes/{subscriptionId}` 🔒

Atualiza dados da subscription (alias). Apenas campos enviados são alterados.

**Request Body:**
```json
{
  "alias": "Processo cível - 2020"
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-uuid-1",
    "alias":          "Processo cível - 2020",
    "updatedAt":      "2025-03-15T15:10:00Z"
  }
}
```

---

### `POST /processes/{subscriptionId}/deactivate` 🔒

Pausa o acompanhamento de um processo (sem deletar histórico).

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-uuid-1",
    "active":         false,
    "deactivatedAt":  "2025-03-15T15:15:00Z",
    "message":        "Acompanhamento pausado. O histórico foi preservado."
  }
}
```

---

### `POST /processes/{subscriptionId}/reactivate` 🔒

Reativa o acompanhamento de um processo previamente pausado.

**Fluxo interno:** verifica limite do plano antes de reativar.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "subscriptionId": "sub-uuid-1",
    "active":         true,
    "message":        "Acompanhamento reativado com sucesso."
  }
}
```

**Erros possíveis:**
| Status | Código | Quando |
|--------|--------|--------|
| `422` | `PROCESS_LIMIT_REACHED` | Reativar excederia o limite do plano |

---

### `DELETE /processes/{subscriptionId}` 🔒

Remove permanentemente o acompanhamento. Libera a vaga no plano. O histórico é preservado se outros usuários acompanham o mesmo processo; caso contrário, o processo fica inativo.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "message": "Processo removido. Uma vaga foi liberada no seu plano."
  }
}
```

---

### `GET /processes/{subscriptionId}/history` 🔒

Lista o histórico de movimentações de um processo, paginado.

**Query params:**
| Param | Padrão | Descrição |
|-------|--------|-----------|
| `page` | `0` | Página |
| `size` | `20` | Itens por página |
| `sort` | `detectedAt,desc` | Ordenação |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":             "hist-uuid-1",
      "description":    "Conclusos para julgamento.",
      "movementDate":   "2025-03-10",
      "detectedAt":     "2025-03-10T09:05:00Z"
    },
    {
      "id":             "hist-uuid-2",
      "description":    "Petição inicial juntada aos autos.",
      "movementDate":   "2025-01-20",
      "detectedAt":     "2025-01-20T11:30:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "pageSize": 20,
    "totalElements": 2,
    "totalPages": 1
  }
}
```

---

## 6. Módulo NOTIFICATIONS

---

### `GET /notifications` 🔒

Lista o histórico de notificações enviadas ao usuário autenticado.

**Query params:**
| Param | Padrão | Descrição |
|-------|--------|-----------|
| `page` | `0` | Página |
| `size` | `20` | Itens por página |
| `channel` | (todos) | `EMAIL`, `PUSH` |
| `status` | (todos) | `SENT`, `FAILED`, `SKIPPED` |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":          "notif-uuid-1",
      "channel":     "EMAIL",
      "eventType":   "MOVEMENT_DETECTED",
      "status":      "SENT",
      "process": {
        "subscriptionId": "sub-uuid-1",
        "processNumber":  "0001234-55.2020.8.26.0001",
        "alias":          "Meu processo trabalhista"
      },
      "sentAt":      "2025-03-10T09:06:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

## 7. Módulo ADMIN

> Todos os endpoints desta seção exigem `ROLE_ADMIN`. 🔑

---

### `GET /admin/courts` 🔑

Lista todos os tribunais com detalhes completos (incluindo inativos).

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":              "court-uuid-1",
      "name":            "Supremo Tribunal Federal",
      "code":            "STF",
      "providerClass":   "STFProvider",
      "active":          true,
      "healthScore":     98,
      "rateLimitPerMin": 5,
      "minDelayMs":      2000,
      "maxDelayMs":      5000,
      "featureFlags": {
        "PLAYWRIGHT_ENABLED":  false,
        "SELENIUM_ENABLED":    false,
        "EXTRA_RETRY_ENABLED": true
      },
      "activeParserVersion": "1.0.0",
      "updatedAt": "2025-03-01T00:00:00Z"
    }
  ]
}
```

---

### `POST /admin/courts` 🔑

Cadastra um novo tribunal.

**Request Body:**
```json
{
  "name":            "Tribunal Regional Federal da 2ª Região",
  "code":            "TRF2",
  "providerClass":   "TRF2Provider",
  "active":          false,
  "rateLimitPerMin": 5,
  "minDelayMs":      2000,
  "maxDelayMs":      5000
}
```

**Validações:**
| Campo | Regras |
|-------|--------|
| `name` | Obrigatório, 3–200 chars |
| `code` | Obrigatório, único, uppercase, 2–20 chars |
| `providerClass` | Obrigatório; deve ser um bean Spring registrado |
| `active` | Padrão `false` para novos tribunais |

**Resposta `201 Created`:**
```json
{
  "success": true,
  "data": {
    "id":   "court-uuid-new",
    "code": "TRF2",
    "name": "Tribunal Regional Federal da 2ª Região"
  }
}
```

---

### `PATCH /admin/courts/{code}` 🔑

Atualiza configurações de um tribunal.

**Request Body (todos opcionais):**
```json
{
  "active":          true,
  "rateLimitPerMin": 8,
  "minDelayMs":      1500,
  "maxDelayMs":      4000
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "code":      "STF",
    "active":    true,
    "updatedAt": "2025-03-15T16:00:00Z"
  }
}
```

---

### `PUT /admin/courts/{code}/feature-flags` 🔑

Atualiza feature flags de um tribunal.

**Request Body:**
```json
{
  "PLAYWRIGHT_ENABLED":  true,
  "SELENIUM_ENABLED":    false,
  "EXTRA_RETRY_ENABLED": true
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "courtCode":    "STF",
    "featureFlags": {
      "PLAYWRIGHT_ENABLED":  true,
      "SELENIUM_ENABLED":    false,
      "EXTRA_RETRY_ENABLED": true
    },
    "updatedAt": "2025-03-15T16:05:00Z"
  }
}
```

---

### `GET /admin/courts/{code}/health` 🔑

Retorna o histórico de health scores de um tribunal.

**Query params:**
| Param | Padrão | Descrição |
|-------|--------|-----------|
| `from` | `now - 7 dias` | Data de início |
| `to` | `now` | Data de fim |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "courtCode":    "STF",
    "currentScore": 98,
    "history": [
      {
        "score":        98,
        "successRate":  0.99,
        "avgDurationMs": 1850,
        "retryRate":    0.01,
        "calculatedAt": "2025-03-15T12:00:00Z"
      }
    ]
  }
}
```

---

### `GET /admin/courts/{code}/parser-versions` 🔑

Lista as versões de parser de um tribunal.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":          "pv-uuid-1",
      "version":     "1.1.0",
      "description": "Ajuste no seletor de movimentações após atualização do portal.",
      "active":      true,
      "releasedAt":  "2025-02-20T10:00:00Z",
      "releasedBy":  "admin@consultorprocessos.com.br"
    },
    {
      "id":          "pv-uuid-0",
      "version":     "1.0.0",
      "description": "Parser inicial.",
      "active":      false,
      "releasedAt":  "2025-01-01T00:00:00Z",
      "releasedBy":  "admin@consultorprocessos.com.br"
    }
  ]
}
```

---

### `POST /admin/courts/{code}/parser-versions` 🔑

Registra e ativa uma nova versão de parser.

**Request Body:**
```json
{
  "version":     "1.2.0",
  "description": "Suporte ao novo layout do portal após atualização de março/2025."
}
```

**Resposta `201 Created`:**
```json
{
  "success": true,
  "data": {
    "id":         "pv-uuid-2",
    "version":    "1.2.0",
    "active":     true,
    "releasedAt": "2025-03-15T16:10:00Z"
  }
}
```

> Ao criar uma nova versão, a anterior é automaticamente marcada como `active = false`.

---

### `GET /admin/court-requests` 🔑

Lista as solicitações de novos tribunais.

**Query params:**
| Param | Padrão | Descrição |
|-------|--------|-----------|
| `status` | `PENDING` | `PENDING`, `IN_PROGRESS`, `DONE`, `REJECTED` |
| `page` | `0` | Página |
| `size` | `20` | Itens por página |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "courtName":    "TRF da 2ª Região",
      "courtCode":    "TRF2",
      "requestCount": 14,
      "status":       "PENDING",
      "lastRequestAt": "2025-03-14T20:00:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "pageSize": 20,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

---

### `PATCH /admin/court-requests/{id}` 🔑

Atualiza o status de uma solicitação de tribunal.

**Request Body:**
```json
{
  "status":     "IN_PROGRESS",
  "adminNotes": "Iniciando implementação do parser. Previsão: 1 semana."
}
```

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "id":         "req-uuid-1",
    "status":     "IN_PROGRESS",
    "adminNotes": "Iniciando implementação do parser. Previsão: 1 semana.",
    "updatedAt":  "2025-03-15T16:15:00Z"
  }
}
```

---

### `GET /admin/metrics` 🔑

Retorna métricas gerais do sistema.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": {
    "users": {
      "total":   1250,
      "active":  1100,
      "byPlan": {
        "GRATUITO": 900,
        "BASICO":   250,
        "AVANCADO": 100
      }
    },
    "processes": {
      "total":          4800,
      "activeSubscriptions": 5200,
      "byStatus": {
        "PENDING": 50,
        "OK":      4600,
        "ERROR":   130,
        "BLOCKED": 20
      }
    },
    "crawlerLast24h": {
      "totalExecutions": 28400,
      "successRate":     0.961,
      "avgDurationMs":   2100,
      "byStrategy": {
        "HTTP":       22000,
        "JSOUP":      4800,
        "PLAYWRIGHT": 1500,
        "SELENIUM":   100
      }
    },
    "queues": {
      "crawlRequests":     42,
      "crawlRetry":        8,
      "crawlDlq":          3,
      "notifications":     0
    }
  }
}
```

---

### `GET /admin/crawler-executions` 🔑

Lista execuções de crawling com filtros.

**Query params:**
| Param | Padrão | Descrição |
|-------|--------|-----------|
| `courtCode` | (todos) | Filtrar por tribunal |
| `success` | (todos) | `true` ou `false` |
| `strategy` | (todos) | `HTTP`, `JSOUP`, `PLAYWRIGHT`, `SELENIUM` |
| `from` | `now - 1h` | Data de início |
| `to` | `now` | Data de fim |
| `page` | `0` | Página |
| `size` | `50` | Itens por página |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":             "exec-uuid-1",
      "courtCode":      "STF",
      "processNumber":  "0001234-55.2020.8.26.0001",
      "strategy":       "HTTP",
      "success":        true,
      "durationMs":     1820,
      "parserVersion":  "1.1.0",
      "executedAt":     "2025-03-15T12:00:00Z"
    },
    {
      "id":            "exec-uuid-2",
      "courtCode":     "EPROC",
      "processNumber": "5001234-88.2021.4.02.5001",
      "strategy":      "PLAYWRIGHT",
      "success":       false,
      "durationMs":    30001,
      "errorType":     "TIMEOUT",
      "errorMessage":  "Timeout após 30 segundos.",
      "executedAt":    "2025-03-15T12:01:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "pageSize": 50,
    "totalElements": 28400,
    "totalPages": 569
  }
}
```

---

### `POST /admin/processes/{processId}/reprocess` 🔑

Força o reprocessamento imediato de um processo específico, ignorando a janela de consulta.

**Resposta `202 Accepted`:**
```json
{
  "success": true,
  "data": {
    "message":   "Processo enfileirado para reprocessamento imediato.",
    "processId": "proc-uuid-1"
  }
}
```

---

### `GET /admin/dlq` 🔑

Lista mensagens na Dead Letter Queue.

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "messageId":     "msg-uuid-1",
      "processId":     "proc-uuid-1",
      "courtCode":     "EPROC",
      "processNumber": "5001234-88.2021.4.02.5001",
      "failureReason": "TIMEOUT após 4 tentativas.",
      "enqueuedAt":    "2025-03-15T08:00:00Z",
      "retryCount":    4
    }
  ]
}
```

---

### `POST /admin/dlq/{messageId}/reprocess` 🔑

Reenfileira uma mensagem da DLQ para reprocessamento.

**Resposta `202 Accepted`:**
```json
{
  "success": true,
  "data": {
    "message": "Mensagem reenfileirada para reprocessamento."
  }
}
```

---

### `GET /admin/users` 🔑

Lista usuários com filtros.

**Query params:**
| Param | Padrão | Descrição |
|-------|--------|-----------|
| `status` | (todos) | `ACTIVE`, `PENDING_VERIFICATION`, `SUSPENDED`, `DELETED` |
| `plan` | (todos) | `GRATUITO`, `BASICO`, `AVANCADO` |
| `search` | — | Busca parcial por nome ou e-mail |
| `page` | `0` | Página |
| `size` | `20` | Itens por página |

**Resposta `200 OK`:**
```json
{
  "success": true,
  "data": [
    {
      "id":          "user-uuid-1",
      "name":        "João Silva",
      "email":       "jo***@email.com",
      "plan":        "GRATUITO",
      "status":      "ACTIVE",
      "processCount": 3,
      "createdAt":   "2025-01-10T09:00:00Z",
      "lastLoginAt": "2025-03-15T14:30:00Z"
    }
  ],
  "meta": {
    "page": 0,
    "pageSize": 20,
    "totalElements": 1250,
    "totalPages": 63
  }
}
```

> E-mails são parcialmente mascarados mesmo para admins, salvo endpoint específico com log de acesso.

---

## 8. Endpoints de Saúde e Diagnóstico

---

### `GET /health` 🔓

Health check básico. Usado pelo Docker e load balancer.

**Resposta `200 OK`:**
```json
{
  "status": "UP",
  "timestamp": "2025-03-15T14:30:00Z"
}
```

**Resposta `503 Service Unavailable`** (quando alguma dependência está down):
```json
{
  "status": "DOWN",
  "components": {
    "database": "DOWN",
    "redis":    "UP",
    "rabbitmq": "UP"
  }
}
```

---

### `GET /health/detailed` 🔑

Health check detalhado com métricas de dependências. Apenas para admins.

**Resposta `200 OK`:**
```json
{
  "status": "UP",
  "components": {
    "database": {
      "status":        "UP",
      "responseMs":    5,
      "poolSize":      10,
      "activeConnections": 2
    },
    "redis": {
      "status":     "UP",
      "responseMs": 1
    },
    "rabbitmq": {
      "status":     "UP",
      "responseMs": 3,
      "queues": {
        "crawl.requests": 42,
        "crawl.dlq":       3
      }
    }
  }
}
```

---

## 9. Segurança dos Endpoints — Resumo

| Endpoint | Método | Auth | Role |
|----------|--------|------|------|
| `/auth/**` | * | 🔓 público | — |
| `/courts` | GET | 🔒 JWT | USER |
| `/courts/{code}` | GET | 🔒 JWT | USER |
| `/processes` | GET, POST | 🔒 JWT | USER |
| `/processes/{id}` | GET, PATCH, DELETE | 🔒 JWT | USER (próprio) |
| `/processes/{id}/deactivate` | POST | 🔒 JWT | USER (próprio) |
| `/processes/{id}/reactivate` | POST | 🔒 JWT | USER (próprio) |
| `/processes/{id}/history` | GET | 🔒 JWT | USER (próprio) |
| `/users/me` | GET, PATCH, DELETE | 🔒 JWT | USER |
| `/users/me/change-password` | POST | 🔒 JWT | USER |
| `/notifications` | GET | 🔒 JWT | USER |
| `/admin/**` | * | 🔑 JWT | ADMIN |
| `/health` | GET | 🔓 público | — |
| `/health/detailed` | GET | 🔑 JWT | ADMIN |

---

## 10. Versionamento da API

- Versão atual: **v1** (prefixo na URL: `/v1/`)
- Versões antigas são mantidas por mínimo de 6 meses após a versão nova ser estabilizada
- Breaking changes sempre resultam em nova versão
- Non-breaking changes (novos campos opcionais, novos endpoints) não geram nova versão
- Header `Deprecation` é adicionado em endpoints próximos de serem removidos

```
Deprecation: true
Sunset: Sat, 01 Jan 2026 00:00:00 GMT
Link: <https://api.consultorprocessos.com.br/v2/processes>; rel="successor-version"
```
