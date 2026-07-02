# 10 — Scheduler e Filas

**Consultor de Processos**
Software Design Document · v1.0

---

## 1. Responsabilidades e Separação de Concerns

O sistema de agendamento é dividido em dois componentes com responsabilidades completamente distintas:

| Componente | Responsabilidade | O que NÃO faz |
|------------|-----------------|---------------|
| `SchedulerService` | Decide **quais** processos consultar e **quando** | Não executa consultas |
| `CrawlerMessageConsumer` | Executa as consultas quando a fila entrega | Não decide timing |

Essa separação garante que o scheduler nunca seja bloqueado por uma consulta lenta. Se um tribunal demorar 30 segundos, o scheduler continua enfileirando os outros processos normalmente.

```
SchedulerService (cron)
    │
    │  "Quais processos precisam ser consultados agora?"
    │
    ▼
MonitoringService
    │
    ▼
Lista de ProcessIds pendentes
    │
    ▼
QueuePublisher → RabbitMQ [crawl.requests]
                      │
                      │  (assíncrono, desacoplado)
                      │
                      ▼
              CrawlerMessageConsumer
                      │
                      ▼
              CourtProvider.consultar()
```

---

## 2. `SchedulerService`

### 2.1 Configuração do Cron

O scheduler executa em ciclo fixo, configurável por variável de ambiente:

```yaml
# application.yml
app:
  scheduler:
    enabled: true
    cron: "0 */10 * * * *"   # a cada 10 minutos
    lock-ttl-seconds: 540      # TTL do lock Redis (90% do intervalo)
```

```java
@Component
@Slf4j
public class SchedulerService {

    private final MonitoringService monitoringService;
    private final QueuePublisher queuePublisher;
    private final RedisLockService redisLockService;

    private static final String SCHEDULER_LOCK_KEY = "scheduler:global:lock";

    @Scheduled(cron = "${app.scheduler.cron}")
    @ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true")
    public void execute() {
        // Lock distribuído: garante que apenas uma instância do scheduler
        // execute por vez, mesmo em ambiente com múltiplas réplicas
        if (!redisLockService.tryAcquire(SCHEDULER_LOCK_KEY,
                Duration.ofSeconds(lockTtlSeconds))) {
            log.debug("Scheduler já está rodando em outra instância. Ignorando.");
            return;
        }

        try {
            log.info("Scheduler iniciado.");
            long startTime = System.currentTimeMillis();

            List<PendingProcess> pending = monitoringService.findPendingProcesses();

            int enqueued = 0;
            int skipped  = 0;

            for (PendingProcess process : pending) {
                boolean published = queuePublisher.tryPublish(process);
                if (published) enqueued++;
                else           skipped++;
            }

            log.info("Scheduler concluído em {}ms. Encontrados: {}, Enfileirados: {}, Ignorados (lock): {}",
                System.currentTimeMillis() - startTime,
                pending.size(), enqueued, skipped);

        } finally {
            redisLockService.release(SCHEDULER_LOCK_KEY);
        }
    }
}
```

### 2.2 Lock Distribuído (Redis)

O lock global previne que múltiplas instâncias do backend (em caso de escala horizontal) executem o scheduler simultaneamente, o que causaria enfileiramento duplicado.

```java
@Component
public class RedisLockService {

    private final StringRedisTemplate redis;

    /**
     * Tenta adquirir um lock. Retorna true se adquirido, false se já existia.
     * Usa SET NX EX — operação atômica no Redis.
     */
    public boolean tryAcquire(String key, Duration ttl) {
        Boolean acquired = redis.opsForValue()
            .setIfAbsent(key, "locked", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    public void release(String key) {
        redis.delete(key);
    }
}
```

---

## 3. `MonitoringService`

Responsável por determinar quais processos precisam ser consultados na execução atual.

### 3.1 Lógica de Decisão

Um processo é elegível para consulta quando **todas** as condições são verdadeiras:

1. `process.status IN ('PENDING', 'OK')` — processos em ERROR ou BLOCKED não entram no ciclo normal
2. Existe ao menos uma `ProcessSubscription` com `active = true` para esse processo
3. O usuário da subscription está com `status = 'ACTIVE'`
4. `process.lastCheckedAt IS NULL` OU `process.lastCheckedAt < NOW() - intervaloEfetivo`

O **intervalo efetivo** de um processo é o **menor** `checkIntervalHours` entre todos os planos dos assinantes ativos. Ou seja: se um processo tem dois assinantes — um no plano de 12h e outro no plano de 4h — o processo é consultado a cada 4h.

```java
@Service
public class MonitoringService {

    private final ProcessRepository processRepository;

    public List<PendingProcess> findPendingProcesses() {
        return processRepository.findAllPendingWithEffectiveInterval();
        // Query documentada em 06-BancoDeDados.md — seção 7.1
    }
}
```

### 3.2 O que o `MonitoringService` NÃO decide

- Não decide a ordem de execução (o RabbitMQ e os workers fazem isso)
- Não verifica se o tribunal está disponível (o Provider cuida disso)
- Não sabe quantos workers estão consumindo a fila

---

## 4. `QueuePublisher`

Publica mensagens no RabbitMQ. Antes de publicar, verifica se já existe um lock de crawl para o processo (evitar duplicatas mesmo dentro da mesma execução do scheduler).

```java
@Component
@Slf4j
public class QueuePublisher {

    private final RabbitTemplate rabbitTemplate;
    private final RedisLockService redisLockService;

    private static final String CRAWL_LOCK_PREFIX  = "crawl:lock:";
    private static final Duration CRAWL_LOCK_TTL   = Duration.ofMinutes(5);

    /**
     * Tenta publicar uma mensagem de crawl.
     * Retorna false se o processo já estiver bloqueado (sendo processado por outro worker).
     */
    public boolean tryPublish(PendingProcess process) {
        String lockKey = CRAWL_LOCK_PREFIX + process.courtCode()
                         + ":" + process.processNumber();

        if (!redisLockService.tryAcquire(lockKey, CRAWL_LOCK_TTL)) {
            log.debug("Processo {} já está em processamento. Ignorando.",
                process.processNumber());
            return false;
        }

        CrawlRequestMessage message = new CrawlRequestMessage(
            process.processId(),
            process.courtCode(),
            process.processNumber()
        );

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_CRAWL,
            RabbitConfig.ROUTING_KEY_CRAWL_REQUEST,
            message
        );

        log.debug("Processo {} enfileirado para consulta no tribunal {}.",
            process.processNumber(), process.courtCode());

        return true;
    }
}
```

> O lock Redis com TTL de 5 minutos garante que, se um worker travar sem liberar o lock, o processo será automaticamente elegível para consulta na próxima rodada do scheduler.

---

## 5. Configuração do RabbitMQ

### 5.1 Topologia de Filas

```
Exchange: crawl.exchange (direct)
    │
    ├── routing key: crawl.request  →  fila: crawl.requests
    │                                        (consumer: CrawlerMessageConsumer)
    │
    └── routing key: crawl.retry   →  fila: crawl.retry
                                             (consumer: RetryMessageConsumer)

Exchange: crawl.dlx (direct) — Dead Letter Exchange
    │
    └── routing key: crawl.dead  →  fila: crawl.dlq
                                          (processamento manual pelo admin)

Exchange: notification.exchange (direct)
    │
    └── routing key: notification →  fila: notifications
                                           (consumer: NotificationMessageConsumer)
```

### 5.2 `RabbitConfig`

```java
@Configuration
public class RabbitConfig {

    // Exchanges
    public static final String EXCHANGE_CRAWL        = "crawl.exchange";
    public static final String EXCHANGE_CRAWL_DLX    = "crawl.dlx";
    public static final String EXCHANGE_NOTIFICATION = "notification.exchange";

    // Routing Keys
    public static final String ROUTING_KEY_CRAWL_REQUEST = "crawl.request";
    public static final String ROUTING_KEY_CRAWL_RETRY   = "crawl.retry";
    public static final String ROUTING_KEY_NOTIFICATION  = "notification";

    // Filas
    public static final String QUEUE_CRAWL_REQUESTS = "crawl.requests";
    public static final String QUEUE_CRAWL_RETRY    = "crawl.retry";
    public static final String QUEUE_CRAWL_DLQ      = "crawl.dlq";
    public static final String QUEUE_NOTIFICATIONS  = "notifications";

    @Bean
    public DirectExchange crawlExchange() {
        return new DirectExchange(EXCHANGE_CRAWL, true, false);
    }

    @Bean
    public DirectExchange crawlDlx() {
        return new DirectExchange(EXCHANGE_CRAWL_DLX, true, false);
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE_NOTIFICATION, true, false);
    }

    @Bean
    public Queue crawlRequestsQueue() {
        return QueueBuilder.durable(QUEUE_CRAWL_REQUESTS)
            .withArgument("x-dead-letter-exchange", EXCHANGE_CRAWL_DLX)
            .withArgument("x-dead-letter-routing-key", "crawl.dead")
            .build();
    }

    @Bean
    public Queue crawlRetryQueue() {
        // Fila de retry com TTL: mensagens voltam para crawl.requests após o delay
        return QueueBuilder.durable(QUEUE_CRAWL_RETRY)
            .withArgument("x-dead-letter-exchange", EXCHANGE_CRAWL)
            .withArgument("x-dead-letter-routing-key", ROUTING_KEY_CRAWL_REQUEST)
            // TTL configurado por mensagem (não fixo na fila)
            .build();
    }

    @Bean
    public Queue crawlDlqQueue() {
        return QueueBuilder.durable(QUEUE_CRAWL_DLQ).build();
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS).build();
    }

    // Bindings
    @Bean
    public Binding bindCrawlRequests() {
        return BindingBuilder.bind(crawlRequestsQueue())
            .to(crawlExchange())
            .with(ROUTING_KEY_CRAWL_REQUEST);
    }

    @Bean
    public Binding bindCrawlRetry() {
        return BindingBuilder.bind(crawlRetryQueue())
            .to(crawlExchange())
            .with(ROUTING_KEY_CRAWL_RETRY);
    }

    @Bean
    public Binding bindCrawlDlq() {
        return BindingBuilder.bind(crawlDlqQueue())
            .to(crawlDlx())
            .with("crawl.dead");
    }

    @Bean
    public Binding bindNotifications() {
        return BindingBuilder.bind(notificationsQueue())
            .to(notificationExchange())
            .with(ROUTING_KEY_NOTIFICATION);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

---

## 6. Mensagens

### `CrawlRequestMessage`

Mensagem publicada pelo scheduler para cada processo pendente.

```java
public record CrawlRequestMessage(
    UUID   processId,
    String courtCode,
    String processNumber,
    int    retryCount,        // 0 na primeira tentativa
    Long   scheduledDelayMs   // null na primeira tentativa; preenchido no retry
) {
    // Construtor para primeira publicação
    public CrawlRequestMessage(UUID processId, String courtCode, String processNumber) {
        this(processId, courtCode, processNumber, 0, null);
    }
}
```

### `NotificationMessage`

Mensagem publicada quando uma movimentação é detectada.

```java
public record NotificationMessage(
    UUID   processId,
    String processNumber,
    String courtCode,
    String eventType,          // MOVEMENT_DETECTED, CRAWL_ERROR, etc.
    String movementDescription,
    LocalDate movementDate,
    Instant detectedAt
) {}
```

---

## 7. `CrawlerMessageConsumer`

Consome mensagens da fila `crawl.requests` e executa a consulta ao tribunal.

```java
@Component
@Slf4j
public class CrawlerMessageConsumer {

    private final CourtProviderFactory providerFactory;
    private final SnapshotComparator   snapshotComparator;
    private final ProcessRepository    processRepository;
    private final RetryPublisher       retryPublisher;
    private final RedisLockService     redisLockService;
    private final CrawlerExecutionRecorder recorder;

    private static final int MAX_RETRIES = 3;

    @RabbitListener(queues = RabbitConfig.QUEUE_CRAWL_REQUESTS)
    public void consume(CrawlRequestMessage message,
                        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                        Channel channel) throws IOException {

        log.info("Processando consulta: processo={}, tribunal={}",
            message.processNumber(), message.courtCode());

        long startTime = System.currentTimeMillis();
        String lockKey = "crawl:lock:" + message.courtCode()
                         + ":" + message.processNumber();

        try {
            CourtProvider provider = providerFactory.getProvider(message.courtCode());
            ProcessSnapshot snapshot = provider.consultar(message.processNumber());

            snapshotComparator.compareAndPersist(message.processId(), snapshot);

            channel.basicAck(deliveryTag, false);
            log.info("Consulta concluída com sucesso: processo={} em {}ms",
                message.processNumber(), System.currentTimeMillis() - startTime);

        } catch (CourtUnavailableException | CrawlException e) {
            handleCrawlFailure(message, e, deliveryTag, channel, startTime);

        } catch (ProcessNotFoundException e) {
            // Processo não existe no tribunal — marca como erro permanente
            processRepository.updateStatus(message.processId(), ProcessStatus.ERROR);
            channel.basicAck(deliveryTag, false); // ack: não tentar novamente
            log.warn("Processo {} não encontrado no tribunal {}.",
                message.processNumber(), message.courtCode());

        } catch (Exception e) {
            // Erro inesperado — nack sem requeue; vai para DLQ
            log.error("Erro inesperado ao processar {}: {}",
                message.processNumber(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);

        } finally {
            redisLockService.release(lockKey);
        }
    }

    private void handleCrawlFailure(CrawlRequestMessage message, Exception e,
                                    long deliveryTag, Channel channel,
                                    long startTime) throws IOException {

        int nextRetry = message.retryCount() + 1;
        log.warn("Falha na consulta do processo {} (tentativa {}/{}): {}",
            message.processNumber(), nextRetry, MAX_RETRIES, e.getMessage());

        processRepository.incrementConsecutiveErrors(message.processId());

        if (nextRetry <= MAX_RETRIES) {
            long delayMs = calculateBackoff(nextRetry);
            retryPublisher.publishWithDelay(message.withRetry(nextRetry), delayMs);
            channel.basicAck(deliveryTag, false); // ack: retry gerenciado por nós
        } else {
            // Esgotou retries → DLQ
            processRepository.updateStatus(message.processId(), ProcessStatus.ERROR);
            channel.basicNack(deliveryTag, false, false); // vai para DLQ via DLX
            log.error("Processo {} entrou em estado ERROR após {} tentativas.",
                message.processNumber(), MAX_RETRIES);
            // Publica evento para notificar o usuário
        }
    }

    /**
     * Backoff exponencial com jitter de ±20%.
     * Tentativa 1 →  ~1 min  (60_000ms  ± 12s)
     * Tentativa 2 →  ~5 min  (300_000ms ± 60s)
     * Tentativa 3 →  ~30 min (1_800_000ms ± 6min)
     */
    private long calculateBackoff(int attempt) {
        long base = switch (attempt) {
            case 1 -> 60_000L;
            case 2 -> 300_000L;
            default -> 1_800_000L;
        };
        double jitterFactor = 0.8 + (Math.random() * 0.4); // 0.8 a 1.2
        return (long) (base * jitterFactor);
    }
}
```

---

## 8. Retry com Delay (Backoff via TTL do RabbitMQ)

O retry com delay é implementado via **per-message TTL** na fila `crawl.retry`. Quando o TTL expira, a mensagem é movida de volta para `crawl.requests` via Dead Letter Exchange da fila de retry.

```java
@Component
public class RetryPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishWithDelay(CrawlRequestMessage message, long delayMs) {
        MessagePostProcessor props = m -> {
            m.getMessageProperties().setExpiration(String.valueOf(delayMs));
            return m;
        };

        rabbitTemplate.convertAndSend(
            RabbitConfig.EXCHANGE_CRAWL,
            RabbitConfig.ROUTING_KEY_CRAWL_RETRY,
            message,
            props
        );

        log.debug("Processo {} reagendado para retry em {}ms.",
            message.processNumber(), delayMs);
    }
}
```

**Fluxo de retry visualizado:**

```
crawl.requests → [falha] → RetryPublisher
                                │
                                ▼
                          crawl.retry
                          (TTL = delayMs)
                                │
                           [TTL expira]
                                │
                                ▼ (via DLX da fila retry)
                          crawl.requests
                          (retryCount + 1)
```

---

## 9. Dead Letter Queue (DLQ)

Mensagens que chegam à `crawl.dlq` são retidas indefinidamente até intervenção manual do administrador.

### 9.1 O que vai para a DLQ

| Causa | Como chega |
|-------|-----------|
| Esgotou os 3 retries | `basicNack` com `requeue=false` → DLX |
| Erro inesperado (Exception) | `basicNack` com `requeue=false` → DLX |
| Mensagem malformada (deserialização) | Spring AMQP → DLX automaticamente |

### 9.2 Reprocessamento via Admin

O painel admin lista as mensagens da DLQ e permite reenfileirá-las:

```java
@Service
public class DlqService {

    private final RabbitAdmin rabbitAdmin;
    private final QueuePublisher queuePublisher;

    /**
     * Consome uma mensagem da DLQ e a reenfileira em crawl.requests
     * com retryCount zerado (tratada como primeira tentativa).
     */
    public void reprocess(String messageId) {
        Message raw = rabbitAdmin.getRabbitTemplate()
            .receive(RabbitConfig.QUEUE_CRAWL_DLQ, 1000);

        if (raw == null) return;

        CrawlRequestMessage original = deserialize(raw);
        CrawlRequestMessage reset    = original.withResetRetry();
        queuePublisher.tryPublish(reset);
    }
}
```

---

## 10. `SnapshotComparator`

Recebe o `ProcessSnapshot` retornado pelo Provider e decide se houve mudança.

```java
@Service
@Slf4j
public class SnapshotComparator {

    private final ProcessRepository      processRepository;
    private final ProcessSnapshotRepository snapshotRepository;
    private final ProcessHistoryRepository  historyRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CrawlerExecutionRecorder  recorder;

    @Transactional
    public void compareAndPersist(UUID processId, ProcessSnapshot snapshot) {
        Process process = processRepository.findById(processId)
            .orElseThrow(() -> new ProcessNotFoundException(processId));

        String previousHash = process.getLastSnapshotHash();
        String newHash      = snapshot.contentHash();

        recorder.record(buildExecutionContext(process, snapshot, true));

        if (newHash.equals(previousHash)) {
            // Sem mudança — atualiza apenas o timestamp
            processRepository.updateLastChecked(processId, Instant.now());
            processRepository.resetConsecutiveErrors(processId);
            log.debug("Processo {} sem mudança (hash idêntico).", process.getProcessNumber());
            return;
        }

        // Mudança detectada
        log.info("Movimentação detectada no processo {}. Hash anterior: {}, novo: {}",
            process.getProcessNumber(), previousHash, newHash);

        ProcessSnapshot savedSnapshot = snapshotRepository.save(
            toEntity(snapshot, process)
        );

        List<ProcessHistory> history = snapshot.movements().stream()
            .map(m -> toHistoryEntity(m, process, savedSnapshot))
            .collect(Collectors.toList());
        historyRepository.saveAll(history);

        processRepository.updateAfterMovement(processId, newHash, Instant.now());
        processRepository.resetConsecutiveErrors(processId);

        // Publica evento para o módulo de notificações
        eventPublisher.publishEvent(new MovimentacaoDetectadaEvent(
            this, processId, snapshot
        ));
    }
}
```

---

## 11. Concorrência e Garantias

### 11.1 Garantia de não-duplicação de consultas

| Mecanismo | Onde | O que previne |
|-----------|------|---------------|
| Lock Redis `crawl:lock:{court}:{number}` | `QueuePublisher` | Mesmo processo publicado duas vezes no mesmo ciclo do scheduler |
| TTL de 5 min no lock | Redis | Lock orphan se o worker travar |
| `basicAck` após processamento | `CrawlerMessageConsumer` | Reentrega automática pelo RabbitMQ se worker morrer antes do ack |
| `UNIQUE (process_number, court_id)` | Banco | Duplo cadastro no nível de dados |

### 11.2 Ordem de processamento

Filas RabbitMQ são FIFO por padrão. O scheduler publica os processos ordenados por `lastCheckedAt ASC NULLS FIRST`, garantindo que processos mais antigos sejam priorizados.

### 11.3 Paralelismo de workers

O número de workers que consomem `crawl.requests` é configurável:

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 3      # workers mínimos
        max-concurrency: 10 # workers máximos (auto-scaling)
        prefetch: 1         # cada worker pega 1 mensagem por vez
```

`prefetch: 1` é crítico: garante que um worker lento não trave mensagens que outros workers poderiam processar.

---

## 12. Monitoramento do Scheduler

### 12.1 Métricas Micrometer

| Métrica | Tags | Tipo | Descrição |
|---------|------|------|-----------|
| `scheduler.executions.total` | — | Counter | Total de execuções do cron |
| `scheduler.processes.found` | — | Gauge | Processos encontrados na última execução |
| `scheduler.processes.enqueued` | — | Counter | Processos enfileirados |
| `scheduler.processes.skipped` | — | Counter | Ignorados por lock já existente |
| `scheduler.duration.ms` | — | Histogram | Tempo total de cada execução |
| `queue.crawl.requests.size` | — | Gauge | Tamanho atual da fila |
| `queue.crawl.dlq.size` | — | Gauge | Mensagens aguardando na DLQ |

### 12.2 Alertas

| Condição | Ação |
|----------|------|
| `queue.crawl.dlq.size > 10` | Alerta no painel admin |
| `scheduler.duration.ms > 30000` | Warning em log (scheduler está lento) |
| Scheduler não executou em 2× o intervalo configurado | Alerta crítico (possível travamento) |
| `crawler.errors.total` subir > 20% em 1 hora | Alerta de degradação de tribunal |

---

## 13. Configuração Completa por Ambiente

```yaml
# application-dev.yml
app:
  scheduler:
    enabled: true
    cron: "0 */1 * * * *"   # a cada 1 minuto em DEV (ciclos rápidos para testes)
    lock-ttl-seconds: 50

# application-prod.yml
app:
  scheduler:
    enabled: true
    cron: "0 */10 * * * *"  # a cada 10 minutos em produção
    lock-ttl-seconds: 540

spring:
  rabbitmq:
    host:     ${RABBITMQ_HOST}
    port:     ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: /
    listener:
      simple:
        concurrency:     ${CRAWLER_WORKERS_MIN:3}
        max-concurrency: ${CRAWLER_WORKERS_MAX:10}
        prefetch:        1
        retry:
          enabled: false  # retry gerenciado manualmente por nós, não pelo Spring
```

---

## 14. Fluxo Completo em Diagrama Resumido

```
┌─────────────────────────────────────────────────────────────────┐
│  A cada 10 min                                                  │
│                                                                 │
│  SchedulerService                                               │
│       │                                                         │
│       │ findPendingProcesses()                                  │
│       ▼                                                         │
│  MonitoringService ──▶ SQL (lastCheckedAt < now - interval)     │
│       │                                                         │
│       │ [para cada processo]                                    │
│       ▼                                                         │
│  QueuePublisher                                                 │
│       │── Redis: SET NX crawl:lock:{court}:{number} (TTL 5min) │
│       │   └─ falhou? → skip                                     │
│       │── RabbitMQ: publish(CrawlRequestMessage)               │
│                                                                 │
└──────────────────────────────┬──────────────────────────────────┘
                               │
                    ┌──────────▼──────────┐
                    │   crawl.requests    │
                    │   (FIFO, durable)   │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
        Worker 1          Worker 2         Worker 3
              │
              ▼
    CrawlerMessageConsumer
              │
              ├─ [sucesso] ──▶ SnapshotComparator
              │                      │
              │                      ├─ [sem mudança] → update lastCheckedAt
              │                      │
              │                      └─ [mudança] ──▶ salva Snapshot + History
              │                                       └──▶ publica NotificationMessage
              │
              ├─ [falha, retry < 3] ──▶ crawl.retry (TTL=backoff) ──▶ crawl.requests
              │
              └─ [falha, retry == 3] ──▶ crawl.dlq (aguarda admin)
```
