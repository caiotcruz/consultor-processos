# 15 — Roadmap de Implementação

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Princípios do Roadmap

- **Nenhuma fase começa sem a anterior estar estável.** Funcionalidade incompleta não avança.
- **A arquitetura não muda entre fases.** O SDD foi feito para isso: toda decisão já foi tomada.
- **Testes acompanham o código.** Cada componente implementado já sai com testes.
- **O Mock Tribunal é configurado antes de qualquer Provider real.** Nenhum tribuna real é tocado até o ambiente de DEV estar controlado.
- **Pagamento é a última coisa.** Toda a plataforma deve estar funcionando antes de cobrar.

---

## 2. Visão Macro das Fases

```
Fase 1  → Infraestrutura base
Fase 2  → Autenticação
Fase 3  → Usuários e Planos
Fase 4  → Cadastro de Processos
Fase 5  → Arquitetura de Crawlers (sem tribunal real)
Fase 6  → Implementação dos 3 tribunais iniciais
Fase 7  → Scheduler, Filas e Monitoramento
Fase 8  → Notificações
Fase 9  → Painel Administrativo
Fase 10 → Aplicativo Mobile (Ionic)
Fase 11 → Pagamentos
```

---

## 3. Fase 1 — Infraestrutura Base

**Objetivo:** ambiente local funcional, banco migrado, comunicação entre serviços confirmada.

**Entregáveis:**

| # | Tarefa | Notas |
|---|--------|-------|
| 1.1 | Criar estrutura de pastas do projeto (backend + docs) | Conforme `04-Arquitetura.md` seção 4 |
| 1.2 | Configurar `pom.xml` com todas as dependências | Spring Boot, Flyway, Jooq/JPA, Redis, RabbitMQ, Jsoup, Testcontainers |
| 1.3 | Criar `docker-compose.dev.yml` | PostgreSQL, Redis, RabbitMQ, Mock Tribunal |
| 1.4 | Criar perfis Spring: `dev`, `test`, `prod` | `application-{profile}.yml` para cada |
| 1.5 | Escrever e executar migrações Flyway V001–V015 | Todas as tabelas do `06-BancoDeDados.md` |
| 1.6 | Seed de dados iniciais: planos e tribunais (V100) | `GRATUITO`, `BASICO`, `AVANCADO`; `STF`, `EPROC`, `STJRJ` |
| 1.7 | Configurar `RabbitConfig` com exchanges, filas e bindings | Conforme `10-Scheduler.md` seção 5 |
| 1.8 | Configurar `RedisConfig` e `RedisLockService` | |
| 1.9 | Endpoint `GET /v1/health` funcional | Verifica banco, Redis e RabbitMQ |
| 1.10 | Configurar `BaseIntegrationTest` com Testcontainers | Reutilizado em todas as fases |

**Critério de conclusão:** `docker compose -f docker-compose.dev.yml up -d` sobe sem erros. `GET /v1/health` retorna `UP` para todas as dependências. Migrations Flyway aplicadas com sucesso.

---

## 4. Fase 2 — Autenticação

**Objetivo:** fluxo completo de identidade funcionando e testado.

**Entregáveis:**

| # | Tarefa | Notas |
|---|--------|-------|
| 2.1 | Entidade `User`, `RefreshToken`, `PasswordReset` e repositórios | |
| 2.2 | `JwtService`: geração e validação de JWT com RS256 | Chaves configuradas via variável de ambiente |
| 2.3 | `UserDetailsServiceImpl` para integração com Spring Security | |
| 2.4 | `JwtAuthenticationFilter` | Intercepta requisições e valida token |
| 2.5 | `POST /auth/register` | Inclui envio de e-mail de verificação (mockado em DEV) |
| 2.6 | `POST /auth/verify-email` | |
| 2.7 | `POST /auth/login` | Com controle de tentativas falhas e bloqueio |
| 2.8 | `POST /auth/refresh` | Com rotation de Refresh Token |
| 2.9 | `POST /auth/logout` | Revogação do Refresh Token |
| 2.10 | `POST /auth/forgot-password` | Anti-enumeração |
| 2.11 | `POST /auth/reset-password` | Token de uso único com expiração |
| 2.12 | `POST /auth/resend-verification` | |
| 2.13 | Dev mode: bypass de autenticação com usuário fixo | `@Profile("dev")` + `DevModeFilter` |
| 2.14 | Testes unitários: `JwtServiceTest`, `PasswordHashServiceTest` | |
| 2.15 | Testes de integração: `AuthControllerIT` (todos os endpoints) | |

**Critério de conclusão:** fluxo completo de registro → verificação → login → refresh → logout testado e funcionando. Dev mode bypassa autenticação sem quebrar testes.

---

## 5. Fase 3 — Usuários e Planos

**Objetivo:** perfil do usuário, planos e limites funcionando.

**Entregáveis:**

| # | Tarefa | Notas |
|---|--------|-------|
| 3.1 | Entidade `Plan` e repositório | |
| 3.2 | `GET /users/me` | Inclui `usage` com processos ativos e restantes |
| 3.3 | `PATCH /users/me` | Nome e preferências de notificação |
| 3.4 | `POST /users/me/change-password` | Revoga outros Refresh Tokens |
| 3.5 | `DELETE /users/me` | Anonimização de dados pessoais |
| 3.6 | `UserNotificationPreferences` como `@Embeddable` em `User` | |
| 3.7 | `PlanService` com método `hasCapacity(user)` | Reutilizado nas fases seguintes |
| 3.8 | Testes de integração: `UserControllerIT` | |

**Critério de conclusão:** usuário consegue ver e editar perfil. Limites do plano são exibidos corretamente.

---

## 6. Fase 4 — Cadastro de Processos

**Objetivo:** fluxo principal do usuário — cadastrar processo e visualizar histórico.

**Entregáveis:**

| # | Tarefa | Notas |
|---|--------|-------|
| 4.1 | `ProcessNumberNormalizer` com testes | Conforme `09-Crawlers.md` seção 14 |
| 4.2 | Entidades `Court`, `Process`, `ProcessSubscription`, `ProcessHistory`, `CourtRequest` | |
| 4.3 | `POST /processes` — caminho feliz (tribunal disponível) | Com deduplicação e verificação de limite |
| 4.4 | `POST /processes` — tribunal indisponível | Cria `CourtRequest`, resposta `202` |
| 4.5 | `GET /processes` | Listagem paginada com filtros |
| 4.6 | `GET /processes/{id}` | |
| 4.7 | `PATCH /processes/{id}` | Alias |
| 4.8 | `POST /processes/{id}/deactivate` | |
| 4.9 | `POST /processes/{id}/reactivate` | Verifica limite do plano |
| 4.10 | `DELETE /processes/{id}` | |
| 4.11 | `GET /processes/{id}/history` | Paginado |
| 4.12 | `GET /courts` e `GET /courts/{code}` | |
| 4.13 | Testes: `ProcessServiceIT`, deduplicação, limite de plano | |
| 4.14 | Testes: `ProcessControllerIT` para todos os endpoints | |

**Critério de conclusão:** usuário consegue cadastrar, listar, pausar, reativar e deletar processos. Deduplicação verificada com dois usuários cadastrando o mesmo processo.

---

## 7. Fase 5 — Arquitetura de Crawlers (Sem Tribunal Real)

**Objetivo:** toda a infraestrutura de crawling funcionando contra o Mock Tribunal. Nenhum tribunal real é tocado nesta fase.

**Entregáveis:**

| # | Tarefa | Notas |
|---|--------|-------|
| 5.1 | Servidor Mock Tribunal (porta 9000) | Todos os endpoints de controle (`/control/*`) |
| 5.2 | Mocks HTML para STF, eProc e STJRJ (fixtures iniciais) | Salvos em `src/test/resources/fixtures/parsers/` |
| 5.3 | Interface `CourtProvider` e `CourtProviderFactory` | Auto-descoberta via Spring DI |
| 5.4 | Modelos: `RawResponse`, `ParsedData`, `ProcessSnapshot`, `Movement` | |
| 5.5 | `MockCourtProvider` com `@Profile("dev") @Primary` | Aponta para localhost:9000 |
| 5.6 | `HttpCrawler` e `JsoupCrawler` | |
| 5.7 | `BlockDetector`, `HashGenerator`, `ProcessNumberNormalizer` | |
| 5.8 | `SnapshotComparator` com persistência de `ProcessSnapshot` e `ProcessHistory` | |
| 5.9 | Entidades `ParserVersion` e `CrawlerExecution` + repositórios | |
| 5.10 | `CrawlerExecutionRecorder` | |
| 5.11 | `HealthScoreService` — cálculo do score por tribunal | |
| 5.12 | `FeatureFlagService` com cache Redis | |
| 5.13 | `RateLimiter` e `DelayStrategy` por tribunal | |
| 5.14 | `UserAgentRotator` | |
| 5.15 | `SessionManager` (Redis) | |
| 5.16 | Testes: `HashGeneratorTest`, `SnapshotComparatorTest`, `BlockDetectorTest` | |
| 5.17 | Testes: `MockTribunalServerTest` — todos os cenários de controle | |

**Critério de conclusão:** `MockCourtProvider.consultar()` retorna `ProcessSnapshot` válido. `SnapshotComparator` detecta mudanças injetadas via `/control/inject-change`. `CrawlerExecution` salvo no banco após cada consulta.

---

## 8. Fase 6 — Implementação dos 3 Tribunais

**Objetivo:** três providers reais funcionando em produção.

Para cada tribunal, seguir o checklist de `09-Crawlers.md` seção 18.

### STF

| # | Tarefa |
|---|--------|
| 6.1 | Inspecionar portal manualmente e mapear URL + seletores |
| 6.2 | Salvar HTML de exemplo como fixture `v1.0.0_processo_normal.html` |
| 6.3 | `STFParser` com testes baseados nas fixtures |
| 6.4 | `STFHttpCrawler` (estratégia primária) |
| 6.5 | `STFProvider` implementando `CourtProvider` |
| 6.6 | Adicionar cenário STF ao Mock Tribunal |
| 6.7 | Migration: ativar `STF` no banco (`active = true`) |
| 6.8 | Teste de integração com WireMock: sucesso, 403, timeout, fallback |
| 6.9 | Monitorar health score nas primeiras 48h em produção |

### eProc

| # | Tarefa |
|---|--------|
| 6.10 | Inspecionar portal e mapear estratégia (HTTP ou Jsoup) |
| 6.11 | Fixture + `EprocParser` com testes |
| 6.12 | `EprocCrawler` (HTTP ou Jsoup conforme necessidade) |
| 6.13 | `EprocProvider` |
| 6.14 | Mock + migration + testes WireMock |

### STJRJ

| # | Tarefa |
|---|--------|
| 6.15 | Inspecionar portal e mapear estratégia |
| 6.16 | Fixture + `STJRJParser` com testes |
| 6.17 | `STJRJCrawler` + `STJRJProvider` |
| 6.18 | Mock + migration + testes WireMock |

**Critério de conclusão:** os três providers consultam seus tribunais reais com taxa de sucesso > 90% nas primeiras 48h. Health scores estáveis acima de 80.

---

## 9. Fase 7 — Scheduler, Filas e Monitoramento

**Objetivo:** pipeline completo de monitoramento automático funcionando de ponta a ponta.

| # | Tarefa | Notas |
|---|--------|-------|
| 7.1 | `MonitoringService.findPendingProcesses()` com query correta | Conforme `06-BancoDeDados.md` seção 7.1 |
| 7.2 | `QueuePublisher` com lock Redis por processo | |
| 7.3 | `SchedulerService` com cron e lock global | |
| 7.4 | `CrawlerMessageConsumer` com `@RabbitListener` | Inclui `basicAck`/`basicNack` manual |
| 7.5 | Política de retry com backoff exponencial e jitter | |
| 7.6 | `RetryPublisher` com per-message TTL | |
| 7.7 | Dead Letter Queue: configuração e persistência | |
| 7.8 | Atualização de `Process.status` para `ERROR` após esgotar retries | |
| 7.9 | Testes: `SchedulerIT` — enfileiramento e deduplicação | |
| 7.10 | Testes: consumer com retry, DLQ, falha inesperada | |
| 7.11 | Métricas Micrometer: scheduler + queue + crawler | Conforme `10-Scheduler.md` seção 12 |

**Critério de conclusão:** processo cadastrado é automaticamente consultado no intervalo do plano. Mudança injetada via Mock Tribunal é detectada e salva no `ProcessHistory`. Retry funciona com delays corretos. DLQ recebe mensagens após 3 falhas.

---

## 10. Fase 8 — Notificações

**Objetivo:** usuário recebe e-mail e push ao detectar movimentação.

| # | Tarefa | Notas |
|---|--------|-------|
| 8.1 | Interface `NotificationChannel` e `NotificationRequest` | |
| 8.2 | `NotificationRequestFactory` | |
| 8.3 | `TemplateRenderer` com Thymeleaf | |
| 8.4 | Templates de e-mail: `movement-detected`, `crawl-error`, `plan-limit-reached` | Conforme `11-Notifications.md` seção 9 |
| 8.5 | `EmailNotificationChannel` com JavaMailSender | |
| 8.6 | `EmailRateLimiter` (Redis, 10 e-mails/hora por usuário) | |
| 8.7 | Tabela `user_device_tokens` + migration | |
| 8.8 | `PushNotificationChannel` com Firebase FCM | |
| 8.9 | Endpoints `POST /devices/register` e `DELETE /devices/{id}` | |
| 8.10 | `NotificationService` consumindo fila `notifications` | |
| 8.11 | Templates admin: `court-request`, `health-score-low` | |
| 8.12 | `AdminNotificationService` | |
| 8.13 | `GET /notifications` (histórico do usuário) | |
| 8.14 | Endpoint de unsubscribe via link no e-mail | |
| 8.15 | `LogOnlyNotificationChannel` com `@Profile("dev") @Primary` | |
| 8.16 | Testes: `NotificationServiceTest` — canais, falha isolada, histórico | |

**Critério de conclusão:** ao injetar mudança via Mock Tribunal, o usuário recebe e-mail (verificado em ferramenta como Mailtrap) e push (verificado via FCM test console). Falha no e-mail não bloqueia push.

---

## 11. Fase 9 — Painel Administrativo

**Objetivo:** operação e observabilidade completas via API admin.

| # | Tarefa | Notas |
|---|--------|-------|
| 9.1 | Tabelas `user_roles`, `audit_log`, `system_logs` + migrations | |
| 9.2 | `AuditLogService` e aspecto `@Audited` | |
| 9.3 | `AdminService` — delegação para outros módulos | |
| 9.4 | `GET /admin/dashboard` | |
| 9.5 | Gestão de tribunais: CRUD, feature flags, parser versions, health | |
| 9.6 | Gestão de processos: reprocessamento, override de status, busca | |
| 9.7 | Gestão de DLQ: listagem, reprocessar individual, reprocessar bulk, descartar | |
| 9.8 | Gestão de court requests: listagem e atualização de status | |
| 9.9 | Gestão de usuários: listagem, detalhe, suspender, reativar, trocar plano | |
| 9.10 | `GET /admin/crawler-executions` com filtros e estatísticas | |
| 9.11 | `GET /admin/logs` com filtros | |
| 9.12 | `GET /admin/audit-log` | |
| 9.13 | `GET /v1/health/detailed` | |
| 9.14 | Testes de segurança: `SecurityIT` — RBAC admin vs usuário comum | |

**Critério de conclusão:** admin consegue visualizar todo o estado do sistema, reprocessar falhas, gerenciar tribunais e usuários via API.

---

## 12. Fase 10 — Aplicativo Mobile (Ionic)

**Objetivo:** aplicativo iOS + Android com as funcionalidades principais.

| # | Tarefa | Notas |
|---|--------|-------|
| 10.1 | Setup do projeto Angular + Ionic + Capacitor | |
| 10.2 | Configuração de ambientes (dev/prod) no Angular | |
| 10.3 | Interceptor HTTP para JWT e refresh automático | |
| 10.4 | Telas de auth: login, registro, recuperação de senha | |
| 10.5 | Tela principal: lista de processos com status | |
| 10.6 | Tela de detalhe do processo: histórico de movimentações | |
| 10.7 | Tela de cadastro de processo: número + tribunal | |
| 10.8 | Tela de perfil: plano, limites, preferências de notificação | |
| 10.9 | Integração com FCM para push notifications (Capacitor Push Notifications) | |
| 10.10 | Registro de device token via `POST /devices/register` no login | |
| 10.11 | Build nativo iOS + Android via Capacitor | |
| 10.12 | Testes manuais em dispositivo físico (iOS + Android) | |

**Critério de conclusão:** usuário consegue se registrar, cadastrar processos e receber notificações push no dispositivo físico.

---

## 13. Fase 11 — Pagamentos

**Objetivo:** monetização da plataforma com Stripe e/ou Mercado Pago.

> Esta fase só começa quando todas as anteriores estiverem estáveis em produção com usuários reais.

| # | Tarefa | Notas |
|---|--------|-------|
| 11.1 | Tabelas `payment_subscriptions`, `invoices` + migrations | |
| 11.2 | Integração com Stripe (checkout, webhooks) | |
| 11.3 | Integração com Mercado Pago (checkout, webhooks) | Alternativa para usuários BR |
| 11.4 | Webhook handler: atualiza plano ao confirmar pagamento | |
| 11.5 | Webhook handler: suspende acesso ao falhar cobrança | |
| 11.6 | Downgrade de plano: aguarda fim do período pago | |
| 11.7 | E-mails transacionais: confirmação de pagamento, falha, cancelamento | |
| 11.8 | Telas de planos e checkout no app | |
| 11.9 | Tela de histórico de faturas | |
| 11.10 | Testes: webhooks com payloads simulados do Stripe/MP | |

**Critério de conclusão:** usuário consegue fazer upgrade de plano, é cobrado corretamente e seu limite é atualizado imediatamente.

---

## 14. Linha do Tempo Estimada

> Estimativas para um desenvolvedor trabalhando em tempo integral, com o SDD como guia.

| Fase | Duração estimada | Acumulado |
|------|-----------------|-----------|
| Fase 1 — Infraestrutura | 2–3 dias | 3 dias |
| Fase 2 — Auth | 3–4 dias | 1 semana |
| Fase 3 — Usuários e Planos | 2–3 dias | 10 dias |
| Fase 4 — Processos | 3–4 dias | 2 semanas |
| Fase 5 — Arquitetura Crawlers | 4–5 dias | 3 semanas |
| Fase 6 — 3 Tribunais | 5–7 dias | 4–5 semanas |
| Fase 7 — Scheduler + Filas | 3–4 dias | 5–6 semanas |
| Fase 8 — Notificações | 3–4 dias | 7 semanas |
| Fase 9 — Admin | 3–4 dias | 8 semanas |
| Fase 10 — Mobile | 7–10 dias | 10–11 semanas |
| Fase 11 — Pagamentos | 5–7 dias | 12–13 semanas |

> Com o SDD completo, cada fase começa sem ambiguidades. O maior ganho do document-first é que não há retrabalho arquitetural entre fases.

---

## 15. Critérios para Considerar o MVP Completo

O MVP está completo quando:

- [ ] Usuário consegue se registrar e verificar e-mail
- [ ] Usuário consegue cadastrar processos nos 3 tribunais iniciais
- [ ] Sistema monitora automaticamente e detecta movimentações
- [ ] Usuário recebe notificação por e-mail ao detectar mudança
- [ ] Scheduler respeita os intervalos dos planos
- [ ] Admin consegue visualizar saúde do sistema e reprocessar falhas
- [ ] App mobile funciona em iOS e Android
- [ ] Taxa de sucesso de crawling > 90% nos 3 tribunais
- [ ] Sistema em produção há ao menos 7 dias sem incidentes críticos
