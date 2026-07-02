# 11 — Notificações

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Princípio de Design

O módulo de notificações segue o mesmo princípio de extensibilidade do módulo de crawlers: **adicionar um novo canal = criar um novo arquivo**. Nenhum código existente é alterado.

Isso é garantido pela interface `NotificationChannel`, que é o único contrato entre o orquestrador e os canais concretos.

```
NotificationService (orquestrador)
        │
        │  para cada canal habilitado do usuário:
        │
        ├──▶ EmailNotificationChannel
        ├──▶ PushNotificationChannel
        ├──▶ (futuro) SmsNotificationChannel
        └──▶ (futuro) WebhookNotificationChannel
```

**Regras invioláveis:**
- `NotificationService` nunca contém lógica de envio — apenas orquestra
- Canais nunca se conhecem entre si
- A decisão de qual canal usar é baseada nas preferências do usuário, não em `if/else` por tipo de evento
- Falha em um canal nunca impede o envio pelos outros

---

## 2. Eventos de Domínio Tratados

O módulo de notificações reage aos seguintes eventos:

| Evento | Origem | Canal(is) | Prioridade |
|--------|--------|-----------|-----------|
| `MovimentacaoDetectadaEvent` | `SnapshotComparator` | Email + Push | Alta |
| `CrawlRetryExhaustedEvent` | `CrawlerMessageConsumer` | Email | Alta |
| `ProcessBlockedEvent` | `CrawlerMessageConsumer` | Email | Alta |
| `CourtRequestCreatedEvent` | `ProcessService` | Email (admin) | Média |
| `HealthScoreLowEvent` | `HealthScoreService` | Email (admin) | Média |
| `PlanLimitReachedEvent` | `SubscriptionService` | Email + Push | Baixa |

---

## 3. Interface `NotificationChannel`

```java
public interface NotificationChannel {

    /**
     * Envia uma notificação para o usuário.
     *
     * @param request Dados completos da notificação a enviar
     * @throws NotificationException se o envio falhar de forma irrecuperável
     */
    void send(NotificationRequest request);

    /**
     * Tipo do canal — usado para filtrar preferências do usuário.
     */
    NotificationChannelType getChannelType();

    /**
     * Verifica se este canal está habilitado nas preferências do usuário.
     */
    boolean isEnabledFor(UserNotificationPreferences preferences);
}
```

---

## 4. `NotificationService` — Orquestrador

Consome eventos de domínio (via Spring `@EventListener` ou RabbitMQ) e despacha para os canais ativos do usuário.

```java
@Service
@Slf4j
public class NotificationService {

    private final List<NotificationChannel>        channels;
    private final UserRepository                   userRepository;
    private final ProcessSubscriptionRepository    subscriptionRepository;
    private final NotificationHistoryRepository    historyRepository;
    private final NotificationRequestFactory       requestFactory;

    // Spring injeta todos os beans que implementam NotificationChannel
    public NotificationService(List<NotificationChannel> channels, ...) {
        this.channels = channels;
    }

    /**
     * Processa detecção de movimentação — notifica TODOS os assinantes ativos.
     */
    @RabbitListener(queues = RabbitConfig.QUEUE_NOTIFICATIONS)
    public void handleMovimentacao(NotificationMessage message) {
        List<ProcessSubscription> subscriptions =
            subscriptionRepository.findActiveByProcessId(message.processId());

        for (ProcessSubscription subscription : subscriptions) {
            notifyUser(subscription.getUser(), message, NotificationEventType.MOVEMENT_DETECTED);
        }
    }

    /**
     * Processa falha irrecuperável de crawling.
     */
    @EventListener
    public void handleCrawlError(CrawlRetryExhaustedEvent event) {
        List<ProcessSubscription> subscriptions =
            subscriptionRepository.findActiveByProcessId(event.getProcessId());

        for (ProcessSubscription subscription : subscriptions) {
            notifyUser(subscription.getUser(), event, NotificationEventType.CRAWL_ERROR);
        }
    }

    /**
     * Lógica central: monta o request e despacha para cada canal ativo.
     */
    private void notifyUser(User user, Object eventData, NotificationEventType eventType) {
        UserNotificationPreferences prefs = user.getNotificationPreferences();

        NotificationRequest request = requestFactory.build(user, eventData, eventType);

        for (NotificationChannel channel : channels) {
            if (!channel.isEnabledFor(prefs)) {
                recordHistory(user, request, channel.getChannelType(),
                    NotificationStatus.SKIPPED, null);
                continue;
            }

            try {
                channel.send(request);
                recordHistory(user, request, channel.getChannelType(),
                    NotificationStatus.SENT, null);

            } catch (NotificationException e) {
                log.error("Falha ao enviar notificação via {} para usuário {}: {}",
                    channel.getChannelType(), user.getId(), e.getMessage());
                recordHistory(user, request, channel.getChannelType(),
                    NotificationStatus.FAILED, e.getMessage());
                // Falha em um canal NÃO interrompe os outros
            }
        }
    }

    private void recordHistory(User user, NotificationRequest request,
                               NotificationChannelType channel,
                               NotificationStatus status, String error) {
        NotificationHistory history = new NotificationHistory();
        history.setUser(user);
        history.setProcess(request.getProcess());
        history.setChannel(channel);
        history.setEventType(request.getEventType().name());
        history.setStatus(status);
        history.setErrorMessage(error);
        history.setSentAt(Instant.now());
        historyRepository.save(history);
    }
}
```

---

## 5. `NotificationRequest`

DTO central que carrega todos os dados necessários para qualquer canal.

```java
public record NotificationRequest(
    User                  user,
    Process               process,
    NotificationEventType eventType,
    String                subject,          // assunto do e-mail / título do push
    String                bodyText,         // corpo em texto simples
    String                bodyHtml,         // corpo em HTML (e-mail)
    Map<String, String>   templateVariables, // variáveis para interpolação
    Instant               generatedAt
) {}
```

---

## 6. `NotificationRequestFactory`

Monta o `NotificationRequest` a partir de cada tipo de evento, interpolando os templates.

```java
@Component
public class NotificationRequestFactory {

    private final TemplateRenderer templateRenderer;

    public NotificationRequest build(User user, Object eventData,
                                     NotificationEventType eventType) {
        return switch (eventType) {
            case MOVEMENT_DETECTED  -> buildMovementRequest(user, (NotificationMessage) eventData);
            case CRAWL_ERROR        -> buildCrawlErrorRequest(user, (CrawlRetryExhaustedEvent) eventData);
            case PROCESS_BLOCKED    -> buildBlockedRequest(user, (ProcessBlockedEvent) eventData);
            case PLAN_LIMIT_REACHED -> buildPlanLimitRequest(user, (PlanLimitReachedEvent) eventData);
        };
    }

    private NotificationRequest buildMovementRequest(User user,
                                                     NotificationMessage event) {
        Map<String, String> vars = Map.of(
            "userName",          user.getName(),
            "processNumber",     event.processNumber(),
            "courtName",         resolveCourtName(event.courtCode()),
            "movementDesc",      event.movementDescription(),
            "movementDate",      formatDate(event.movementDate()),
            "appUrl",            buildProcessUrl(event.processId())
        );

        return new NotificationRequest(
            user,
            resolveProcess(event.processId()),
            NotificationEventType.MOVEMENT_DETECTED,
            templateRenderer.renderSubject(Template.MOVEMENT_SUBJECT, vars),
            templateRenderer.renderText(Template.MOVEMENT_BODY_TEXT, vars),
            templateRenderer.renderHtml(Template.MOVEMENT_BODY_HTML, vars),
            vars,
            Instant.now()
        );
    }
}
```

---

## 7. Canal: E-mail (`EmailNotificationChannel`)

### 7.1 Implementação

```java
@Component
@Slf4j
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final EmailConfig    emailConfig;

    @Override
    public void send(NotificationRequest request) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");

            helper.setFrom(emailConfig.getSenderAddress(), emailConfig.getSenderName());
            helper.setTo(request.user().getEmail());
            helper.setSubject(request.subject());
            helper.setText(request.bodyText(), request.bodyHtml());

            mailSender.send(mime);
            log.debug("E-mail enviado para {}: {}", request.user().getEmail(), request.subject());

        } catch (MailException | MessagingException e) {
            throw new NotificationException("Falha ao enviar e-mail: " + e.getMessage(), e);
        }
    }

    @Override
    public NotificationChannelType getChannelType() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public boolean isEnabledFor(UserNotificationPreferences prefs) {
        return prefs.isEmailEnabled();
    }
}
```

### 7.2 Configuração SMTP

```yaml
spring:
  mail:
    host:     ${SMTP_HOST}
    port:     ${SMTP_PORT:587}
    username: ${SMTP_USER}
    password: ${SMTP_PASSWORD}
    properties:
      mail:
        smtp:
          auth:            true
          starttls:
            enable:        true
            required:      true
          connectiontimeout: 5000
          timeout:           5000
          writetimeout:      5000

app:
  email:
    sender-address: "noreply@consultorprocessos.com.br"
    sender-name:    "Consultor de Processos"
```

### 7.3 Rate Limiting de E-mail

Para evitar spam e bloquear o sistema de e-mail por excesso de envios, aplica-se um limite por usuário:

```java
@Component
public class EmailRateLimiter {

    private final RedisTemplate<String, String> redis;
    private static final int    MAX_EMAILS_PER_HOUR = 10;
    private static final String KEY_PREFIX = "email:rate:";

    public boolean isAllowed(UUID userId) {
        String key = KEY_PREFIX + userId.toString();
        Long count = redis.opsForValue().increment(key);

        if (count == 1) {
            redis.expire(key, Duration.ofHours(1));
        }

        return count <= MAX_EMAILS_PER_HOUR;
    }
}
```

> Esse limiter é aplicado dentro do `EmailNotificationChannel` antes do envio. Se o limite for atingido, a notificação é registrada como `SKIPPED` no histórico com motivo `RATE_LIMITED`.

---

## 8. Canal: Push Notification (`PushNotificationChannel`)

### 8.1 Estratégia

Push notifications são enviadas via **Firebase Cloud Messaging (FCM)**, que unifica o envio para Android e iOS. O token FCM de cada dispositivo do usuário é armazenado na tabela `user_device_tokens`.

### 8.2 Tabela de suporte: `user_device_tokens`

```sql
CREATE TABLE user_device_tokens (
    id          UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token       VARCHAR(512) NOT NULL,
    platform    VARCHAR(10)  NOT NULL,   -- 'ANDROID', 'IOS', 'WEB'
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at TIMESTAMPTZ NULL,

    CONSTRAINT udt_token_unique UNIQUE (token)
);

CREATE INDEX idx_udt_user_id ON user_device_tokens(user_id) WHERE active = true;
```

### 8.3 Implementação

```java
@Component
@Slf4j
public class PushNotificationChannel implements NotificationChannel {

    private final FirebaseMessaging         fcm;
    private final UserDeviceTokenRepository tokenRepository;

    @Override
    public void send(NotificationRequest request) {
        List<UserDeviceToken> tokens =
            tokenRepository.findActiveByUserId(request.user().getId());

        if (tokens.isEmpty()) {
            log.debug("Usuário {} não possui dispositivos registrados para push.",
                request.user().getId());
            return;
        }

        for (UserDeviceToken deviceToken : tokens) {
            sendToDevice(request, deviceToken);
        }
    }

    private void sendToDevice(NotificationRequest request, UserDeviceToken deviceToken) {
        try {
            Message fcmMessage = Message.builder()
                .setToken(deviceToken.getToken())
                .setNotification(Notification.builder()
                    .setTitle(request.subject())
                    .setBody(request.bodyText())
                    .build())
                .putData("processId",    request.process().getId().toString())
                .putData("eventType",    request.eventType().name())
                .putData("courtCode",    request.process().getCourt().getCode())
                .setAndroidConfig(AndroidConfig.builder()
                    .setPriority(AndroidConfig.Priority.HIGH)
                    .build())
                .setApnsConfig(ApnsConfig.builder()
                    .setAps(Aps.builder()
                        .setSound("default")
                        .setBadge(1)
                        .build())
                    .build())
                .build();

            fcm.send(fcmMessage);
            tokenRepository.updateLastUsed(deviceToken.getId(), Instant.now());
            log.debug("Push enviado para dispositivo {} do usuário {}.",
                deviceToken.getId(), request.user().getId());

        } catch (FirebaseMessagingException e) {
            if (isTokenInvalid(e)) {
                // Token expirado ou revogado — desativa silenciosamente
                tokenRepository.deactivate(deviceToken.getId());
                log.info("Token FCM inválido desativado: {}", deviceToken.getId());
            } else {
                throw new NotificationException("Falha no envio push: " + e.getMessage(), e);
            }
        }
    }

    private boolean isTokenInvalid(FirebaseMessagingException e) {
        return e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED
            || e.getMessagingErrorCode() == MessagingErrorCode.INVALID_ARGUMENT;
    }

    @Override
    public NotificationChannelType getChannelType() { return NotificationChannelType.PUSH; }

    @Override
    public boolean isEnabledFor(UserNotificationPreferences prefs) {
        return prefs.isPushEnabled();
    }
}
```

### 8.4 Endpoints para gerenciamento de tokens

```
POST /devices/register
Body: { "token": "fcm-token-aqui", "platform": "ANDROID" }
→ Registra ou reativa o token do dispositivo atual

DELETE /devices/{tokenId}
→ Desativa o token (usuário fez logout no dispositivo)
```

---

## 9. Templates de E-mail

### 9.1 Estrutura de arquivos

```
src/main/resources/templates/email/
├── base-layout.html          ← layout HTML base com header e footer
├── movement-detected/
│   ├── subject.txt
│   ├── body.txt
│   └── body.html
├── crawl-error/
│   ├── subject.txt
│   ├── body.txt
│   └── body.html
├── process-blocked/
│   ├── subject.txt
│   ├── body.txt
│   └── body.html
├── plan-limit-reached/
│   ├── subject.txt
│   ├── body.txt
│   └── body.html
├── account-verification/
│   ├── subject.txt
│   └── body.html
├── password-reset/
│   ├── subject.txt
│   └── body.html
└── admin/
    ├── court-request/
    │   ├── subject.txt
    │   └── body.html
    └── health-score-low/
        ├── subject.txt
        └── body.html
```

O motor de templates é **Thymeleaf**, integrado ao Spring Boot. Os templates usam a engine apenas para interpolação de variáveis — sem lógica de negócio dentro dos templates.

### 9.2 Template: Movimentação Detectada

**`movement-detected/subject.txt`:**
```
Nova movimentação: processo {{processNumber}} ({{courtName}})
```

**`movement-detected/body.html`** (simplificado):
```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<body>
  <!-- inclui base-layout -->
  <div class="content">
    <h2>Olá, <span th:text="${userName}">Usuário</span>!</h2>
    <p>
      Detectamos uma nova movimentação no processo
      <strong th:text="${processNumber}">0000000-00.0000.0.00.0000</strong>
      no <strong th:text="${courtName}">Tribunal</strong>.
    </p>

    <div class="movement-card">
      <p class="movement-date">
        📅 <span th:text="${movementDate}">00/00/0000</span>
      </p>
      <p class="movement-desc" th:text="${movementDesc}">
        Descrição da movimentação.
      </p>
    </div>

    <a th:href="${appUrl}" class="btn-primary">
      Ver processo no app
    </a>
  </div>
</body>
</html>
```

**`movement-detected/body.txt`** (fallback texto simples):
```
Olá, {{userName}}!

Nova movimentação no processo {{processNumber}} ({{courtName}}):

Data: {{movementDate}}
{{movementDesc}}

Acesse o app para ver detalhes: {{appUrl}}

---
Consultor de Processos · Você recebe este e-mail porque acompanha este processo.
Para cancelar as notificações, acesse: {{unsubscribeUrl}}
```

### 9.3 Template: Falha de Crawling

**`crawl-error/subject.txt`:**
```
Atenção: não conseguimos consultar seu processo {{processNumber}}
```

**`crawl-error/body.txt`:**
```
Olá, {{userName}}!

Tentamos consultar o processo {{processNumber}} no {{courtName}} várias vezes,
mas o tribunal está temporariamente indisponível.

O processo foi marcado como "erro" e pausamos as consultas automáticas.

Para reativar o monitoramento, acesse: {{appUrl}}

Pedimos desculpas pelo inconveniente.
```

### 9.4 Template: Solicitação de Tribunal (Admin)

**`admin/court-request/subject.txt`:**
```
[Admin] Nova solicitação de tribunal: {{courtName}} ({{requestCount}} pedido(s))
```

**`admin/court-request/body.txt`:**
```
Um usuário solicitou o tribunal "{{courtName}}" ao tentar cadastrar o processo {{processNumber}}.

Total de solicitações para este tribunal: {{requestCount}}

Acesse o painel admin para gerenciar: {{adminUrl}}
```

---

## 10. `TemplateRenderer`

Responsável por carregar e renderizar os templates com as variáveis do evento.

```java
@Component
public class TemplateRenderer {

    private final TemplateEngine templateEngine; // Thymeleaf

    public String renderSubject(Template template, Map<String, String> vars) {
        return render(template.subjectPath(), vars);
    }

    public String renderText(Template template, Map<String, String> vars) {
        return render(template.textPath(), vars);
    }

    public String renderHtml(Template template, Map<String, String> vars) {
        Context ctx = new Context();
        ctx.setVariables(new HashMap<>(vars));
        return templateEngine.process(template.htmlPath(), ctx);
    }

    private String render(String path, Map<String, String> vars) {
        String content = loadTemplate(path);
        for (Map.Entry<String, String> entry : vars.entrySet()) {
            content = content.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return content;
    }
}
```

```java
public enum Template {

    MOVEMENT_SUBJECT    ("email/movement-detected/subject.txt",
                         "email/movement-detected/body.txt",
                         "email/movement-detected/body"),

    CRAWL_ERROR_SUBJECT ("email/crawl-error/subject.txt",
                         "email/crawl-error/body.txt",
                         "email/crawl-error/body"),

    PLAN_LIMIT_SUBJECT  ("email/plan-limit-reached/subject.txt",
                         "email/plan-limit-reached/body.txt",
                         "email/plan-limit-reached/body");

    private final String subjectPath;
    private final String textPath;
    private final String htmlPath;
    // construtor e getters omitidos por brevidade
}
```

---

## 11. Notificações Administrativas

Alguns eventos geram notificações diretas ao administrador, sem passar pelo `NotificationService` de usuário. Esses eventos são tratados por `AdminNotificationService`.

```java
@Service
@Slf4j
public class AdminNotificationService {

    private final JavaMailSender mailSender;
    private final AdminConfig    adminConfig;
    private final TemplateRenderer renderer;

    @EventListener
    public void handleCourtRequest(CourtRequestCreatedEvent event) {
        sendAdminEmail(
            Template.ADMIN_COURT_REQUEST,
            Map.of(
                "courtName",    event.getCourtName(),
                "processNumber", event.getProcessNumber(),
                "requestCount", String.valueOf(event.getTotalRequests()),
                "adminUrl",     adminConfig.getPanelUrl() + "/court-requests"
            )
        );
    }

    @EventListener
    public void handleHealthScoreLow(HealthScoreLowEvent event) {
        sendAdminEmail(
            Template.ADMIN_HEALTH_SCORE_LOW,
            Map.of(
                "courtName",  event.getCourtName(),
                "courtCode",  event.getCourtCode(),
                "score",      String.valueOf(event.getScore()),
                "threshold",  String.valueOf(event.getThreshold()),
                "adminUrl",   adminConfig.getPanelUrl() + "/courts/" + event.getCourtCode()
            )
        );
    }

    private void sendAdminEmail(Template template, Map<String, String> vars) {
        try {
            MimeMessage mime = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, "UTF-8");
            helper.setFrom(adminConfig.getSenderAddress());
            helper.setTo(adminConfig.getAdminEmail());
            helper.setSubject(renderer.renderSubject(template, vars));
            helper.setText(renderer.renderText(template, vars),
                           renderer.renderHtml(template, vars));
            mailSender.send(mime);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail administrativo: {}", e.getMessage(), e);
            // Não lança exceção — e-mail admin não pode derrubar o fluxo principal
        }
    }
}
```

---

## 12. Preferências de Notificação do Usuário

### 12.1 Estrutura

```java
@Embeddable
public class UserNotificationPreferences {

    @Column(name = "notif_email_enabled")
    private boolean emailEnabled = true;   // padrão: ativo

    @Column(name = "notif_push_enabled")
    private boolean pushEnabled = false;   // padrão: inativo até registrar dispositivo
}
```

Essas colunas ficam na própria tabela `users` (sem tabela separada, pois são simples flags).

```sql
-- Migração: adiciona colunas de preferência na tabela users
ALTER TABLE users
    ADD COLUMN notif_email_enabled BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN notif_push_enabled  BOOLEAN NOT NULL DEFAULT false;
```

### 12.2 Atualização via API

O usuário atualiza preferências via `PATCH /users/me`:

```json
{
  "notifications": {
    "emailEnabled": true,
    "pushEnabled":  true
  }
}
```

---

## 13. Link de Cancelamento (Unsubscribe)

Todo e-mail de notificação de movimentação inclui um link de cancelamento de notificações por e-mail, conforme exigência do RFC 8058 (List-Unsubscribe) e boas práticas anti-spam.

O link usa um token de uso único gerado na hora do envio:

```
https://app.consultorprocessos.com.br/unsubscribe?token={token}
```

```java
@RestController
@RequestMapping("/unsubscribe")
public class UnsubscribeController {

    private final UnsubscribeTokenService tokenService;
    private final UserService             userService;

    @GetMapping
    public ResponseEntity<Void> unsubscribe(@RequestParam String token) {
        UUID userId = tokenService.validate(token);
        userService.disableEmailNotifications(userId);
        // Redireciona para página de confirmação no frontend
        return ResponseEntity.status(302)
            .location(URI.create("https://app.consultorprocessos.com.br/unsubscribed"))
            .build();
    }
}
```

> O token de unsubscribe é diferente do token de autenticação. É gerado especificamente para esse fim, com validade de 30 dias, e não requer autenticação prévia para ser usado.

---

## 14. Extensibilidade: Adicionando Novos Canais

### 14.1 SMS (futuro)

```java
@Component
// @Profile("sms-enabled") — ativar quando integração estiver pronta
public class SmsNotificationChannel implements NotificationChannel {

    private final SmsGateway smsGateway; // ex: Twilio, Zenvia

    @Override
    public void send(NotificationRequest request) {
        // Mensagem SMS limitada a 160 chars — usa bodyText truncado
        String smsText = truncate(request.bodyText(), 160);
        smsGateway.send(request.user().getPhoneNumber(), smsText);
    }

    @Override
    public NotificationChannelType getChannelType() { return NotificationChannelType.SMS; }

    @Override
    public boolean isEnabledFor(UserNotificationPreferences prefs) {
        return prefs.isSmsEnabled();
    }
}
```

**O que precisa ser adicionado para suportar SMS:**
- [ ] Novo valor `SMS` no enum `NotificationChannelType`
- [ ] Nova coluna `notif_sms_enabled` e `phone_number` em `users`
- [ ] Novo bean `SmsNotificationChannel` (acima)
- [ ] Templates de texto curto (já existem os `.txt`)
- [ ] Integração com gateway (Twilio / Zenvia)
- [ ] Zero modificações no `NotificationService` ou outros canais

### 14.2 Webhook (futuro)

```java
@Component
public class WebhookNotificationChannel implements NotificationChannel {

    private final WebhookConfigRepository webhookRepository;
    private final HttpClient              httpClient;

    @Override
    public void send(NotificationRequest request) {
        // Busca URL de webhook configurada pelo usuário
        Optional<WebhookConfig> webhook =
            webhookRepository.findActiveByUserId(request.user().getId());

        if (webhook.isEmpty()) return;

        WebhookPayload payload = new WebhookPayload(
            request.eventType().name(),
            request.process().getProcessNumber(),
            request.process().getCourt().getCode(),
            request.generatedAt()
        );

        HttpRequest httpReq = HttpRequest.newBuilder()
            .uri(URI.create(webhook.get().getUrl()))
            .header("Content-Type", "application/json")
            .header("X-Consultor-Signature", generateSignature(payload, webhook.get().getSecret()))
            .POST(HttpRequest.BodyPublishers.ofString(toJson(payload)))
            .timeout(Duration.ofSeconds(10))
            .build();

        httpClient.send(httpReq, HttpResponse.BodyHandlers.discarding());
    }

    @Override
    public NotificationChannelType getChannelType() { return NotificationChannelType.WEBHOOK; }

    @Override
    public boolean isEnabledFor(UserNotificationPreferences prefs) {
        return prefs.isWebhookEnabled();
    }
}
```

---

## 15. Histórico e Rastreabilidade

### 15.1 O que é registrado

Toda tentativa de envio — bem-sucedida, falha ou ignorada — é registrada em `notification_history` (schema documentado em `06-BancoDeDados.md`, seção 3.15).

### 15.2 Retenção e limpeza

```
SENT   → manter por 1 ano
FAILED → manter por 1 ano (útil para diagnóstico)
SKIPPED → manter por 30 dias (volume alto; menos útil)
```

Job de limpeza executado semanalmente:

```java
@Scheduled(cron = "0 0 3 * * SUN") // todo domingo às 3h
public void cleanupNotificationHistory() {
    historyRepository.deleteOlderThan(
        NotificationStatus.SENT,    Instant.now().minus(Duration.ofDays(365))
    );
    historyRepository.deleteOlderThan(
        NotificationStatus.SKIPPED, Instant.now().minus(Duration.ofDays(30))
    );
}
```

---

## 16. Métricas

| Métrica | Tags | Tipo | Descrição |
|---------|------|------|-----------|
| `notifications.sent.total` | `channel`, `eventType` | Counter | Notificações enviadas com sucesso |
| `notifications.failed.total` | `channel`, `eventType` | Counter | Falhas de envio |
| `notifications.skipped.total` | `channel`, `reason` | Counter | Ignoradas (preferência desabilitada ou rate limit) |
| `notifications.duration.ms` | `channel` | Histogram | Tempo de envio por canal |
| `push.tokens.active` | — | Gauge | Total de tokens FCM ativos |
| `push.tokens.invalidated` | — | Counter | Tokens inválidos desativados |
| `email.rate_limited.total` | — | Counter | E-mails bloqueados por rate limit |

---

## 17. Configuração por Ambiente

```yaml
# application.yml (base)
app:
  notifications:
    email:
      enabled:              true
      rate-limit-per-hour:  10
    push:
      enabled:              true
    admin-email:            ${ADMIN_EMAIL}
    unsubscribe-base-url:   ${APP_BASE_URL}/unsubscribe

  firebase:
    credentials-file: ${FIREBASE_CREDENTIALS_PATH}
    project-id:       ${FIREBASE_PROJECT_ID}

# application-dev.yml
app:
  notifications:
    email:
      enabled: false    # em DEV, e-mails são apenas logados, não enviados
    push:
      enabled: false    # em DEV, push é apenas logado
```

### Canal mock para DEV

```java
@Component
@Profile("dev")
@Primary
public class LogOnlyNotificationChannel implements NotificationChannel {

    @Override
    public void send(NotificationRequest request) {
        log.info("[DEV] Notificação simulada | canal={} | evento={} | usuário={} | assunto={}",
            getChannelType(), request.eventType(),
            request.user().getEmail(), request.subject());
        // Não envia nada de verdade
    }
    // Implementa todos os canais em um único bean de DEV
}
```

> Em DEV, um único bean `LogOnlyNotificationChannel` substitui todos os canais reais via `@Primary`. O `NotificationService` chama normalmente — apenas o destino final é diferente.
